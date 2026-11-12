package com.tajertrack.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.SmsManager
import com.tajertrack.phone.MainActivity
import com.tajertrack.phone.R
import com.tajertrack.phone.data.ApiClient
import com.tajertrack.phone.data.AppPreferences
import com.tajertrack.phone.data.DeviceTokenStore
import com.tajertrack.phone.data.GatewayHttpException
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
            stopRelay()
            return
        }

        var idleDelayMs = IDLE_DELAY_MIN_MS
        val api = ApiClient()
        while (!stopping.get()) {
            try {
                val claim = api.claim(token)
                if (claim.deviceDisabled) {
                    updateNotification("تم إيقاف هذا الهاتف من لوحة تاجر تراك")
                    stopRelay()
                    return
                }
                if (claim.gatewayPaused) {
                    updateNotification("الإرسال متوقف مؤقتاً من لوحة التحكم")
                    delay(PAUSED_DELAY_MS)
                    continue
                }
                if (claim.merchantDisabled) {
                    updateNotification("تفعيل SMS متوقف من إعدادات المتجر")
                    delay(PAUSED_DELAY_MS)
                    continue
                }

                val job = claim.job
                if (job == null) {
                    idleDelayMs = (idleDelayMs + IDLE_DELAY_STEP_MS).coerceAtMost(IDLE_DELAY_MAX_MS)
                    updateNotification("متصل — بانتظار طلب جديد")
                    delay(idleDelayMs)
                    continue
                }

                idleDelayMs = IDLE_DELAY_MIN_MS
                updateNotification("جارٍ إرسال رابط تتبع طلب")
                val result = sendSms(job, preferences.selectedSubscriptionId)
                api.report(token, job.id, result.status, result.error)
                updateNotification(
                    if (result.status == "submitted") "تم تسليم الرسالة إلى نظام الهاتف" else "تعذّر إرسال رسالة؛ تم تسجيل النتيجة"
                )
                delay(AFTER_JOB_DELAY_MS)
            } catch (error: CancellationException) {
                throw error
            } catch (error: GatewayHttpException) {
                if (error.statusCode == 401 || error.statusCode == 403) {
                    updateNotification("انتهى اتصال هذا الهاتف. أعد ربطه من التطبيق")
                    stopRelay(clearPairing = true)
                    return
                }
                updateNotification("تعذر الاتصال — ستتم إعادة المحاولة تلقائياً")
                delay(ERROR_DELAY_MS)
            } catch (error: Exception) {
                updateNotification("اتصال غير مستقر — ستتم إعادة المحاولة")
                delay(ERROR_DELAY_MS)
            }
        }
    }

    private suspend fun sendSms(job: SmsJob, subscriptionId: Int): SmsSendOutcome {
        if (job.id.isBlank() || job.phone.isBlank() || job.message.isBlank()) {
            return SmsSendOutcome("failed", "The SMS job was incomplete.")
        }
        if (subscriptionId < 0) {
            return SmsSendOutcome("failed", "No SIM is selected for sending SMS.")
        }

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
        val result = SmsResultBus.register(job.id)

        return try {
            manager.sendTextMessage(job.phone, null, job.message, pendingIntent, null)
            withTimeoutOrNull(SMS_RESULT_TIMEOUT_MS) { result.await() }
                ?: SmsSendOutcome(
                    "unknown",
                    "Android did not report whether the SMS was submitted. TajerTrack will not automatically resend it."
                )
        } catch (error: SecurityException) {
            SmsSendOutcome("failed", "SMS permission is not available. Open TajerTrack SMS and allow sending.")
        } catch (error: IllegalArgumentException) {
            SmsSendOutcome("failed", "The customer phone number or SMS content was rejected by Android.")
        } catch (error: Exception) {
            SmsSendOutcome("unknown", "The phone could not confirm the SMS result: ${error.message.orEmpty()}")
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

    private fun buildNotification(message: String = "جارٍ تجهيز الاتصال"): Notification {
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
        private const val CHANNEL_ID = "tajertrack_sms_relay"
        private const val NOTIFICATION_ID = 4120
        private const val IDLE_DELAY_MIN_MS = 10_000L
        private const val IDLE_DELAY_STEP_MS = 5_000L
        private const val IDLE_DELAY_MAX_MS = 30_000L
        private const val PAUSED_DELAY_MS = 30_000L
        private const val ERROR_DELAY_MS = 25_000L
        private const val AFTER_JOB_DELAY_MS = 1_000L
        private const val SMS_RESULT_TIMEOUT_MS = 90_000L
    }
}
