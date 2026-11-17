package com.tajertrack.phone.data

import com.tajertrack.phone.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * The app talks only to TajerTrack's merchant-scoped mobile gateway API.
 * It must never call the legacy global-token / Termux endpoints.
 */
class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.TAJERTRACK_BASE_URL.trimEnd('/')

    suspend fun completePairing(code: String, deviceName: String): PairingResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", code)
            .put("deviceName", deviceName)
            .put("platform", "android")
            .put("appVersion", BuildConfig.VERSION_NAME)
        val response = post("/api/mobile/sms-gateway/pair/complete", body, token = null)
        val deviceToken = response.optString("deviceToken")
        val deviceId = response.optString("deviceId")
        if (deviceToken.isBlank() || deviceId.isBlank()) {
            throw GatewayProtocolException("The pairing response did not include a device credential.")
        }
        PairingResult(
            deviceToken = deviceToken,
            deviceId = deviceId,
            storeName = response.optString("storeName").ifBlank { "متجرك" },
            operatorCode = response.optString("operatorCode").takeIf { it in setOf("djezzy", "mobilis", "ooredoo") }
        )
    }

    suspend fun completeManagerPairing(code: String, deviceName: String): ManagerPairingResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", code)
            .put("deviceName", deviceName)
            .put("platform", "android")
            .put("appVersion", BuildConfig.VERSION_NAME)
        val response = post("/api/mobile/sms-gateway/manager/pair/complete", body, token = null)
        val managerToken = response.optString("managerToken")
        val managerDeviceId = response.optString("managerDeviceId")
        val deviceToken = response.optString("deviceToken")
        val deviceId = response.optString("senderDeviceId")
        val operatorCode = response.optString("operatorCode")
            .takeIf { it in setOf("djezzy", "mobilis", "ooredoo") }
        if (managerToken.isBlank() || managerDeviceId.isBlank() ||
            deviceToken.isBlank() || deviceId.isBlank()
        ) {
            throw GatewayProtocolException("The manager pairing response did not include both device credentials.")
        }
        ManagerPairingResult(
            managerToken = managerToken,
            managerDeviceId = managerDeviceId,
            senderToken = deviceToken,
            senderDeviceId = deviceId,
            storeName = response.optString("storeName").ifBlank { "متجرك" },
            operatorCode = operatorCode
        )
    }

    suspend fun createManagerInvite(managerToken: String, operatorCode: String): ManagerInviteResult =
        withContext(Dispatchers.IO) {
            if (operatorCode !in setOf("djezzy", "mobilis", "ooredoo")) {
                throw GatewayProtocolException("Invalid sender network.")
            }
            val response = post(
                "/api/mobile/sms-gateway/manager/invites",
                JSONObject().put("operatorCode", operatorCode),
                managerToken
            )
            val inviteUrl = response.optString("inviteUrl")
            if (inviteUrl.isBlank() || !inviteUrl.startsWith("https://")) {
                throw GatewayProtocolException("TajerTrack did not return a valid invitation link.")
            }
            ManagerInviteResult(
                inviteUrl = inviteUrl,
                expiresInSeconds = response.optLong("expiresInSeconds", 1800L),
                operatorCode = response.optString("operatorCode")
            )
        }

    suspend fun listManagerDevices(managerToken: String): List<ManagerSenderDevice> =
        withContext(Dispatchers.IO) {
            val response = get("/api/mobile/sms-gateway/manager/devices", managerToken)
            val items = response.optJSONArray("devices") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val deviceId = item.optString("deviceId")
                    if (deviceId.isBlank()) continue
                    add(
                        ManagerSenderDevice(
                            deviceId = deviceId,
                            isPrimary = item.optBoolean("isPrimary"),
                            displayName = item.optString("displayName").ifBlank { "Android phone" },
                            operatorCode = item.optString("operatorCode").takeIf { it in setOf("djezzy", "mobilis", "ooredoo") },
                            enabled = item.optBoolean("enabled"),
                            revoked = item.optBoolean("revoked"),
                            isOnline = item.optBoolean("isOnline"),
                            connectedAt = item.optString("connectedAt"),
                            lastSeenAt = item.optString("lastSeenAt")
                        )
                    )
                }
            }
        }

    suspend fun updateManagerDevice(
        managerToken: String,
        deviceId: String,
        action: String
    ) = withContext(Dispatchers.IO) {
        if (action !in setOf("pause", "resume", "revoke")) {
            throw GatewayProtocolException("Invalid sender device action.")
        }
        val response = post(
            "/api/mobile/sms-gateway/manager/devices",
            JSONObject()
                .put("deviceId", deviceId)
                .put("action", action),
            managerToken
        )
        if (!response.optBoolean("success")) {
            throw GatewayProtocolException("TajerTrack did not confirm the sender device update.")
        }
    }

    suspend fun claim(token: String, operatorCode: String?): ClaimResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("dispatchProtocol", 1)
        if (!operatorCode.isNullOrBlank()) payload.put("operatorCode", operatorCode)

        val response = post(
            "/api/mobile/sms-gateway/device/claim",
            payload,
            token
        )
        val job = response.optJSONObject("job")
        ClaimResult(
            job = job?.let {
                val claimToken = it.optString("claimToken")
                    .takeIf(String::isNotBlank)
                    ?: throw GatewayProtocolException("The SMS claim did not include its recovery token.")
                SmsJob(
                    id = it.optString("id"),
                    claimToken = claimToken,
                    phone = it.optString("phone"),
                    message = it.optString("message")
                )
            },
            deviceDisabled = response.optBoolean("deviceDisabled"),
            gatewayPaused = response.optBoolean("gatewayPaused"),
            merchantDisabled = response.optBoolean("merchantDisabled"),
            operatorConflict = response.optBoolean("operatorConflict"),
            operatorUnassigned = response.optBoolean("operatorUnassigned")
        )
    }

    suspend fun beginDispatch(token: String, jobId: String, claimToken: String) = withContext(Dispatchers.IO) {
        val response = post(
            "/api/mobile/sms-gateway/device/dispatch",
            JSONObject()
                .put("jobId", jobId)
                .put("claimToken", claimToken),
            token
        )
        if (!response.optBoolean("success")) {
            throw GatewayProtocolException("TajerTrack did not authorize SMS dispatch.")
        }
    }

    suspend fun testSmsTarget(token: String): SmsTestTarget = withContext(Dispatchers.IO) {
        val response = post(
            "/api/mobile/sms-gateway/device/test-target",
            JSONObject(),
            token
        )
        val phone = response.optString("phone")
        val message = response.optString("message")
        if (phone.isBlank() || message.isBlank()) {
            throw GatewayProtocolException("TajerTrack did not return a valid registered merchant phone.")
        }
        SmsTestTarget(phone = phone, message = message)
    }

    suspend fun report(
        token: String,
        jobId: String,
        claimToken: String?,
        status: String,
        error: String? = null,
        retryable: Boolean = false,
        failureCode: String? = null
    ) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("jobId", jobId)
            .put("status", status)
            .put("retryable", retryable)
        if (!claimToken.isNullOrBlank()) body.put("claimToken", claimToken)
        if (!error.isNullOrBlank()) body.put("error", error.take(500))
        if (!failureCode.isNullOrBlank()) body.put("failureCode", failureCode.take(80))

        val response = post("/api/mobile/sms-gateway/device/report", body, token)
        if (!response.optBoolean("success")) {
            throw GatewayProtocolException("TajerTrack did not confirm the SMS result.")
        }
    }

    private fun get(path: String, token: String): JSONObject {
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept", "application/json")
            .get()
        if (token.isNotBlank()) requestBuilder.header("Authorization", "Bearer $token")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val serverMessage = parsed?.optString("error")?.takeIf { it.isNotBlank() }
                throw GatewayHttpException(
                    statusCode = response.code,
                    message = serverMessage ?: "TajerTrack returned HTTP " + response.code + ".",
                    retryAfterMs = response.header("Retry-After")
                        ?.toLongOrNull()
                        ?.times(1_000L)
                        ?.coerceIn(1_000L, 300_000L),
                    serverErrorCode = parsed?.optString("errorCode")?.takeIf { it.isNotBlank() }
                )
            }
            return parsed ?: throw GatewayProtocolException("TajerTrack returned an invalid response.")
        }
    }

    private fun post(path: String, payload: JSONObject, token: String?): JSONObject {
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
        if (!token.isNullOrBlank()) requestBuilder.header("Authorization", "Bearer $token")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { JSONObject(raw) }.getOrNull()
            if (!response.isSuccessful) {
                val serverMessage = parsed?.optString("error")?.takeIf { it.isNotBlank() }
                val retryAfterMs = response.header("Retry-After")
                    ?.toLongOrNull()
                    ?.times(1_000L)
                    ?.coerceIn(1_000L, 300_000L)
                throw GatewayHttpException(
                    statusCode = response.code,
                    message = serverMessage ?: "TajerTrack returned HTTP " + response.code + ".",
                    retryAfterMs = retryAfterMs,
                    serverErrorCode = parsed?.optString("errorCode")?.takeIf { it.isNotBlank() }
                )
            }
            return parsed ?: throw GatewayProtocolException("TajerTrack returned an invalid response.")
        }
    }

    companion object {
        fun describeFailure(error: Throwable): String {
            val causes = generateSequence(error) { it.cause }.take(8).toList()

            if (error is GatewayHttpException) {
                val rawServerMessage = error.message.orEmpty()
                val serverMessage = rawServerMessage
                    .takeIf { it.isNotBlank() && !it.startsWith("TajerTrack returned HTTP ") }
                if (serverMessage != null) return serverMessage

                return when (error.statusCode) {
                    401, 403 -> "انتهت صلاحية ربط هذا الهاتف أو تم إيقافه. أعد ربطه من التطبيق."
                    404 -> "مسار خدمة الهاتف غير متاح على الخادم. حدّث التطبيق أو تواصل مع دعم تاجر تراك."
                    408, 425 -> "تأخر رد خادم تاجر تراك. سيعيد التطبيق المحاولة تلقائياً."
                    429 -> "طلبات الاتصال كثيرة مؤقتاً. سيخفّض التطبيق عدد المحاولات تلقائياً."
                    in 500..599 -> "خادم تاجر تراك يواجه مشكلة مؤقتة (HTTP " + error.statusCode + "). ستتم إعادة المحاولة."
                    in 400..499 -> "رفض خادم تاجر تراك الطلب (HTTP " + error.statusCode + "). راجع الربط وحدّث التطبيق عند الحاجة."
                    else -> "تعذّر إكمال طلب الاتصال (HTTP " + error.statusCode + "). ستتم إعادة المحاولة."
                }
            }

            return when {
                causes.any { it is UnknownHostException } ->
                    "تعذّر العثور على عنوان خادم تاجر تراك. تحقق من الإنترنت أو إعدادات DNS."
                causes.any { it is SocketTimeoutException } ->
                    "استغرق خادم تاجر تراك وقتاً أطول من المعتاد للرد. ستتم إعادة المحاولة."
                causes.any { it is ConnectException } ->
                    "تعذّر إنشاء اتصال بخادم تاجر تراك. تحقق من الشبكة وحاول مجدداً."
                causes.any { it is SSLException } ->
                    "تعذّر إنشاء اتصال آمن بخادم تاجر تراك. تحقق من تاريخ الهاتف وإعدادات الشبكة."
                error is GatewayProtocolException ->
                    "وصل رد غير مكتمل من خادم تاجر تراك. سيعيد التطبيق المحاولة."
                causes.any { it is IOException } ->
                    "انقطع الاتصال أثناء تبادل البيانات مع تاجر تراك. سيعيد التطبيق المحاولة."
                else ->
                    "حدث خطأ غير متوقع أثناء الاتصال بتاجر تراك. سيعيد التطبيق المحاولة."
            }
        }

        fun suggestedRetryDelayMs(error: Throwable, failureCount: Int): Long {
            if (error is GatewayHttpException && error.retryAfterMs != null) {
                return error.retryAfterMs
            }
            val exponent = (failureCount - 1).coerceIn(0, 5)
            return (10_000L * (1L shl exponent)).coerceAtMost(300_000L)
        }
    }
}

