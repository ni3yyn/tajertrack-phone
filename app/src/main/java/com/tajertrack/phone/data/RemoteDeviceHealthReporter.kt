package com.tajertrack.phone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.tajertrack.phone.BuildConfig
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Uses each sender line's own bearer credential; device identity is server-derived. */
object RemoteDeviceHealthReporter {
    private const val REPORT_INTERVAL_MS = 60_000L
    private val lastAttemptByDevice = ConcurrentHashMap<String, Long>()

    suspend fun reportIfDue(
        context: Context,
        preferences: AppPreferences,
        api: ApiClient,
        profile: DeviceCredential,
        relayRunning: Boolean,
        force: Boolean = false,
        refreshRequestedAt: String? = null
    ) {
        val now = System.currentTimeMillis()
        val previous = lastAttemptByDevice[profile.deviceId] ?: 0L
        if (!force && refreshRequestedAt.isNullOrBlank() && now - previous < REPORT_INTERVAL_MS) return
        lastAttemptByDevice[profile.deviceId] = now
        try {
            api.reportDeviceHealth(
                profile.token,
                DeviceHealthSnapshot.capture(context, preferences, relayRunning),
                refreshRequestedAt
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Could not report sender device health for ${profile.deviceId}.", error)
        }
    }

    private const val TAG = "TajerTrackHealth"
}

object DeviceHealthSnapshot {
    fun capture(context: Context, preferences: AppPreferences, relayRunning: Boolean): JSONObject {
        val sendSmsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val phoneStateGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val notificationsRequired = Build.VERSION.SDK_INT >= 33
        val notificationsGranted = !notificationsRequired ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val activeSubscriptions = if (phoneStateGranted) runCatching {
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
        }.getOrNull() else null
        val enabledIds = preferences.enabledSubscriptionIds()
        val activeIds = activeSubscriptions?.map { it.subscriptionId }?.toSet()
        val missingEnabledCount = activeIds?.let { active -> enabledIds.count { it !in active } }
        val internetValidated = runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val network = connectivity.activeNetwork ?: return@runCatching false
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
        val relayState = when {
            !preferences.relayEnabled -> "stopped"
            !relayRunning -> "needs_user_start"
            else -> preferences.relayConnectionState
        }.takeIf {
            it in setOf("connecting", "connected", "sending", "reporting", "retrying", "offline",
                "paused", "needs_repair", "needs_pairing", "needs_user_start", "disabled", "attention", "stopped")
        } ?: "attention"
        val model = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }
            .joinToString(" ").take(80).ifBlank { "Android phone" }

        return JSONObject()
            .put("reportVersion", 1)
            .put("device", JSONObject()
                .put("appVersion", BuildConfig.VERSION_NAME)
                .put("model", model)
                .put("androidRelease", Build.VERSION.RELEASE.orEmpty().take(24).ifBlank { "unknown" })
                .put("apiLevel", Build.VERSION.SDK_INT))
            .put("connectivity", JSONObject().put("internetValidated", internetValidated))
            .put("permissions", JSONObject()
                .put("sendSmsGranted", sendSmsGranted)
                .put("phoneStateGranted", phoneStateGranted)
                .put("notificationsGranted", notificationsGranted)
                .put("notificationsRequired", notificationsRequired))
            .put("relay", JSONObject()
                .put("enabled", preferences.relayEnabled)
                .put("serviceRunning", relayRunning)
                .put("connectionState", relayState)
                .put("diagnosticMessage", preferences.relayStatusMessage.take(280))
                .put("statusUpdatedAt", preferences.relayStatusUpdatedAt)
                .put("lastSuccessfulPollAt", preferences.relayLastSuccessfulPollAt)
                .put("consecutiveFailures", preferences.consecutiveRelayFailures.coerceIn(0, 10_000)))
            .put("sims", JSONObject()
                .put("activeCount", activeSubscriptions?.size ?: JSONObject.NULL)
                .put("enabledCount", enabledIds.size.coerceIn(0, 2))
                .put("missingEnabledCount", missingEnabledCount ?: JSONObject.NULL)
                .put("alternateSimsEnabled", preferences.alternateSimsEnabled))
    }
}
