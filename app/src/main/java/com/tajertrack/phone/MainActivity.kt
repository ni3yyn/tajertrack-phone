package com.tajertrack.phone

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
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
    private var isManager by mutableStateOf(false)
    private var isCreatingInvite by mutableStateOf(false)
    private var invitationUrl by mutableStateOf("")
    private var invitationStatus by mutableStateOf("")
    private var managerSenderDevices by mutableStateOf(emptyList<com.tajertrack.phone.data.ManagerSenderDevice>())
    private var managerDevicesStatus by mutableStateOf("")
    private var isLoadingManagerDevices by mutableStateOf(false)
    private var updatingManagerDeviceId by mutableStateOf("")
    private var managerSmsMessages by mutableStateOf(emptyList<com.tajertrack.phone.data.SmsMessageLogEntry>())
    private var managerSmsMessagesStatus by mutableStateOf("")
    private var isLoadingManagerSmsMessages by mutableStateOf(false)
    private var hasMoreManagerSmsMessages by mutableStateOf(false)
    private var managerSmsMessagesOffset by mutableStateOf(0)
    private var managerSmsMessagesFilter by mutableStateOf("all")
    private var managerSmsSearchQuery by mutableStateOf("")
    private var managerSmsDeviceFilter by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var simChoices by mutableStateOf(emptyList<SimChoice>())
    private var selectedSimId by mutableStateOf(-1)
    private var secondarySimId by mutableStateOf(-1)
    private var alternateSimsEnabled by mutableStateOf(false)
    private var isPairing by mutableStateOf(false)
    private var deviceManagerLaunchRequest by mutableStateOf(0)
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
                    statusMessage = when {
                        simChoices.isEmpty() -> "تم السماح بالأذونات. أدخل شريحة اتصال فعّالة ثم حاول مجدداً."
                        simChoices.size == 1 -> "تم اختيار الشريحة الوحيدة تلقائياً. يمكنك الآن بدء إرسال روابط التتبع."
                        else -> "تم السماح بالأذونات. افتح قائمة الشرائح وفعّل شريحة واحدة أو الشريحتين."
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
                    deviceManagerLaunchRequest = deviceManagerLaunchRequest,
                    managerDevice = isManager,
                    invitationUrl = invitationUrl,
                    invitationStatus = invitationStatus,
                    creatingInvite = isCreatingInvite,
                    senderDevices = managerSenderDevices,
                    managerDevicesStatus = managerDevicesStatus,
                    loadingSenderDevices = isLoadingManagerDevices,
                    updatingManagerDeviceId = updatingManagerDeviceId,
                    smsMessages = managerSmsMessages,
                    smsMessagesStatus = managerSmsMessagesStatus,
                    loadingSmsMessages = isLoadingManagerSmsMessages,
                    hasMoreSmsMessages = hasMoreManagerSmsMessages,
                    smsMessageFilter = managerSmsMessagesFilter,
                    smsSearchQuery = managerSmsSearchQuery,
                    smsDeviceFilter = managerSmsDeviceFilter,
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
                    onToggleSim = ::toggleSim,
                    onReconnect = ::openPairingPage,
                    onExitApp = { finish() },
                    isCheckingForUpdates = isCheckingForUpdates,
                    updateMessage = updateMessage,
                    availableUpdateVersion = availableUpdate?.version,
                    onCheckForUpdates = ::checkForUpdates,
                    onDownloadUpdate = ::openUpdateDownload,
                    onCreateInvite = ::createManagerInvite,
                    onSendInviteBySms = ::sendInviteBySms,
                    onShareInvite = ::shareInvite,
                    onCopyInvite = ::copyInvite,
                    onCopyDownloadLink = ::copyDownloadLink,
                    onCopyInviteMessage = ::copyInviteMessage,
                    onRefreshManagerDevices = ::loadManagerDevices,
                    onUpdateManagerDevice = ::updateManagerDevice,
                    onRefreshManagerSmsMessages = { loadManagerSmsMessages(reset = true) },
                    onLoadMoreManagerSmsMessages = { loadManagerSmsMessages(reset = false) },
                    onFilterManagerSmsMessages = ::filterManagerSmsMessages,
                    onSearchManagerSmsMessages = ::searchManagerSmsMessages,
                    onFilterManagerSmsDevice = ::filterManagerSmsDevice
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
        if (tokenStore.hasManagerCredential()) {
            loadManagerDevices()
            loadManagerSmsMessages(reset = true)
        }
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
                isManager = tokenStore.hasManagerCredential()
                storeName = tokenStore.storeName()
                isRelayRunning = preferences.relayEnabled && SmsGatewayService.isRunning
                relayConnectionState = if (preferences.relayEnabled && !SmsGatewayService.isRunning) {
                    "needs_user_start"
                } else {
                    preferences.relayConnectionState
                }
                if (preferences.relayEnabled && !SmsGatewayService.isRunning) {
                    statusMessage = "الإرسال محفوظ كمفعّل، لكن خدمة الهاتف غير نشطة حالياً. افتح إعداد الإرسال واضغط تشغيل لاستعادة الاتصال."
                }
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
        isManager = tokenStore.hasManagerCredential()
        storeName = tokenStore.storeName()
        isRelayRunning = preferences.relayEnabled && SmsGatewayService.isRunning
        relayConnectionState = if (preferences.relayEnabled && !SmsGatewayService.isRunning) {
            "needs_user_start"
        } else {
            preferences.relayConnectionState
        }
        if (preferences.relayEnabled && !SmsGatewayService.isRunning) {
            statusMessage = "الإرسال محفوظ كمفعّل، لكن خدمة الهاتف غير نشطة حالياً. افتح إعداد الإرسال واضغط تشغيل لاستعادة الاتصال."
        }
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
        val redirectUri = Uri.encode("tajertrack-sms://manager-pair")
        val url = "${BuildConfig.TAJERTRACK_BASE_URL}/mobile/sms-gateway/connect?redirect_uri=$redirectUri"
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            statusMessage = "تعذر فتح صفحة تاجر تراك. تحقق من اتصال الإنترنت."
        }
    }

    private fun handlePairingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "tajertrack-sms") return
        if (uri.host == "device-manager") {
            deviceManagerLaunchRequest += 1
            if (tokenStore.hasManagerCredential()) {
                loadManagerDevices()
                loadManagerSmsMessages(reset = true)
            }
            return
        }
        if (uri.host !in setOf("pair", "manager-pair")) return

        val managerMode = uri.host == "manager-pair"
        val code = uri.getQueryParameter("code")?.trim().orEmpty()
        if (code.isBlank()) {
            statusMessage = "رابط الربط غير مكتمل. افتح صفحة الربط من جديد."
            return
        }

        isPairing = true
        statusMessage = if (managerMode) {
            "جارٍ اعتماد هذا الهاتف كهاتف رئيسي..."
        } else {
            "جارٍ ربط الهاتف كجهاز إرسال..."
        }
        lifecycleScope.launch {
            try {
                val deviceName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
                    .joinToString(" ")
                    .trim()
                    .take(80)
                    .ifBlank { "Android phone" }

                if (managerMode) {
                    val result = ApiClient().completeManagerPairing(code, deviceName)
                    val saved = tokenStore.saveManagerPairing(
                        managerDeviceId = result.managerDeviceId,
                        managerToken = result.managerToken,
                        profile = com.tajertrack.phone.data.DeviceCredential(
                            deviceId = result.senderDeviceId,
                            token = result.senderToken,
                            storeName = result.storeName,
                            operatorCode = result.operatorCode
                        )
                    )
                    if (!saved) {
                        throw IllegalStateException("تعذر حفظ صلاحيات الهاتف الرئيسي وخط الإرسال بأمان.")
                    }
                    isManager = true
                    isConnected = true
                    storeName = result.storeName
                    loadManagerDevices()
                    loadManagerSmsMessages(reset = true)
                    statusMessage = "أصبح هذا الهاتف الرئيسي لمتجرك، ويمكنه إرسال الرسائل وإنشاء دعوات للهواتف الأخرى."
                } else {
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
                    if (tokenStore.hasManagerCredential()) loadManagerDevices()
                    statusMessage = "تم ربط الهاتف كجهاز إرسال فقط. لا يملك صلاحية إدارة الدعوات أو حساب المتجر."
                }
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
            statusMessage = when {
                simChoices.isEmpty() -> "تم السماح بالأذونات. أدخل شريحة اتصال فعّالة ثم حاول مجدداً."
                simChoices.size == 1 -> "تم اختيار الشريحة الوحيدة تلقائياً. يمكنك الآن بدء إرسال روابط التتبع."
                else -> "تم السماح بالأذونات. افتح قائمة الشرائح وفعّل شريحة واحدة أو الشريحتين."
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
            SimChoice(subscription.subscriptionId, "$operatorLabel — SIM $slot", operatorCode)
        }

        if (simChoices.size == 1) {
            // A single-SIM phone should work immediately without making the
            // merchant choose the only available option.
            val onlySimId = simChoices.first().subscriptionId
            selectedSimId = onlySimId
            secondarySimId = -1
            alternateSimsEnabled = false
            preferences.selectedSubscriptionId = onlySimId
            preferences.secondarySubscriptionId = -1
            preferences.alternateSimsEnabled = false
            return
        }

        val secondaryStillAvailable = alternateSimsEnabled &&
            secondarySimId >= 0 &&
            secondarySimId != selectedSimId &&
            simChoices.any { it.subscriptionId == secondarySimId }

        if (simChoices.none { it.subscriptionId == selectedSimId }) {
            // If the primary SIM disappeared but the previously enabled
            // secondary SIM remains, promote it instead of disabling sending.
            selectedSimId = if (secondaryStillAvailable) secondarySimId else -1
            preferences.selectedSubscriptionId = selectedSimId
            if (secondaryStillAvailable) {
                secondarySimId = -1
                alternateSimsEnabled = false
                preferences.secondarySubscriptionId = -1
                preferences.alternateSimsEnabled = false
            }
        }

        if (!secondaryStillAvailable || secondarySimId == selectedSimId) {
            val hadSecondaryEnabled = alternateSimsEnabled
            secondarySimId = -1
            alternateSimsEnabled = false
            preferences.secondarySubscriptionId = -1
            preferences.alternateSimsEnabled = false
            if (hadSecondaryEnabled) {
                statusMessage = "تغيّرت الشرائح المتاحة. تم تحديث الشرائح المفعّلة؛ راجع اختيارك قبل تشغيل الإرسال."
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

    private fun toggleSim(subscriptionId: Int) {
        if (simChoices.none { it.subscriptionId == subscriptionId }) return

        val isPrimaryEnabled = selectedSimId == subscriptionId
        val isSecondaryEnabled = alternateSimsEnabled && secondarySimId == subscriptionId

        when {
            isPrimaryEnabled -> {
                if (isSecondaryEnabled) return
                if (alternateSimsEnabled &&
                    secondarySimId >= 0 &&
                    simChoices.any { it.subscriptionId == secondarySimId }
                ) {
                    // Keep the other enabled SIM active and promote it to primary.
                    selectedSimId = secondarySimId
                    preferences.selectedSubscriptionId = secondarySimId
                    secondarySimId = -1
                    preferences.secondarySubscriptionId = -1
                    alternateSimsEnabled = false
                    preferences.alternateSimsEnabled = false
                    statusMessage = "تم إيقاف الشريحة المحددة. تستمر الخدمة عبر الشريحة الأخرى المفعّلة."
                } else {
                    selectedSimId = -1
                    preferences.selectedSubscriptionId = -1
                    statusMessage = "تم إيقاف جميع شرائح الإرسال. اختر شريحة واحدة على الأقل للمتابعة."
                }
            }

            isSecondaryEnabled -> {
                secondarySimId = -1
                preferences.secondarySubscriptionId = -1
                alternateSimsEnabled = false
                preferences.alternateSimsEnabled = false
                statusMessage = "تم إيقاف الشريحة الثانية. سيستخدم التطبيق الشريحة الأساسية المفعّلة فقط."
            }

            selectedSimId < 0 -> {
                selectedSimId = subscriptionId
                preferences.selectedSubscriptionId = subscriptionId
                statusMessage = "تم تفعيل هذه الشريحة للإرسال."
            }

            !alternateSimsEnabled && simChoices.size >= 2 -> {
                secondarySimId = subscriptionId
                preferences.secondarySubscriptionId = subscriptionId
                alternateSimsEnabled = true
                preferences.alternateSimsEnabled = true
                statusMessage = "تم تفعيل الشريحتين. سيختار التطبيق شبكة العميل ويرسل رسالة واحدة فقط من الشريحة المطابقة."
            }

            else -> {
                // Only two SIM subscriptions are supported as active sender
                // lines. Do not silently replace an already enabled line.
                statusMessage = "لا يمكن تفعيل شريحة ثالثة على هذا الهاتف."
            }
        }
    }

    private fun startRelay() {
        if (!isConnected || tokenStore.readProfiles().isEmpty()) {
            statusMessage = "اربط متجرك أولاً قبل تفعيل إرسال الرسائل."
            return
        }

        val missingPermissions = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.SEND_SMS)
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.READ_PHONE_STATE)
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            pendingStartAfterPermissions = true
            statusMessage = "يلزم إعادة منح أذونات SMS وشرائح الاتصال قبل حجز أي طلب جديد."
            permissionLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        refreshSimChoices()
        if (simChoices.isEmpty() || selectedSimId < 0) {
            statusMessage = "لم يتم العثور على شريحة جاهزة. أدخل شريحة اتصال فعّالة واخترها أولاً."
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
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            isRelayRunning = true
            statusMessage = "بدأ تشغيل الخدمة. تحقق من حالة الاتصال قبل مغادرة التطبيق."
        } catch (error: SecurityException) {
            preferences.relayEnabled = false
            isRelayRunning = false
            relayConnectionState = "attention"
            statusMessage = "منع Android تشغيل خدمة الإرسال بسبب أذونات أو إعدادات النظام. راجع أذونات التطبيق ثم حاول مجدداً."
        } catch (error: IllegalStateException) {
            preferences.relayEnabled = false
            isRelayRunning = false
            relayConnectionState = "needs_user_start"
            statusMessage = "لم يسمح Android ببدء الخدمة في الخلفية. افتح التطبيق من جديد واضغط تشغيل الإرسال."
        }
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

    private fun createManagerInvite(operatorCode: String) {
        if (isCreatingInvite) return
        val managerToken = tokenStore.managerToken()
        if (managerToken.isNullOrBlank() || !tokenStore.hasManagerCredential()) {
            invitationStatus = "هذا الهاتف لا يملك صلاحية إدارة الأجهزة. أعد ربطه من حساب التاجر."
            return
        }

        isCreatingInvite = true
        invitationStatus = "جارٍ إنشاء رابط الدعوة..."
        lifecycleScope.launch {
            try {
                val result = ApiClient().createManagerInvite(managerToken, operatorCode)
                invitationUrl = result.inviteUrl
                invitationStatus = "تم إنشاء دعوة ${operatorLabelArabic(result.operatorCode)}. تنتهي بعد 30 دقيقة وتُستخدم مرة واحدة."
                statusMessage = invitationStatus
            } catch (error: Exception) {
                invitationStatus = ApiClient.describeFailure(error)
            } finally {
                isCreatingInvite = false
            }
        }
    }

    private fun loadManagerDevices() {
        val managerToken = tokenStore.managerToken()
        if (managerToken.isNullOrBlank()) {
            managerSenderDevices = emptyList()
            managerDevicesStatus = "هذا الهاتف ليس هاتفاً رئيسياً."
            return
        }
        if (isLoadingManagerDevices) return

        isLoadingManagerDevices = true
        lifecycleScope.launch {
            try {
                managerSenderDevices = ApiClient().listManagerDevices(managerToken)
                managerDevicesStatus = if (managerSenderDevices.isEmpty()) {
                    "لم يتم ربط هواتف إرسال إضافية بعد."
                } else {
                    "تظهر هنا هواتف الإرسال المرتبطة بهذا المتجر."
                }
            } catch (error: Exception) {
                managerDevicesStatus = ApiClient.describeFailure(error)
            } finally {
                isLoadingManagerDevices = false
            }
        }
    }

    private fun filterManagerSmsMessages(filter: String) {
        if (filter !in setOf("all", "pending", "submitted", "failed", "unknown")) return
        if (isLoadingManagerSmsMessages || managerSmsMessagesFilter == filter) return

        managerSmsMessagesFilter = filter
        resetManagerSmsMessages()
        loadManagerSmsMessages(reset = true)
    }

    private fun searchManagerSmsMessages(query: String) {
        if (isLoadingManagerSmsMessages) return
        val safeQuery = query.trim().take(80)
        if (managerSmsSearchQuery == safeQuery) {
            loadManagerSmsMessages(reset = true)
            return
        }
        managerSmsSearchQuery = safeQuery
        resetManagerSmsMessages()
        loadManagerSmsMessages(reset = true)
    }

    private fun filterManagerSmsDevice(deviceId: String) {
        if (isLoadingManagerSmsMessages) return
        val safeDeviceId = deviceId.takeIf { it.isBlank() || it.matches(Regex("^[A-Za-z0-9_-]{8,100}$")) }.orEmpty()
        if (managerSmsDeviceFilter == safeDeviceId) return
        managerSmsDeviceFilter = safeDeviceId
        resetManagerSmsMessages()
        loadManagerSmsMessages(reset = true)
    }

    private fun resetManagerSmsMessages() {
        managerSmsMessages = emptyList()
        managerSmsMessagesOffset = 0
        hasMoreManagerSmsMessages = false
    }

    private fun loadManagerSmsMessages(
        reset: Boolean = true,
        statusFilter: String = managerSmsMessagesFilter
    ) {
        val managerToken = tokenStore.managerToken()
        if (managerToken.isNullOrBlank() || !tokenStore.hasManagerCredential()) {
            managerSmsMessages = emptyList()
            managerSmsMessagesStatus = "سجل الرسائل متاح من الهاتف الرئيسي فقط."
            hasMoreManagerSmsMessages = false
            managerSmsMessagesOffset = 0
            return
        }
        if (isLoadingManagerSmsMessages) return
        if (!reset && !hasMoreManagerSmsMessages) return

        val requestedOffset = if (reset) 0 else managerSmsMessagesOffset
        isLoadingManagerSmsMessages = true
        if (reset) managerSmsMessagesStatus = "جارٍ تحميل سجل الرسائل..."
        lifecycleScope.launch {
            try {
                val page = ApiClient().listManagerSmsMessages(
                    managerToken = managerToken,
                    offset = requestedOffset,
                    statusFilter = statusFilter,
                    searchQuery = managerSmsSearchQuery,
                    deviceId = managerSmsDeviceFilter
                )
                managerSmsMessages = if (reset) {
                    page.messages
                } else {
                    (managerSmsMessages + page.messages).distinctBy { it.id }
                }
                managerSmsMessagesOffset = page.nextOffset
                hasMoreManagerSmsMessages = page.hasMore
                managerSmsMessagesStatus = if (managerSmsMessages.isEmpty()) {
                    "لا توجد رسائل ضمن هذا التصنيف."
                } else {
                    ""
                }
            } catch (error: Exception) {
                managerSmsMessagesStatus = ApiClient.describeFailure(error)
            } finally {
                isLoadingManagerSmsMessages = false
            }
        }
    }

    private fun updateManagerDevice(deviceId: String, action: String) {
        if (updatingManagerDeviceId.isNotBlank()) return
        val managerToken = tokenStore.managerToken()
        if (managerToken.isNullOrBlank()) {
            managerDevicesStatus = "صلاحية إدارة الأجهزة غير متاحة. أعد ربط الهاتف الرئيسي."
            return
        }

        updatingManagerDeviceId = deviceId
        lifecycleScope.launch {
            try {
                ApiClient().updateManagerDevice(managerToken, deviceId, action)
                managerDevicesStatus = when (action) {
                    "pause" -> "تم إيقاف خط الإرسال."
                    "resume" -> "تم استئناف خط الإرسال."
                    "revoke" -> "تم إلغاء ربط الهاتف."
                    else -> "تم تحديث الجهاز."
                }
                managerSenderDevices = ApiClient().listManagerDevices(managerToken)
            } catch (error: Exception) {
                managerDevicesStatus = ApiClient.describeFailure(error)
            } finally {
                updatingManagerDeviceId = ""
            }
        }
    }

    private fun operatorLabelArabic(operatorCode: String): String = when (operatorCode) {
        "djezzy" -> "Djezzy"
        "mobilis" -> "Mobilis"
        "ooredoo" -> "Ooredoo"
        else -> "الشبكة المحددة"
    }

    private fun invitationMessage(): String {
        val url = invitationUrl
        if (url.isBlank()) return ""
        return "مرحباً! نحتاج إلى ربط هاتفك بخدمة إرسال رسائل تتبع الطلبات لمتجر ${storeName.ifBlank { "تاجر تراك" }}.\n\n" +
            "1. حمّل تطبيق TajerTrack Phone: $APP_DOWNLOAD_URL\n\n" +
            "2. افتح رابط دعوة الربط: $url\n\n" +
            "اتبع التعليمات لإكمال الربط. صلاحية الدعوة 30 دقيقة وتُستخدم مرة واحدة."
    }

    private fun sendInviteBySms() {
        val message = invitationMessage()
        if (message.isBlank()) {
            invitationStatus = "أنشئ رابط الدعوة أولاً."
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:")
                putExtra("sms_body", message)
            }
            startActivity(Intent.createChooser(intent, "إرسال دعوة ربط الهاتف"))
        }.onFailure {
            invitationStatus = "تعذر فتح تطبيق الرسائل على هذا الهاتف."
        }
    }

    private fun shareInvite() {
        val message = invitationMessage()
        if (message.isBlank()) {
            invitationStatus = "أنشئ رابط الدعوة أولاً."
            return
        }
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "دعوة ربط هاتف TajerTrack")
                putExtra(Intent.EXTRA_TEXT, message)
            }
            startActivity(Intent.createChooser(intent, "مشاركة دعوة ربط الهاتف"))
        }.onFailure {
            invitationStatus = "تعذرت مشاركة الدعوة من هذا الهاتف."
        }
    }

    private fun copyText(label: String, value: String) {
        if (value.isBlank()) {
            invitationStatus = "أنشئ رابط الدعوة أولاً."
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        invitationStatus = "تم نسخ $label."
    }

    private fun copyInvite() = copyText("رابط الدعوة", invitationUrl)

    private fun copyDownloadLink() = copyText("رابط تحميل التطبيق", APP_DOWNLOAD_URL)

    private fun copyInviteMessage() = copyText("رسالة الدعوة كاملة", invitationMessage())

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
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            startActivity(browserIntent)
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
        private const val APP_DOWNLOAD_URL = "https://github.com/ni3yyn/tajertrack-phone/releases/latest/download/tajertrack-phone.apk"
    }
}

data class SimChoice(val subscriptionId: Int, val label: String, val operatorCode: String? = null)