data class PairingResult(
    val deviceToken: String,
    val deviceId: String,
    val storeName: String,
    val operatorCode: String?
)

data class ManagerPairingResult(
    val managerToken: String,
    val managerDeviceId: String,
    val senderToken: String,
    val senderDeviceId: String,
    val storeName: String,
    val operatorCode: String?
)

data class ManagerInviteResult(
    val inviteUrl: String,
    val expiresInSeconds: Long,
    val operatorCode: String
)

data class ManagerSenderDevice(
    val deviceId: String,
    val isPrimary: Boolean,
    val displayName: String,
    val operatorCode: String?,
    val enabled: Boolean,
    val revoked: Boolean,
    val isOnline: Boolean,
    val connectedAt: String,
    val lastSeenAt: String
)



data class SmsJob(
    val id: String,
    val claimToken: String,
    val phone: String,
    val message: String
)

data class SmsTestTarget(
    val phone: String,
    val message: String
)

data class ClaimResult(
    val job: SmsJob?,
    val deviceDisabled: Boolean,
    val gatewayPaused: Boolean,
    val merchantDisabled: Boolean,
    val operatorConflict: Boolean,
    val operatorUnassigned: Boolean
)

class GatewayHttpException(
    val statusCode: Int,
    message: String,
    val retryAfterMs: Long? = null,
    val serverErrorCode: String? = null
) : IOException(message)

class GatewayProtocolException(message: String) : IOException(message)
