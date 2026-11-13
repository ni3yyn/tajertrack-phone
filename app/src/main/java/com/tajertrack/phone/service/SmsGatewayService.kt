package com.tajertrack.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.tajertrack.phone.MainActivity
import com.tajertrack.phone.R
import com.tajertrack.phone.data.ApiClient
import com.tajertrack.phone.data.AppPreferences
import com.tajertrack.phone.data.DeviceTokenStore
import com.tajertrack.phone.data.GatewayHttpException
import com.tajertrack.phone.data.PendingSmsReport
import com.tajertrack.phone.data.SmsJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class SmsGatewayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var relayJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val stopping = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val preferences = AppPreferences(this)
            publishStatus(preferences, "stopped", "تم إيقاف إرسال الرسائل من هذا الهاتف.")
            stopRelay()
            return START_NOT_STICKY
        }

        stopping.set(false)
        val notification = buildNotification()
        acquireCpuWakeLock()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (relayJob?.isActive != true) {
            relayJob = serviceScope.launch { runRelay() }
        }
        return START_STICKY
    }

    private suspend fun runRelay() {
        val tokenStore = DeviceTokenStore(this)
        val preferences = AppPreferences(this)
        val token = tokenStore.readToken()
        if (token.isNullOrBlank()) {
            publishStatus(preferences, "needs_pairing", "اربط الهاتف بمتجرك أولاً لتشغيل خدمة الرسائل.")
            stopRelay()
            return
        }

        var idleDelayMs = IDLE_DELAY_MIN_MS
        var consecutiveFailures = preferences.consecutiveRelayFailures
        var inMemoryPendingReport: PendingSmsReport? = null
        val api = ApiClient()
        publishStatus(preferences, "connecting", "جارٍ الاتصال بخادم تاجر تراك...")

        while (!stopping.get()) {
            try {
                // Report the previous outcome before claiming anything else. If the
                // HTTP response is lost, retry the report instead of sending again.
                val pendingReport = inMemoryPendingReport ?: preferences.pendingSmsReport()
                if (pendingReport != null) {
                    val currentDeviceId = tokenStore.deviceId()
                    if (pendingReport.deviceId.isNotBlank() && pendingReport.deviceId != currentDeviceId) {
                        preferences.clearPendingSmsReport(pendingReport.jobId)
                        inMemoryPendingReport = null
                        publishStatus(
                            preferences,
                            "attention",
                            "توجد نتيجة رسالة من ربط سابق لم تعد صالحة لهذا الهاتف. لن يعاد إرسال الرسالة تلقائياً."
                        )
                    } else {
                        try {
                            publishStatus(preferences, "reporting", "جارٍ تأكيد نتيجة الرسالة السابقة مع تاجر تراك...")
                            api.report(token, pendingReport.jobId, pendingReport.status, pendingReport.error)
                            preferences.clearPendingSmsReport(pendingReport.jobId)
                            inMemoryPendingReport = null
                            markServerSuccess(preferences)
                            publishReportedSmsStatus(preferences, pendingReport)
                        } catch (error: GatewayHttpException) {
                            if (error.statusCode == 401 || error.statusCode == 403) {
                                publishStatus(preferences, "needs_repair", ApiClient.describeFailure(error))
                                stopRelay(clearPairing = true)
                                return
                            }
                            if (error.statusCode in setOf(400, 404, 409, 422)) {
                                preferences.clearPendingSmsReport(pendingReport.jobId)
                                inMemoryPendingReport = null
                                publishStatus(
                                    preferences,
                                    "attention",
                                    "تغيّرت حالة الرسالة على الخادم ولم تعد النتيجة قابلة للتسجيل. راجع سجل الرسائل قبل أي إعادة يدوية؛ لن يعيد الهاتف إرسالها تلقائياً."
                                )
                            } else {
                                throw error
                            }
                        }
                    }
                }

                val claim = api.claim(token)
                consecutiveFailures = 0
                idleDelayMs = IDLE_DELAY_MIN_MS
                markServerSuccess(preferences)

                if (claim.deviceDisabled) {
                    publishStatus(preferences, "disabled", "تم إيقاف هذا الهاتف من لوحة تاجر تراك. تواصل مع مسؤول المتجر.")
                    stopRelay()
                    return
                }
                if (claim.gatewayPaused) {
                    publishStatus(preferences, "paused", "الخادم متاح، لكن خدمة SMS متوقفة مؤقتاً من لوحة تاجر تراك.")
                    delay(PAUSED_DELAY_MS)
                    continue
                }
                if (claim.merchantDisabled) {
                    publishStatus(preferences, "paused", "الخادم متاح، لكن تفعيل SMS متوقف من إعدادات المتجر.")
                    delay(PAUSED_DELAY_MS)
                    continue
                }

                val job = claim.job
                if (job == null) {
                    publishStatus(preferences, "connected", "متصل بخادم تاجر تراك — بانتظار طلب جديد.")
                    delay(idleDelayMs)
                    idleDelayMs = (idleDelayMs + IDLE_DELAY_STEP_MS).coerceAtMost(IDLE_DELAY_MAX_MS)
                    continue
                }

                publishStatus(preferences, "sending", "وصل طلب جديد. جارٍ إرسال رابط التتبع عبر الشريحة المختارة...")
                val sendingSubscriptionId = preferences.nextSubscriptionIdForMessage()
                val result = sendSms(job, sendingSubscriptionId)
                val pending = PendingSmsReport(
                    jobId = job.id,
                    deviceId = tokenStore.deviceId(),
                    status = result.status,
                    error = result.error
                )
                if (!preferences.savePendingSmsReport(pending)) {
                    inMemoryPendingReport = pending
                    Log.e(TAG, "Could not persist the pending SMS report; retaining it in memory.")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: GatewayHttpException) {
                if (error.statusCode == 401 || error.statusCode == 403) {
                    publishStatus(preferences, "needs_repair", ApiClient.describeFailure(error))
                    stopRelay(clearPairing = true)
                    return
                }
                consecutiveFailures += 1
                preferences.consecutiveRelayFailures = consecutiveFailures
                val retryDelay = ApiClient.suggestedRetryDelayMs(error, consecutiveFailures)
                val reason = describeConnectionFailure(error)
                Log.w(TAG, "Gateway request failed with HTTP " + error.statusCode + "; retrying in " + retryDelay + "ms.")
                publishStatus(
                    preferences,
                    if (hasValidatedInternet()) "retrying" else "offline",
                    reason + " المحاولة التالية بعد " + (retryDelay / 1_000L) + " ث."
                )
                delay(retryDelay)
            } catch (error: Exception) {
                consecutiveFailures += 1
                preferences.consecutiveRelayFailures = consecutiveFailures
                val retryDelay = ApiClient.suggestedRetryDelayMs(error, consecutiveFailures)
                val reason = describeConnectionFailure(error)
                Log.w(
                    TAG,
                    "Gateway relay operation failed (" + error.javaClass.simpleName + "); retrying in " + retryDelay + "ms.",
                    error
                )
                publishStatus(
                    preferences,
                    if (hasValidatedInternet()) "retrying" else "offline",
                    reason + " المحاولة التالية بعد " + (retryDelay / 1_000L) + " ث."
                )
                delay(retryDelay)
            }
        }
    }

    private fun describeConnectionFailure(error: Throwable): String {
        if (!hasValidatedInternet()) {
            return "لا يوجد اتصال إنترنت صالح على الهاتف. فعّل Wi-Fi أو بيانات الهاتف."
        }
        return ApiClient.describeFailure(error)
    }

    private fun hasValidatedInternet(): Boolean {
        return runCatching {
            val manager = getSystemService(ConnectivityManager::class.java)
            val network = manager.activeNetwork ?: return false
            val capabilities = manager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(true)
    }

    private fun markServerSuccess(preferences: AppPreferences) {
        preferences.relayLastSuccessfulPollAt = System.currentTimeMillis()
        preferences.consecutiveRelayFailures = 0
    }

    private fun publishReportedSmsStatus(preferences: AppPreferences, report: PendingSmsReport) {
        val message = when (report.status) {
            "submitted" -> "تم تسجيل قبول Android لرسالة التتبع لدى تاجر تراك. هذا لا يعني تأكيد وصولها إلى العميل."
            "failed" -> "تعذّر إرسال الرسالة: " + (report.error ?: "سبب غير محدد.") + " تم تسجيل النتيجة لدى تاجر تراك."
            else -> "تم تسجيل أن نتيجة الرسالة غير مؤكدة. لن يعيد الهاتف إرسالها تلقائياً لتجنب التكرار."
        }
        publishStatus(preferences, "connected", message)
    }

    private fun publishStatus(preferences: AppPreferences, state: String, message: String) {
        preferences.relayConnectionState = state
        preferences.relayStatusMessage = message
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()
        updateNotification(message)
    }

    private suspend fun sendSms(job: SmsJob, subscriptionId: Int): SmsSendOutcome {
        if (job.id.isBlank() || job.phone.isBlank() || job.message.isBlank()) {
            return SmsSendOutcome("failed", "بيانات رسالة الطلب غير مكتملة.")
        }
        if (subscriptionId < 0) {
            return SmsSendOutcome("failed", "لم يتم اختيار شريحة لإرسال الرسائل.")
        }

        val activeSubscriptions = if (
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
            }.getOrNull()
        } else {
            return SmsSendOutcome("failed", "إذن الوصول إلى شرائح الاتصال غير متاح. افتح إعدادات التطبيق واسمح به ثم أعد اختيار الشريحة.")
        }
        if (activeSubscriptions != null && activeSubscriptions.none { it.subscriptionId == subscriptionId }) {
            return SmsSendOutcome("failed", "الشريحة المختارة لم تعد متاحة. افتح إعداد إرسال الرسائل واختر الشريحة من جديد.")
        }

        val result = SmsResultBus.register(job.id)
        return try {
            val manager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            val resultIntent = Intent(this, SmsSendResultReceiver::class.java).apply {
                action = SmsSendResultReceiver.ACTION_SMS_SENT
                putExtra(SmsSendResultReceiver.EXTRA_JOB_ID, job.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                job.id.hashCode(),
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            manager.sendTextMessage(job.phone, null, job.message, pendingIntent, null)
            withTimeoutOrNull(SMS_RESULT_TIMEOUT_MS) { result.await() }
                ?: SmsSendOutcome(
                    "unknown",
                    "لم يؤكد Android نتيجة إرسال الرسالة. لن يعيد الهاتف إرسالها تلقائياً لتجنب التكرار."
                )
        } catch (error: SecurityException) {
            SmsSendOutcome("failed", "إذن إرسال الرسائل غير متاح. افتح إعدادات التطبيق واسمح بإرسال SMS.")
        } catch (error: IllegalArgumentException) {
            SmsSendOutcome("failed", "رفض Android رقم الهاتف أو محتوى الرسالة. تحقق من بيانات الطلب.")
        } catch (error: Exception) {
            SmsSendOutcome(
                "unknown",
                "لم يتمكن الهاتف من تأكيد نتيجة الرسالة (" + error.javaClass.simpleName + "). لن يعيد إرسالها تلقائياً."
            )
        } finally {
            SmsResultBus.remove(job.id)
        }
    }

    private fun stopRelay(clearPairing: Boolean = false) {
        if (stopping.getAndSet(true)) return
        relayJob?.cancel()
        AppPreferences(this).relayEnabled = false
        if (clearPairing) DeviceTokenStore(this).clear()
        releaseCpuWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireCpuWakeLock() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (wakeLock?.isHeld == true) return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TajerTrackPhone:SmsRelay"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) lock.release()
        }
        wakeLock = null
    }

    private fun buildNotification(message: String = "جارٍ الاتصال بخادم تاجر تراك"): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tajertrack)
            .setContentTitle("TajerTrack SMS")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تشغيل رسائل تاجر تراك",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "إشعار دائم أثناء إرسال روابط تتبع الطلبات من شريحة هاتفك"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopping.set(true)
        relayJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancel()
        releaseCpuWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.tajertrack.phone.action.STOP_RELAY"
        private const val TAG = "TajerTrackSmsRelay"
        private const val CHANNEL_ID = "tajertrack_sms_relay"
        private const val NOTIFICATION_ID = 4120
        private const val IDLE_DELAY_MIN_MS = 10_000L
        private const val IDLE_DELAY_STEP_MS = 5_000L
        private const val IDLE_DELAY_MAX_MS = 30_000L
        private const val PAUSED_DELAY_MS = 30_000L
        private const val SMS_RESULT_TIMEOUT_MS = 90_000L
    }
}
