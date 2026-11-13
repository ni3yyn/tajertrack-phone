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
            storeName = response.optString("storeName").ifBlank { "متجرك" }
        )
    }

    suspend fun claim(token: String): ClaimResult = withContext(Dispatchers.IO) {
        val response = post(
            "/api/mobile/sms-gateway/device/claim",
            JSONObject().put("appVersion", BuildConfig.VERSION_NAME),
            token
        )
        val job = response.optJSONObject("job")
        ClaimResult(
            job = job?.let {
                SmsJob(
                    id = it.optString("id"),
                    phone = it.optString("phone"),
                    message = it.optString("message")
                )
            },
            deviceDisabled = response.optBoolean("deviceDisabled"),
            gatewayPaused = response.optBoolean("gatewayPaused"),
            merchantDisabled = response.optBoolean("merchantDisabled")
        )
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

    suspend fun report(token: String, jobId: String, status: String, error: String? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("jobId", jobId)
            .put("status", status)
        if (!error.isNullOrBlank()) body.put("error", error.take(500))

        val response = post("/api/mobile/sms-gateway/device/report", body, token)
        if (!response.optBoolean("success")) {
            throw GatewayProtocolException("TajerTrack did not confirm the SMS result.")
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
                    retryAfterMs = retryAfterMs
                )
            }
            return parsed ?: throw GatewayProtocolException("TajerTrack returned an invalid response.")
        }
    }

    companion object {
        fun describeFailure(error: Throwable): String {
            val causes = generateSequence(error) { it.cause }.take(8).toList()

            if (error is GatewayHttpException) {
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
            val exponent = (failureCount - 1).coerceIn(0, 4)
            return (5_000L * (1L shl exponent)).coerceAtMost(60_000L)
        }
    }
}

data class PairingResult(
    val deviceToken: String,
    val deviceId: String,
    val storeName: String
)

data class SmsJob(
    val id: String,
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
    val merchantDisabled: Boolean
)

class GatewayHttpException(
    val statusCode: Int,
    message: String,
    val retryAfterMs: Long? = null
) : IOException(message)

class GatewayProtocolException(message: String) : IOException(message)
