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
import java.util.concurrent.TimeUnit

/**
 * The app talks only to TajerTrack's merchant-scoped mobile gateway API.
 * It must never call the legacy global-token / Termux endpoints.
 */
class ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
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
            throw IOException("The pairing response did not include a device credential.")
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

    suspend fun report(token: String, jobId: String, status: String, error: String? = null) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("jobId", jobId)
            .put("status", status)
        if (!error.isNullOrBlank()) body.put("error", error.take(500))
        post("/api/mobile/sms-gateway/device/report", body, token)
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
                throw GatewayHttpException(response.code, serverMessage ?: "TajerTrack returned HTTP ${response.code}.")
            }
            return parsed ?: throw IOException("TajerTrack returned an invalid response.")
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

data class ClaimResult(
    val job: SmsJob?,
    val deviceDisabled: Boolean,
    val gatewayPaused: Boolean,
    val merchantDisabled: Boolean
)

class GatewayHttpException(val statusCode: Int, message: String) : IOException(message)
