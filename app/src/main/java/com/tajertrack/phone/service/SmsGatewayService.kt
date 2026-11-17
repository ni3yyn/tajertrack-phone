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
        var profiles = tokenStore.readProfiles()
        if (profiles.isEmpty()) {
            publishStatus(preferences, "needs_pairing", "اربط متجرك بخط إرسال واحد على الأقل من إعدادات تاجر تراك.")
            stopRelay()
            return
        }

        var idleDelayMs = IDLE_DELAY_MIN_MS
        var consecutiveFailures = preferences.consecutiveRelayFailures
        val api = ApiClient()
        publishStatus(preferences, "connecting", "جارٍ الاتصال بخادم تاجر تراك...")

        while (!stopping.get()) {
            try {
                profiles = tokenStore.readProfiles()
                if (profiles.isEmpty()) {
                    publishStatus(preferences, "needs_pairing", "لم يعد لهذا الهاتف خط إرسال مرتبط. أنشئ دعوة جديدة من لوحة المتجر.")
                    stopRelay()
                    return
                }

                // Recover the one durable in-flight record before claiming anything
                // else. "claimed" proves SmsManager was not called; "invoking" is
                // ambiguous and must never be automatically resent.
                val pendingReport = preferences.pendingSmsReport()
                if (pendingReport != null) {
                    val reportProfile = profiles.firstOrNull { it.deviceId == pendingReport.deviceId }
                    if (reportProfile == null) {
                        preferences.clearPendingSmsReport(pendingReport.jobId)
                        publishStatus(
                            preferences,
                            "attention",
                            "تعذر إرسال نتيجة رسالة من خط ربط سابق. تحقق من سجل الرسائل في لوحة تاجر تراك قبل أي إعادة يدوية."
                        )
                    } else {
                        val recovered = when (pendingReport.stage) {
                            AppPreferences.STAGE_CLAIMED -> pendingReport.copy(
                                status = "not_sent",
                                error = "استعاد التطبيق الطلب قبل استدعاء Android SmsManager."
                            )
                            AppPreferences.STAGE_INVOKING -> pendingReport.copy(
                                status = "unknown",
                                error = "أُعيد تشغيل التطبيق أثناء محاولة إرسال SMS. قد تكون الرسالة أُرسلت؛ لن تُكرر تلقائياً."
                            )
                            else -> pendingReport
                        }

                        publishStatus(preferences, "reporting", "جارٍ حفظ نتيجة الرسالة السابقة لدى تاجر تراك...")
                        api.report(
                            token = reportProfile.token,
                            jobId = recovered.jobId,
                            claimToken = recovered.claimToken,
                            status = recovered.status,
                            error = recovered.error,
                            retryable = recovered.retryable,
                            failureCode = recovered.failureCode
                        )
                        preferences.clearPendingSmsReport(recovered.jobId)
                        markServerSuccess(preferences)
                        publishReportedSmsStatus(preferences, recovered)
                        // One job at a time: never claim a second order until the
                        // first order's result is durably acknowledged by the API.
                        continue
                    }
                }

                profiles = tokenStore.readProfiles()
                if (profiles.isEmpty()) {
                    publishStatus(preferences, "needs_pairing", "لم يعد لهذا الهاتف خط إرسال مرتبط.")
                    stopRelay()
                    return
                }

                if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    publishStatus(
                        preferences,
                        "attention",
                        "إذن الوصول إلى شرائح الاتصال غير متاح. اسمح به من إعدادات Android؛ لن تُحجز طلبات جديدة حتى يعود الإذن."
                    )
                    delay(PAUSED_DELAY_MS)
                    continue
                }

                // Permission loss must pause BEFORE a server claim. It must not
                // consume attempts or turn a perfectly valid order into a failure.
                if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    publishStatus(
                        preferences,
                        "attention",
                        "إذن إرسال SMS غير متاح. فعّله من إعدادات Android؛ ستبقى الطلبات في قائمة الانتظار دون استهلاك محاولات."
                    )
                    delay(PAUSED_DELAY_MS)
                    continue
                }

                val activeSubscriptions = runCatching {
                    getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                }.getOrElse {
                    Log.w(TAG, "Could not read active SIM subscriptions.", it)
                    emptyList()
                }
                val enabledSubscriptionIds = preferences.enabledSubscriptionIds()
                var hasEligibleLine = false
                var disabledLineSeen = false
                var servicePaused = false
                var operatorNeedsSetup = false
                var operatorConflict = false
                var claimedAndSent = false

                for (profile in profiles) {
                    val subscriptionId = resolveSubscriptionId(
                        operatorCode = profile.operatorCode,
                        activeSubscriptions = activeSubscriptions,
                        enabledSubscriptionIds = enabledSubscriptionIds,
                        preferences = preferences
                    ) ?: continue

                    hasEligibleLine = true
                    val selectedSubscription = activeSubscriptions.firstOrNull {
                        it.subscriptionId == subscriptionId
                    }
                    val effectiveOperatorCode = profile.operatorCode
                        ?: selectedSubscription?.let(::detectOperator)

                    if (effectiveOperatorCode == null) {
                        operatorNeedsSetup = true
                        continue
                    }

                    val claim = try {
                        api.claim(profile.token, effectiveOperatorCode)
                    } catch (error: GatewayHttpException) {
                        if (error.statusCode == 401 || error.statusCode == 403) {
                            tokenStore.removeProfile(profile.deviceId)
                            Log.i(TAG, "Removed revoked sender-line credential for device " + profile.deviceId)
                            continue
                        }
                        throw error
                    }

                    markServerSuccess(preferences)
                    consecutiveFailures = 0
                    preferences.consecutiveRelayFailures = 0
                    idleDelayMs = IDLE_DELAY_MIN_MS

                    if (claim.deviceDisabled) {
                        disabledLineSeen = true
                        continue
                    }
                    if (claim.gatewayPaused || claim.merchantDisabled) {
                        servicePaused = true
                        break
                    }
                    if (claim.operatorConflict || claim.operatorUnassigned) {
                        operatorConflict = true
                        continue
                    }

                    val job = claim.job ?: continue

                    // Recheck the exact SIM and permissions immediately before
                    // dispatch. A changed SIM/permission returns the claim safely
                    // without ever calling SmsManager.
                    val stillHasPermission =
                        checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                        checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    val simStillPresent = runCatching {
                        getSystemService(SubscriptionManager::class.java)
                            .activeSubscriptionInfoList.orEmpty()
                            .any { it.subscriptionId == subscriptionId }
                    }.getOrDefault(false)

                    if (!stillHasPermission || !simStillPresent) {
                        val saved = preferences.savePendingSmsClaim(job.id, profile.deviceId, job.claimToken)
                        if (saved) {
                            publishStatus(
                                preferences,
                                "attention",
                                "تغيّر إذن SMS أو حالة الشريحة قبل الإرسال. لم يستدعِ التطبيق Android؛ سيعيد الطلب إلى قائمة الانتظار."
                            )
                        } else {
                            Log.e(TAG, "Could not persist pre-dispatch claim; leaving server lease to recover safely.")
                            publishStatus(preferences, "attention", "تعذر حفظ سجل الاستعادة محلياً. لم تُرسل الرسالة، وسيستعيد الخادم الحجز بعد انتهاء مهلة الأمان.")
                        }
                        claimedAndSent = true
                        break
                    }

                    // Save the claim synchronously before any operation that can
                    // send SMS. A crash in this stage is safely recoverable.
                    if (!preferences.savePendingSmsClaim(job.id, profile.deviceId, job.claimToken)) {
                        Log.e(TAG, "Could not persist SMS claim; refusing to call SmsManager.")
                        publishStatus(
                            preferences,
                            "attention",
                            "تعذر حفظ سجل إرسال الطلب على الهاتف. لم تُرسل الرسالة؛ سيعيد الخادم الطلب تلقائياً بعد انتهاء الحجز."
                        )
                        claimedAndSent = true
                        break
                    }

                    try {
                        api.beginDispatch(profile.token, job.id, job.claimToken)
                    } catch (error: Exception) {
                        // The request may have reached the server, but the local
                        // stage is still "claimed" and SmsManager was not called.
                        // The next loop reports not_sent idempotently.
                        publishStatus(
                            preferences,
                            "retrying",
                            "تعذر تأكيد بدء الإرسال مع الخادم. لم يستدعِ التطبيق Android؛ جارٍ استعادة الطلب بأمان."
                        )
                        throw error
                    }

                    if (!preferences.markPendingSmsInvoking(job.id)) {
                        // Never send without a durable marker. The journal remains
                        // in "claimed", so recovery can safely requeue the job.
                        publishStatus(preferences, "attention", "تعذر حفظ حالة الإرسال على الهاتف. لم تُرسل الرسالة وسيُعاد الطلب إلى الانتظار.")
                        claimedAndSent = true
                        break
                    }

                    publishStatus(
                        preferences,
                        "sending",
                        "وصل طلب جديد. جارٍ إرسال رابط التتبع من شريحة الشبكة المطابقة..."
                    )

                    val result = sendSms(job, subscriptionId)
                    val pending = PendingSmsReport(
                        jobId = job.id,
                        deviceId = profile.deviceId,
                        status = result.status,
                        error = result.error,
                        claimToken = job.claimToken,
                        stage = AppPreferences.STAGE_RESULT,
                        retryable = result.retryable,
                        failureCode = result.failureCode
                    )
                    if (!preferences.savePendingSmsOutcome(pending)) {
                        Log.e(TAG, "Could not persist the SMS outcome; keeping it in memory for immediate reporting.")
                        publishStatus(
                            preferences,
                            "attention",
                            "وصلت نتيجة Android لكن تعذر حفظها محلياً. سيحاول التطبيق تسجيلها فوراً، ولن يعيد إرسال الطلب."
                        )
                    }
                    claimedAndSent = true
                    break
                }

                if (claimedAndSent) {
                    // Immediately return to the top to report the durable journal.
                    continue
                }
                if (servicePaused) {
                    publishStatus(
                        preferences,
                        "paused",
                        "خدمة SMS متوقفة مؤقتاً من لوحة الإدارة أو إعدادات المتجر. الطلبات المؤهلة تبقى في قائمة الانتظار."
                    )
                    delay(PAUSED_DELAY_MS)
                    continue
                }

                if (tokenStore.readProfiles().isEmpty()) {
                    publishStatus(preferences, "needs_pairing", "أُلغيت جميع خطوط الإرسال المرتبطة بهذا الهاتف.")
                    stopRelay()
                    return
                }

                when {
                    operatorConflict -> publishStatus(
                        preferences,
                        "attention",
                        "تعذر تصنيف خط الإرسال: يوجد خط آخر للشبكة نفسها أو أن هذا الهاتف مرتبط بشبكة مختلفة. لم تُرسل أي رسالة."
                    )
                    operatorNeedsSetup -> publishStatus(
                        preferences,
                        "attention",
                        "تعذر التعرف على شبكة الشريحة الأساسية. تحقق من أن الشريحة جزائرية واخترها من إعدادات الإرسال."
                    )
                    !hasEligibleLine -> publishStatus(
                        preferences,
                        "attention",
                        "لا توجد شريحة مفعّلة تطابق أي خط مرتبط. أدخل الشريحة الصحيحة أو فعّل الشريحة الثانية."
                    )
                    disabledLineSeen -> publishStatus(
                        preferences,
                        "disabled",
                        "بعض خطوط الإرسال موقوفة من لوحة تاجر تراك. ستبقى طلباتها في الانتظار حتى تفعيل الخط."
                    )
                    else -> publishStatus(
                        preferences,
                        "connected",
                        "متصل بخادم تاجر تراك — ينتظر طلباً يطابق إحدى شبكات الإرسال المرتبطة."
                    )
                }

                delay(idleDelayMs)
                idleDelayMs = (idleDelayMs + IDLE_DELAY_STEP_MS).coerceAtMost(IDLE_DELAY_MAX_MS)
            } catch (error: CancellationException) {
                throw error
            } catch (error: GatewayHttpException) {
                if (error.statusCode == 401 || error.statusCode == 403) {
                    publishStatus(preferences, "needs_repair", ApiClient.describeFailure(error))
                    stopRelay()
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

    private fun resolveSubscriptionId(
        operatorCode: String?,
        activeSubscriptions: List<android.telephony.SubscriptionInfo>,
        enabledSubscriptionIds: Set<Int>,
        preferences: AppPreferences
    ): Int? {
        val enabledActive = activeSubscriptions.filter {
            it.subscriptionId in enabledSubscriptionIds
        }
        if (operatorCode == null) {
            val primary = preferences.selectedSubscriptionId
            return primary.takeIf { id -> enabledActive.any { it.subscriptionId == id } }
        }

        return enabledActive.firstOrNull {
            detectOperator(it) == operatorCode
        }?.subscriptionId
    }

    /**
     * Match the SIM by its network identity (Algerian MCC 603) before
     * falling back to the carrier label. Android labels are not consistent
     * across manufacturers, languages and SIM configurations.
     */
    private fun detectOperator(subscription: android.telephony.SubscriptionInfo): String? {
        val mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subscription.mccString
        } else {
            @Suppress("DEPRECATION")
            subscription.mcc.toString()
        }?.filter(Char::isDigit)
        val mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            subscription.mncString
        } else {
            @Suppress("DEPRECATION")
            subscription.mnc.toString()
        }?.filter(Char::isDigit)

        if (mcc == "603") {
            when (mnc?.toIntOrNull()) {
                1 -> return "mobilis"
                2 -> return "djezzy"
                3 -> return "ooredoo"
            }
        }

        val value = subscription.carrierName?.toString().orEmpty().lowercase()
        return when {
            "djezzy" in value || "جازي" in value || "ota" in value -> "djezzy"
            "mobilis" in value || "موبيليس" in value || "atm" in value -> "mobilis"
            "ooredoo" in value || "اوريدو" in value || "أوريدو" in value ||
                "wataniya" in value || "wta" in value -> "ooredoo"
            else -> null
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
