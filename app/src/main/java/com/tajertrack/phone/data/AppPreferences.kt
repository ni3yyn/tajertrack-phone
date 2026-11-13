package com.tajertrack.phone.data

import android.content.Context

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("tajertrack_phone_preferences", Context.MODE_PRIVATE)

    var selectedSubscriptionId: Int
        get() = preferences.getInt(KEY_SUBSCRIPTION_ID, -1)
        set(value) = preferences.edit().putInt(KEY_SUBSCRIPTION_ID, value).apply()

    var relayEnabled: Boolean
        get() = preferences.getBoolean(KEY_RELAY_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_RELAY_ENABLED, value).apply()

    var smsSetupUnlocked: Boolean
        get() = preferences.getBoolean(KEY_SMS_SETUP_UNLOCKED, false)
        set(value) = preferences.edit().putBoolean(KEY_SMS_SETUP_UNLOCKED, value).apply()

    var relayConnectionState: String
        get() = preferences.getString(KEY_RELAY_CONNECTION_STATE, "stopped").orEmpty()
        set(value) = preferences.edit().putString(KEY_RELAY_CONNECTION_STATE, value).apply()

    var relayStatusMessage: String
        get() = preferences.getString(KEY_RELAY_STATUS_MESSAGE, "").orEmpty()
        set(value) = preferences.edit().putString(KEY_RELAY_STATUS_MESSAGE, value).apply()

    var relayStatusUpdatedAt: Long
        get() = preferences.getLong(KEY_RELAY_STATUS_UPDATED_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_RELAY_STATUS_UPDATED_AT, value).apply()

    var relayLastSuccessfulPollAt: Long
        get() = preferences.getLong(KEY_RELAY_LAST_SUCCESSFUL_POLL_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_RELAY_LAST_SUCCESSFUL_POLL_AT, value).apply()

    var consecutiveRelayFailures: Int
        get() = preferences.getInt(KEY_RELAY_FAILURE_COUNT, 0)
        set(value) = preferences.edit().putInt(KEY_RELAY_FAILURE_COUNT, value.coerceAtLeast(0)).apply()

    /** Saves an SMS result before reporting it, so the same SMS is never resent after a lost API response. */
    fun savePendingSmsReport(report: PendingSmsReport): Boolean {
        return preferences.edit()
            .putString(KEY_PENDING_REPORT_JOB_ID, report.jobId)
            .putString(KEY_PENDING_REPORT_DEVICE_ID, report.deviceId)
            .putString(KEY_PENDING_REPORT_STATUS, report.status)
            .putString(KEY_PENDING_REPORT_ERROR, report.error.orEmpty())
            .commit()
    }

    fun pendingSmsReport(): PendingSmsReport? {
        val jobId = preferences.getString(KEY_PENDING_REPORT_JOB_ID, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        val status = preferences.getString(KEY_PENDING_REPORT_STATUS, null)
            ?.takeIf { it in setOf("submitted", "failed", "unknown") } ?: return null

        return PendingSmsReport(
            jobId = jobId,
            deviceId = preferences.getString(KEY_PENDING_REPORT_DEVICE_ID, "").orEmpty(),
            status = status,
            error = preferences.getString(KEY_PENDING_REPORT_ERROR, null)?.takeIf { it.isNotBlank() }
        )
    }

    fun clearPendingSmsReport(jobId: String) {
        if (preferences.getString(KEY_PENDING_REPORT_JOB_ID, null) != jobId) return
        preferences.edit()
            .remove(KEY_PENDING_REPORT_JOB_ID)
            .remove(KEY_PENDING_REPORT_DEVICE_ID)
            .remove(KEY_PENDING_REPORT_STATUS)
            .remove(KEY_PENDING_REPORT_ERROR)
            .commit()
    }

    companion object {
        private const val KEY_SUBSCRIPTION_ID = "selected_subscription_id"
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_SMS_SETUP_UNLOCKED = "sms_setup_unlocked"
        private const val KEY_RELAY_CONNECTION_STATE = "relay_connection_state"
        private const val KEY_RELAY_STATUS_MESSAGE = "relay_status_message"
        private const val KEY_RELAY_STATUS_UPDATED_AT = "relay_status_updated_at"
        private const val KEY_RELAY_LAST_SUCCESSFUL_POLL_AT = "relay_last_successful_poll_at"
        private const val KEY_RELAY_FAILURE_COUNT = "relay_failure_count"
        private const val KEY_PENDING_REPORT_JOB_ID = "pending_sms_report_job_id"
        private const val KEY_PENDING_REPORT_DEVICE_ID = "pending_sms_report_device_id"
        private const val KEY_PENDING_REPORT_STATUS = "pending_sms_report_status"
        private const val KEY_PENDING_REPORT_ERROR = "pending_sms_report_error"
    }
}

data class PendingSmsReport(
    val jobId: String,
    val deviceId: String,
    val status: String,
    val error: String?
)
