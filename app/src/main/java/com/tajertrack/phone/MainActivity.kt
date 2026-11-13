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
import com.tajertrack.phone.data.DeviceTokenStore
import com.tajertrack.phone.service.SmsGatewayService
import com.tajertrack.phone.service.SmsResultBus
import com.tajertrack.phone.service.SmsSendResultReceiver
import com.tajertrack.phone.ui.PhoneHomeScreen
import com.tajertrack.phone.ui.PhoneTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val tokenStore by lazy { DeviceTokenStore(this) }
    private val preferences by lazy { AppPreferences(this) }

    private var isConnected by mutableStateOf(false)
    private var isRelayRunning by mutableStateOf(false)
    private var storeName by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var simChoices by mutableStateOf(emptyList<SimChoice>())
    private var selectedSimId by mutableStateOf(-1)
    private var isPairing by mutableStateOf(false)
    private var simSetupUnlocked by mutableStateOf(false)
    private var isSendingTestSms by mutableStateOf(false)
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
                    storeName = storeName,
                    statusMessage = statusMessage,
                    pairing = isPairing,
                    simSetupUnlocked = simSetupUnlocked,
                    testSmsSending = isSendingTestSms,
                    simChoices = simChoices,
                    selectedSimId = selectedSimId,
                    onConnect = ::openPairingPage,
                    onStartRelay = ::requestPermissionsAndStart,
                    onStopRelay = ::stopRelay,
                    onSendTestSms = ::sendTestSms,
                    onSelectSim = ::selectSim,
                    onReconnect = ::openPairingPage,
                    onExitApp = { finish() }
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
    }

    private fun refreshConnectionState() {
        isConnected = !tokenStore.readToken().isNullOrBlank()
        storeName = tokenStore.storeName()
        isRelayRunning = preferences.relayEnabled
        val permissionsGranted = requiredPermissionsGranted()
        simSetupUnlocked = permissionsGranted && (preferences.smsSetupUnlocked || preferences.relayEnabled)
        if (!permissionsGranted) preferences.smsSetupUnlocked = false
        selectedSimId = preferences.selectedSubscriptionId
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
                tokenStore.saveToken(result.deviceToken)
                tokenStore.saveDevice(result.deviceId, result.storeName)
                isConnected = true
                storeName = result.storeName
                statusMessage = "تم ربط الهاتف بنجاح. انتقل إلى إعداد إرسال الرسائل لتفعيل الأذونات واختيار الشريحة."
            } catch (error: Exception) {
                statusMessage = error.message ?: "تعذر إكمال ربط الهاتف. حاول إنشاء رابط ربط جديد."
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
            SimChoice(subscription.subscriptionId, "$carrier — SIM $slot")
        }

        if (simChoices.none { it.subscriptionId == selectedSimId }) {
            selectedSimId = when {
                simChoices.size == 1 -> simChoices.first().subscriptionId
                else -> -1
            }
            preferences.selectedSubscriptionId = selectedSimId
        }
    }

    private fun selectSim(subscriptionId: Int) {
        selectedSimId = subscriptionId
        preferences.selectedSubscriptionId = subscriptionId
        statusMessage = "تم اختيار الشريحة. يمكنك تفعيل إرسال الرسائل الآن."
    }

    private fun startRelay() {
        if (!isConnected || tokenStore.readToken().isNullOrBlank()) {
            statusMessage = "اربط متجرك أولاً قبل تفعيل إرسال الرسائل."
            return
        }
        if (simChoices.isEmpty() || selectedSimId < 0) {
            statusMessage = "لم يتم العثور على شريحة جاهزة. أدخل شريحة اتصال وامنح إذن الوصول إليها."
            return
        }

        preferences.relayEnabled = true
        val serviceIntent = Intent(this, SmsGatewayService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        isRelayRunning = true
        statusMessage = "تم تفعيل الإرسال. اترك هذا الهاتف متصلاً بالإنترنت."
    }

    private fun sendTestSms() {
        if (isSendingTestSms) return

        val token = tokenStore.readToken()
        if (!isConnected || token.isNullOrBlank()) {
            statusMessage = "اربط متجرك أولاً قبل إرسال الرسالة التجريبية."
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
                statusMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "تعذر إرسال رسالة الاختبار. تحقق من اتصال الإنترنت والشريحة ثم حاول لاحقاً."
            } finally {
                testRequestId?.let { SmsResultBus.remove(it) }
                isSendingTestSms = false
            }
        }
    }

    private fun stopRelay() {
        stopService(Intent(this, SmsGatewayService::class.java).setAction(SmsGatewayService.ACTION_STOP))
        preferences.relayEnabled = false
        isRelayRunning = false
        statusMessage = "تم إيقاف إرسال الرسائل من هذا الهاتف."
    }

    companion object {
        private const val TEST_SMS_RESULT_TIMEOUT_MS = 60_000L
    }
}

data class SimChoice(val subscriptionId: Int, val label: String)
