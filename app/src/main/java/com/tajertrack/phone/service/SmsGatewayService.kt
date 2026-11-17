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
import com.tajertrack.phone.data.DeviceCredential
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

private data class SmsPollLine(
    val profile: DeviceCredential,
    val subscriptionId: Int,
    val operatorCode: String,
    val allowCrossNetworkFallback: Boolean
)

class SmsGatewayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var relayJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val stopping = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        isRunning = true
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

                // A short persistent spacing avoids a burst when a phone
                // reconnects with a large backlog. It is pacing, not a daily
                // quota: eligible messages continue until the 72-hour window.
                val spacingRemaining = preferences.lastSmsDispatchAt +
                    MIN_SMS_SEND_SPACING_MS - System.currentTimeMillis()
                if (spacingRemaining > 0) delay(spacingRemaining)

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

                // First poll every enabled SIM for on-network customer jobs.
                // After those exact matches have had priority, the neutral main
                // phone gets one fallback pass for networks without an enabled
                // dedicated sender. Invited profiles remain carrier-scoped.
                val profileLines = buildList {
                    profiles.forEach { profile ->
                        if (profile.operatorCode != null) {
                            val subscriptionId = resolveSubscriptionId(
                                operatorCode = profile.operatorCode,
                                activeSubscriptions = activeSubscriptions,
                                enabledSubscriptionIds = enabledSubscriptionIds,
                                preferences = preferences
                            )
                            if (subscriptionId != null) {
                                add(SmsPollLine(profile, subscriptionId, profile.operatorCode, false))
                            }
                        } else {
                            val enabledSubscriptions = activeSubscriptions.filter {
                                it.subscriptionId in enabledSubscriptionIds
                            }
                            if (enabledSubscriptions.any { detectOperator(it) == null }) {
                                operatorNeedsSetup = true
                            }
                            val byOperator = enabledSubscriptions.mapNotNull { subscription ->
                                detectOperator(subscription)?.let { operator -> operator to subscription }
                            }.groupBy({ it.first }, { it.second })
                            byOperator.forEach { (operator, matchingSims) ->
                                val chosenSim = matchingSims.firstOrNull {
                                    it.subscriptionId == preferences.selectedSubscriptionId
                                } ?: matchingSims.first()
                                add(SmsPollLine(profile, chosenSim.subscriptionId, operator, false))
                            }

                            val fallbackSim = enabledSubscriptions.firstOrNull {
                                it.subscriptionId == preferences.selectedSubscriptionId
                            } ?: enabledSubscriptions.firstOrNull()
                            fallbackSim?.let { subscription ->
                                val operator = detectOperator(subscription)
                                if (operator != null) {
                                    add(SmsPollLine(profile, subscription.subscriptionId, operator, true))
                                } else {
                                    operatorNeedsSetup = true
                                }
                            }
                        }
                    }
                }

                for (line in profileLines) {
                    val profile = line.profile
                    val subscriptionId = line.subscriptionId
                    val effectiveOperatorCode = line.operatorCode
                    hasEligibleLine = true
                    val claim = try {
                        api.claim(profile.token, effectiveOperatorCode, line.allowCrossNetworkFallback)
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
                        if (line.allowCrossNetworkFallback) {
                            "وصل طلب جديد لشبكة لا تملك خطاً مخصصاً. جارٍ الإرسال من الشريحة الأساسية المتاحة..."
                        } else {
                            "وصل طلب جديد. جارٍ إرسال رابط التتبع من خط الشبكة المطابقة..."
                        }
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
                        "متصل بخادم تاجر تراك — يستخدم الخط المخصص للشبكة، ويغطي الهاتف الرئيسي الشبكات غير المربوطة."
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

        val matching = enabledActive.filter {
            detectOperator(it) == operatorCode
        }
        return matching.firstOrNull { it.subscriptionId == preferences.selectedSubscriptionId }?.subscriptionId
            ?: matching.firstOrNull()?.subscriptionId
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
            "submitted" ->
                "أكد Android قبول رسالة رابط التتبع للإرسال. هذا لا يثبت وصولها إلى هاتف العميل."
            "failed" -> if (report.retryable) {
                "تعذر الإرسال مؤقتاً: ${report.error ?: "سبب غير محدد."} بقي الطلب في قائمة الانتظار وسيُعاد بعد مهلة متزايدة."
            } else {
                "تعذر إرسال الرسالة: ${report.error ?: "سبب غير محدد."} يلزم تصحيح السبب قبل إعادة المحاولة."
            }
            "not_sent" ->
                "لم تُرسل الرسالة؛ أُعيد الطلب إلى قائمة الانتظار دون احتساب محاولة إرسال."
            else ->
                "نتيجة الإرسال غير مؤكدة. حُفظت الحالة للمراجعة ولن يكرر الهاتف الرسالة تلقائياً."
        }
        publishStatus(
            preferences,
            if (report.status == "unknown") "attention" else "connected",
            message
        )
    }

    private fun publishStatus(preferences: AppPreferences, state: String, message: String) {
        preferences.relayConnectionState = state
        preferences.relayStatusMessage = message
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()
        updateNotification(message)
    }

    private suspend fun sendSms(job: SmsJob, subscriptionId: Int): SmsSendOutcome {
        if (job.id.isBlank() || job.phone.isBlank() || job.message.isBlank()) {
            return SmsSendOutcome(
                status = "failed",
                error = "بيانات رسالة الطلب غير مكتملة.",
                failureCode = "invalid_sms_job"
            )
        }
        if (subscriptionId < 0) {
            return SmsSendOutcome(
                status = "not_sent",
                error = "لم تعد الشريحة المختارة متاحة. أعد اختيار شريحة مطابقة للشبكة.",
                failureCode = "selected_sim_unavailable"
            )
        }

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            return SmsSendOutcome(
                status = "not_sent",
                error = "أحد أذونات SMS أو شرائح الاتصال غير متاح. لم يستدعِ التطبيق Android.",
                failureCode = "runtime_permission_missing"
            )
        }

        val activeSubscriptions = runCatching {
            getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
        }.getOrElse {
            Log.w(TAG, "Could not recheck the selected SIM before SMS dispatch.", it)
            emptyList()
        }
        if (activeSubscriptions.none { it.subscriptionId == subscriptionId }) {
            return SmsSendOutcome(
                status = "not_sent",
                error = "أُزيلت الشريحة المختارة قبل الإرسال. سيبقى الطلب في قائمة الانتظار.",
                failureCode = "sim_removed_before_send"
            )
        }

        val result = SmsResultBus.register(job.id)
        var sendCallStarted = false
        return try {
            val manager = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            val resultIntent = Intent(this, SmsSendResultReceiver::class.java).apply {
                action = SmsSendResultReceiver.ACTION_SMS_SENT
                data = android.net.Uri.parse("tajertrack-sms://sent/${job.id}")
                putExtra(SmsSendResultReceiver.EXTRA_JOB_ID, job.id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                job.id.hashCode(),
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // The carrier supplies the raw status-report PDU by filling in this
            // explicit, app-private receiver's mutable PendingIntent. The
            // receiver itself is not exported, limiting who can invoke it.
            val deliveryIntent = Intent(this, SmsSendResultReceiver::class.java).apply {
                action = SmsSendResultReceiver.ACTION_SMS_DELIVERED
                data = android.net.Uri.parse("tajertrack-sms://delivered/${job.id}")
                putExtra(SmsSendResultReceiver.EXTRA_JOB_ID, job.id)
                putExtra(SmsSendResultReceiver.EXTRA_CLAIM_TOKEN, job.claimToken)
            }
            val deliveryPendingIntent = PendingIntent.getBroadcast(
                this,
                job.id.hashCode() xor 0x4D454E,
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // Persisted stage is already "invoking" before this line. Any process
            // death after it is treated as ambiguous, never automatically resent.
            sendCallStarted = true
            manager.sendTextMessage(job.phone, null, job.message, pendingIntent, deliveryPendingIntent)

            withTimeoutOrNull(SMS_RESULT_TIMEOUT_MS) { result.await() }
                ?: SmsSendOutcome(
                    status = "unknown",
                    error = "لم يؤكد Android نتيجة إرسال الرسالة خلال المهلة. قد تكون أُرسلت؛ لن تُكرر تلقائياً.",
                    failureCode = "sent_callback_timeout"
                )
        } catch (error: SecurityException) {
            SmsSendOutcome(
                status = "not_sent",
                error = "رفض Android الإرسال بسبب إذن غير متاح. لم تُرسل الرسالة؛ امنح الإذن ثم سيُعاد الطلب.",
                failureCode = "runtime_permission_revoked"
            )
        } catch (error: IllegalArgumentException) {
            SmsSendOutcome(
                status = "failed",
                error = "رفض Android رقم الهاتف أو محتوى الرسالة. تحقق من رقم العميل وبيانات الطلب.",
                failureCode = "invalid_sms_arguments"
            )
        } catch (error: Exception) {
            Log.w(TAG, "SMS call failed for job ${job.id}.", error)
            if (sendCallStarted) {
                SmsSendOutcome(
                    status = "unknown",
                    error = "انقطع تأكيد Android بعد بدء استدعاء SMS (${error.javaClass.simpleName}). قد تكون أُرسلت؛ لن تُكرر تلقائياً.",
                    failureCode = "sms_call_result_unknown"
                )
            } else {
                SmsSendOutcome(
                    status = "not_sent",
                    error = "تعذر تجهيز مدير SMS قبل استدعاء Android. لم تُرسل الرسالة.",
                    failureCode = "sms_manager_unavailable"
                )
            }
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
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
        const val ACTION_STOP = "com.tajertrack.phone.action.STOP_RELAY"
        private const val TAG = "TajerTrackSmsRelay"
        private const val CHANNEL_ID = "tajertrack_sms_relay"
        private const val NOTIFICATION_ID = 4120
        private const val IDLE_DELAY_MIN_MS = 10_000L
        private const val IDLE_DELAY_STEP_MS = 5_000L
        private const val IDLE_DELAY_MAX_MS = 30_000L
        private const val PAUSED_DELAY_MS = 30_000L
        private const val SMS_RESULT_TIMEOUT_MS = 90_000L
        private const val MIN_SMS_SEND_SPACING_MS = 3_000L
    }
}
