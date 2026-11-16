package com.tajertrack.phone

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tajertrack.phone.data.ApiClient
import com.tajertrack.phone.data.AppPreferences
import com.tajertrack.phone.data.AppUpdateCheckException
import com.tajertrack.phone.data.AppUpdateCheckResult
import com.tajertrack.phone.data.AppUpdateChecker
import com.tajertrack.phone.data.AppUpdateInfo
import com.tajertrack.phone.data.DeviceTokenStore
import com.tajertrack.phone.service.SmsGatewayService
import com.tajertrack.phone.service.SmsResultBus
import com.tajertrack.phone.service.SmsSendResultReceiver
import com.tajertrack.phone.ui.PhoneHomeScreen
import com.tajertrack.phone.ui.PhoneTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val tokenStore by lazy { DeviceTokenStore(this) }
    private val preferences by lazy { AppPreferences(this) }

    private var isConnected by mutableStateOf(false)
    private var isRelayRunning by mutableStateOf(false)
    private var relayConnectionState by mutableStateOf("stopped")
    private var statusSyncJob: Job? = null
    private var lastObservedRelayStatusAt = 0L
    private var storeName by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var simChoices by mutableStateOf(emptyList<SimChoice>())
    private var selectedSimId by mutableStateOf(-1)
    private var secondarySimId by mutableStateOf(-1)
    private var alternateSimsEnabled by mutableStateOf(false)
    private var isPairing by mutableStateOf(false)
    private var simSetupUnlocked by mutableStateOf(false)
    private var isSendingTestSms by mutableStateOf(false)
    private var isCheckingForUpdates by mutableStateOf(false)
    private var updateMessage by mutableStateOf("")
    private var availableUpdate by mutableStateOf<AppUpdateInfo?>(null)
    private var pendingStartAfterPermissions = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            refreshSimChoices()
            if (pendingStartAfterPermissions) {
                pendingStartAfterPermissions = false
                if (requiredPermissionsGranted()) {
                    simSetupUnlocked = true
                    preferences.smsSetupUnlocked = true
                    statusMessage = if (simChoices.isEmpty()) {
                        "تم السماح بالأذونات. أدخل شريحة اتصال فعّالة ثم حاول مجدداً."
                    } else {
                        "تم السماح بالأذونات. اختر الشريحة التي سيُرسل منها رابط التتبع."
                    }
                } else {
                    statusMessage = "يجب السماح بإرسال الرسائل والوصول إلى معلومات شرائح الاتصال والإشعارات لإكمال الإعداد."
                }
            }
        }

        refreshConnectionState()
        handlePairingIntent(intent)

        setContent {
            PhoneTheme {
                PhoneHomeScreen(
                    connected = isConnected,
                    relayRunning = isRelayRunning,
                    relayConnectionState = relayConnectionState,
                    storeName = storeName,
                    statusMessage = statusMessage,
                    pairing = isPairing,
                    simSetupUnlocked = simSetupUnlocked,
                    testSmsSending = isSendingTestSms,
                    simChoices = simChoices,
                    selectedSimId = selectedSimId,
                    secondarySimId = secondarySimId,
                    alternateSimsEnabled = alternateSimsEnabled,
                    onConnect = ::openPairingPage,
                    onStartRelay = ::requestPermissionsAndStart,
                    onStopRelay = ::stopRelay,
                    onSendTestSms = ::sendTestSms,
                    onSelectSim = ::selectSim,
                    onSelectSecondarySim = ::selectSecondarySim,
                    onToggleAlternateSims = ::toggleAlternateSims,
                    onReconnect = ::openPairingPage,
                    onExitApp = { finish() },
                    isCheckingForUpdates = isCheckingForUpdates,
                    updateMessage = updateMessage,
                    availableUpdateVersion = availableUpdate?.version,
                    onCheckForUpdates = ::checkForUpdates,
                    onDownloadUpdate = ::openUpdateDownload
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePairingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshConnectionState()
        refreshSimChoices()
        startRelayStatusSync()
    }

    override fun onPause() {
        statusSyncJob?.cancel()
        statusSyncJob = null
        super.onPause()
    }

    private fun startRelayStatusSync() {
        statusSyncJob?.cancel()
        statusSyncJob = lifecycleScope.launch {
            while (true) {
                isConnected = tokenStore.readProfiles().isNotEmpty()
                storeName = tokenStore.storeName()
                isRelayRunning = preferences.relayEnabled
                relayConnectionState = preferences.relayConnectionState
                val updatedAt = preferences.relayStatusUpdatedAt
                if (updatedAt > lastObservedRelayStatusAt) {
                    statusMessage = preferences.relayStatusMessage
                    lastObservedRelayStatusAt = updatedAt
                }
                delay(1_500L)
            }
        }
    }

    private fun refreshConnectionState() {
        isConnected = tokenStore.readProfiles().isNotEmpty()
        storeName = tokenStore.storeName()
        isRelayRunning = preferences.relayEnabled
        relayConnectionState = preferences.relayConnectionState
        if (preferences.relayStatusUpdatedAt > lastObservedRelayStatusAt) {
            statusMessage = preferences.relayStatusMessage
            lastObservedRelayStatusAt = preferences.relayStatusUpdatedAt
        }
        val permissionsGranted = requiredPermissionsGranted()
        simSetupUnlocked = permissionsGranted && (preferences.smsSetupUnlocked || preferences.relayEnabled)
        if (!permissionsGranted) preferences.smsSetupUnlocked = false
        selectedSimId = preferences.selectedSubscriptionId
        secondarySimId = preferences.secondarySubscriptionId
        alternateSimsEnabled = preferences.alternateSimsEnabled
    }

    private fun openPairingPage() {
        val redirectUri = Uri.encode("tajertrack-sms://pair")
        val url = "${BuildConfig.TAJERTRACK_BASE_URL}/mobile/sms-gateway/connect?redirect_uri=$redirectUri"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            statusMessage = "تعذر فتح صفحة تاجر تراك. تحقق من اتصال الإنترنت."
        }
    }

    private fun handlePairingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tajertrack-sms" || uri.host != "pair") return
        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) {
            statusMessage = "رابط الربط غير مكتمل. افتح صفحة الربط من التطبيق مجدداً."
            return
        }

        isPairing = true
        statusMessage = "جارٍ ربط الهاتف بمتجرك..."
        lifecycleScope.launch {
            try {
                val deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
                    .joinToString(" ")
                    .trim()
                    .take(80)
                    .ifBlank { "Android phone" }
                val result = ApiClient().completePairing(code, deviceName)
                val saved = tokenStore.saveProfile(
                    com.tajertrack.phone.data.DeviceCredential(
                        deviceId = result.deviceId,
                        token = result.deviceToken,
                        storeName = result.storeName,
                        operatorCode = result.operatorCode
                    )
                )
                if (!saved) {
                    throw IllegalStateException("تعذر حفظ صلاحية خط الإرسال بأمان على هذا الهاتف.")
                }
                isConnected = true
                storeName = result.storeName
                statusMessage = "تم ربط الهاتف بنجاح. انتقل إلى إعداد إرسال الرسائل لتفعيل الأذونات واختيار الشريحة."
            } catch (error: Exception) {
                statusMessage = ApiClient.describeFailure(error)
            } finally {
                isPairing = false
                setIntent(Intent(this@MainActivity, MainActivity::class.java))
            }
        }
    }

    private fun requestPermissionsAndStart() {
        // First tap only unlocks SIM setup after the required Android permissions
        // are granted. A second tap starts the relay after a SIM has been selected.
        if (simSetupUnlocked) {
            startRelay()
            return
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)) {
            statusMessage = "هذا الهاتف لا يدعم إرسال الرسائل النصية عبر شريحة اتصال."
            return
        }

        val required = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            pendingStartAfterPermissions = true
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            refreshSimChoices()
            simSetupUnlocked = true
            preferences.smsSetupUnlocked = true
            statusMessage = if (simChoices.isEmpty()) {
                "تم السماح بالأذونات. أدخل شريحة اتصال فعّالة ثم حاول مجدداً."
            } else {
                "تم السماح بالأذونات. اختر الشريحة التي سيُرسل منها رابط التتبع."
            }
        }
    }

    private fun requiredPermissionsGranted(): Boolean {
        val required = buildList {
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun refreshSimChoices() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simChoices = emptyList()
            return
        }

        val subscriptions = runCatching {
            getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
        }.getOrDefault(emptyList())

        simChoices = subscriptions.map { subscription ->
            val slot = subscription.simSlotIndex + 1
            val carrier = subscription.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM $slot"
            val operatorCode = detectOperator(
                carrierName = carrier,
                mcc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subscription.mccString
                } else {
                    @Suppress("DEPRECATION")
                    subscription.mcc.toString()
                },
                mnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subscription.mncString
                } else {
                    @Suppress("DEPRECATION")
                    subscription.mnc.toString()
                }
            )
            val operatorLabel = when (operatorCode) {
                "djezzy" -> "Djezzy"
                "mobilis" -> "Mobilis"
                "ooredoo" -> "Ooredoo"
                else -> carrier
            }
            SimChoice(subscription.subscriptionId, "$operatorLabel — $carrier — SIM $slot", operatorCode)
        }

        if (simChoices.none { it.subscriptionId == selectedSimId }) {
            selectedSimId = when {
                simChoices.size == 1 -> simChoices.first().subscriptionId
                else -> -1
            }
            preferences.selectedSubscriptionId = selectedSimId
        }

        if (
            secondarySimId == selectedSimId ||
            simChoices.none { it.subscriptionId == secondarySimId }
        ) {
            secondarySimId = -1
            preferences.secondarySubscriptionId = -1
            if (alternateSimsEnabled) {
                alternateSimsEnabled = false
                preferences.alternateSimsEnabled = false
                statusMessage = "لم تعد الشريحتان المحددتان متاحتين. تم إيقاف الشريحة الثانية؛ اخترها من جديد عند الحاجة."
            }
        }
    }

    /**
     * Resolve the SIM operator from the network identity first (Algerian MCC 603),
     * then fall back to Android's carrier label. Carrier labels vary by device,
     * locale and SIM profile, so they must not be the only source of truth.
     */
    private fun detectOperator(carrierName: String, mcc: String?, mnc: String?): String? {
        if (mcc?.filter(Char::isDigit) == "603") {
            when (mnc?.filter(Char::isDigit)?.toIntOrNull()) {
                1 -> return "mobilis"
                2 -> return "djezzy"
                3 -> return "ooredoo"
            }
        }

        val value = carrierName.lowercase()
        return when {
            "djezzy" in value || "جازي" in value || "ota" in value -> "djezzy"
            "mobilis" in value || "موبيليس" in value || "atm" in value -> "mobilis"
            "ooredoo" in value || "اوريدو" in value || "أوريدو" in value ||
                "wataniya" in value || "wta" in value -> "ooredoo"
            else -> null
        }
    }

    private fun selectSim(subscriptionId: Int) {
        if (subscriptionId == secondarySimId) {
            val previousPrimary = selectedSimId
            secondarySimId = previousPrimary
            preferences.secondarySubscriptionId = previousPrimary
        }
        selectedSimId = subscriptionId
        preferences.selectedSubscriptionId = subscriptionId
        statusMessage = "تم اختيار الشريحة الأساسية. ستُستخدم فقط مع خط الشبكة المطابق لها."
    }

    private fun selectSecondarySim(subscriptionId: Int) {
        if (subscriptionId == selectedSimId) return
        secondarySimId = subscriptionId
        preferences.secondarySubscriptionId = subscriptionId
        statusMessage = "تم اختيار الشريحة الثانية. فعّل استخدامها للسماح بخط الإرسال المطابق لها."
    }

    private fun toggleAlternateSims(enabled: Boolean) {
        if (enabled) {
            if (selectedSimId < 0 || secondarySimId < 0 || selectedSimId == secondarySimId ||
                simChoices.none { it.subscriptionId == selectedSimId } ||
                simChoices.none { it.subscriptionId == secondarySimId }
            ) {
                statusMessage = "اختر شريحة أساسية وثانية مختلفتين أولاً لتفعيل الشريحة الثانية."
                return
            }
        }
        alternateSimsEnabled = enabled
        preferences.alternateSimsEnabled = enabled
        statusMessage = if (enabled) {
            "تم السماح باستخدام الشريحة الثانية. يختار التطبيق الشريحة المطابقة لشبكة العميل ويرسل رسالة واحدة فقط لكل طلب."
        } else {
            "تم إيقاف استخدام الشريحة الثانية. لن يرسل التطبيق منها أي رسالة."
        }
    }

    private fun startRelay() {
        if (!isConnected || tokenStore.readProfiles().isEmpty()) {
            statusMessage = "اربط متجرك أولاً قبل تفعيل إرسال الرسائل."
            return
        }
        if (simChoices.isEmpty() || selectedSimId < 0) {
            statusMessage = "لم يتم العثور على شريحة جاهزة. أدخل شريحة اتصال وامنح إذن الوصول إليها."
            return
        }
        if (alternateSimsEnabled && (
            secondarySimId < 0 ||
            secondarySimId == selectedSimId ||
            simChoices.none { it.subscriptionId == secondarySimId }
        )) {
            statusMessage = "الشريحة الثانية غير متاحة. اختر شريحتين فعالتين أو أوقف استخدامها."
            return
        }

        preferences.relayEnabled = true
        preferences.relayConnectionState = "connecting"
        preferences.relayStatusMessage = "جارٍ الاتصال بخادم تاجر تراك..."
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()
        relayConnectionState = "connecting"
        statusMessage = preferences.relayStatusMessage
        val serviceIntent = Intent(this, SmsGatewayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        isRelayRunning = true
        statusMessage = "تم تفعيل الإرسال. اترك هذا الهاتف متصلاً بالإنترنت."
    }

    private fun sendTestSms() {
        if (isSendingTestSms) return

        val selectedOperator = simChoices.firstOrNull { it.subscriptionId == selectedSimId }?.operatorCode
        val profiles = tokenStore.readProfiles()

        // Match the relay's legacy compatibility behavior: direct pairing
        // creates an unscoped profile (operatorCode == null), which uses the
        // merchant's selected primary SIM. Prefer an exact carrier profile,
        // but allow that legacy profile when no exact carrier profile exists.
        val testProfile = profiles.firstOrNull { it.operatorCode == selectedOperator }
            ?: profiles.firstOrNull { it.operatorCode == null }
        val token = testProfile?.token
        if (!isConnected || token.isNullOrBlank()) {
            statusMessage = if (selectedOperator == null) {
                "اربط خط الإرسال المطابق للشريحة المختارة قبل إرسال الرسالة التجريبية."
            } else {
                "لا يوجد خط ${selectedOperator} مرتبط بهذه الشريحة. اربط خط الشبكة المطابق أولاً."
            }
            return
        }
        if (!simSetupUnlocked || selectedSimId < 0 || simChoices.none { it.subscriptionId == selectedSimId }) {
            statusMessage = "امنح الأذونات واختر شريحة الإرسال أولاً."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            statusMessage = "إذن إرسال الرسائل غير متاح. فعّله من إعدادات Android ثم أعد المحاولة."
            return
        }

        isSendingTestSms = true
        statusMessage = "جارٍ تجهيز رسالة الاختبار إلى رقم الهاتف المسجل في حسابك..."
        lifecycleScope.launch {
            var testRequestId: String? = null
            try {
                val target = ApiClient().testSmsTarget(token)
                val requestId = UUID.randomUUID().toString()
                testRequestId = requestId
                val result = SmsResultBus.register(requestId)

                val sentIntent = Intent(this@MainActivity, SmsSendResultReceiver::class.java).apply {
                    action = SmsSendResultReceiver.ACTION_SMS_SENT
                    putExtra(SmsSendResultReceiver.EXTRA_JOB_ID, requestId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    this@MainActivity,
                    requestId.hashCode(),
                    sentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val manager = if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(SmsManager::class.java).createForSubscriptionId(selectedSimId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(selectedSimId)
                }

                manager.sendTextMessage(target.phone, null, target.message, pendingIntent, null)
                val outcome = withTimeoutOrNull(TEST_SMS_RESULT_TIMEOUT_MS) { result.await() }

                statusMessage = when {
                    outcome == null ->
                        "لم يؤكد Android نتيجة رسالة الاختبار إلى ${target.phone}. لا تضغط إرسال مجدداً قبل التحقق من وصولها."
                    outcome.status == "submitted" ->
                        "أرسل Android رسالة الاختبار إلى ${target.phone}. تحقق من وصولها؛ تأكيد Android يعني قبولها للإرسال عبر الشبكة ولا يضمن التسليم."
                    else ->
                        "تعذر إرسال رسالة الاختبار إلى ${target.phone}. ${outcome.error.orEmpty()}"
                }
            } catch (error: SecurityException) {
                statusMessage = "لا يملك التطبيق إذن إرسال SMS حالياً. راجع أذونات التطبيق في Android."
            } catch (error: Exception) {
                statusMessage = ApiClient.describeFailure(error)
            } finally {
                testRequestId?.let { SmsResultBus.remove(it) }
                isSendingTestSms = false
            }
        }
    }

    private fun checkForUpdates() {
        if (isCheckingForUpdates) return

        isCheckingForUpdates = true
        availableUpdate = null
        updateMessage = ""
        lifecycleScope.launch {
            try {
                when (val result = AppUpdateChecker().checkForUpdates(BuildConfig.VERSION_NAME)) {
                    AppUpdateCheckResult.UpToDate -> {
                        updateMessage = "تطبيقك محدّث بالفعل · الإصدار ${BuildConfig.VERSION_NAME}"
                    }
                    is AppUpdateCheckResult.Available -> {
                        availableUpdate = result.update
                        updateMessage = "يتوفر تحديث جديد للتطبيق."
                    }
                }
            } catch (error: AppUpdateCheckException) {
                updateMessage = error.message ?: "تعذر التحقق من التحديثات. حاول مجدداً."
            } catch (_: Exception) {
                updateMessage = "تعذر التحقق من التحديثات. تحقق من اتصال الإنترنت وحاول مجدداً."
            } finally {
                isCheckingForUpdates = false
            }
        }
    }

    private fun openUpdateDownload() {
        val downloadUrl = availableUpdate?.downloadUrl ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
        }.onFailure {
            updateMessage = "تعذر فتح رابط التنزيل. افتح متصفحاً وتحقق من اتصال الإنترنت."
        }
    }

    private fun stopRelay() {
        stopService(Intent(this, SmsGatewayService::class.java).setAction(SmsGatewayService.ACTION_STOP))
        preferences.relayEnabled = false
        preferences.relayConnectionState = "stopped"
        preferences.relayStatusMessage = "تم إيقاف إرسال الرسائل من هذا الهاتف."
        preferences.relayStatusUpdatedAt = System.currentTimeMillis()
        isRelayRunning = false
        relayConnectionState = "stopped"
        statusMessage = preferences.relayStatusMessage
    }

    companion object {
        private const val TEST_SMS_RESULT_TIMEOUT_MS = 60_000L
    }
}

data class SimChoice(val subscriptionId: Int, val label: String, val operatorCode: String? = null)
