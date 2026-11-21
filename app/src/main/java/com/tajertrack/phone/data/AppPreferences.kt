package com.tajertrack.phone.data

import android.content.Context
import android.provider.Settings

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("tajertrack_phone_preferences", Context.MODE_PRIVATE)

    var selectedSubscriptionId: Int
        get() = preferences.getInt(KEY_SUBSCRIPTION_ID, -1)
        set(value) = preferences.edit()
            .putInt(KEY_SUBSCRIPTION_ID, value)
            .putInt(KEY_NEXT_SIM_SLOT, 0)
            .apply()

    /**
     * This is the user's desired relay state, not a snapshot of process liveness.
     * Commit it synchronously so a quick shutdown/reboot cannot lose the choice.
     * When explicitly enabled, remember the boot on which the user enabled it.
     */
    var relayEnabled: Boolean
        get() = preferences.getBoolean(KEY_RELAY_ENABLED, false)
        set(value) {
            val editor = preferences.edit().putBoolean(KEY_RELAY_ENABLED, value)
            if (value) {
                currentBootCount()?.let { editor.putInt(KEY_RELAY_BOOT_COUNT, it) }
            }
            editor.commit()
        }

    /**
     * Claims one auto-start attempt for this actual device boot.
     *
     * Android 15+ can also deliver BOOT_COMPLETED when a force-stopped app is
     * brought out of the stopped state. Comparing BOOT_COUNT prevents that
     * same-boot broadcast from silently re-enabling the SMS relay.
     *
     * A missing saved value is treated as a one-time migration from older builds
     * that stored only relay_enabled.
     */
    fun claimBootAutoStart(): Boolean {
        if (!relayEnabled) return false

        val currentBootCount = currentBootCount() ?: return true
        val previousBootCount = preferences.getInt(KEY_RELAY_BOOT_COUNT, Int.MIN_VALUE)
        if (previousBootCount == currentBootCount) return false

        return preferences.edit()
            .putInt(KEY_RELAY_BOOT_COUNT, currentBootCount)
            .commit()
    }

    private fun currentBootCount(): Int? = runCatching {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()

    var smsSetupUnlocked: Boolean
        get() = preferences.getBoolean(KEY_SMS_SETUP_UNLOCKED, false)
        set(value) = preferences.edit().putBoolean(KEY_SMS_SETUP_UNLOCKED, value).apply()

    var secondarySubscriptionId: Int
        get() = preferences.getInt(KEY_SECONDARY_SUBSCRIPTION_ID, -1)
        set(value) = preferences.edit()
            .putInt(KEY_SECONDARY_SUBSCRIPTION_ID, value)
            .putInt(KEY_NEXT_SIM_SLOT, 0)
            .apply()

    var alternateSimsEnabled: Boolean
        get() = preferences.getBoolean(KEY_ALTERNATE_SIMS_ENABLED, false)
        set(value) = preferences.edit()
            .putBoolean(KEY_ALTERNATE_SIMS_ENABLED, value)
            .putInt(KEY_NEXT_SIM_SLOT, 0)
            .apply()

    /** The configured SIMs are an explicit allow-list, never an automatic rotation list. */
    fun enabledSubscriptionIds(): Set<Int> = buildSet {
        if (selectedSubscriptionId >= 0) add(selectedSubscriptionId)
        if (alternateSimsEnabled && secondarySubscriptionId >= 0 &&
            secondarySubscriptionId != selectedSubscriptionId
        ) add(secondarySubscriptionId)
    }

    /** Legacy lines use only the explicitly selected primary SIM. */
    fun nextSubscriptionIdForMessage(): Int = selectedSubscriptionId

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

    var lastSmsDispatchAt: Long
        get() = preferences.getLong(KEY_LAST_SMS_DISPATCH_AT, 0L)
        private set(value) = preferences.edit().putLong(KEY_LAST_SMS_DISPATCH_AT, value).apply()

    /**
     * Persist a claim before the SMS API can be called. If the process dies in
     * this stage, recovery can prove that Android was never asked to send it.
     */
    fun savePendingSmsClaim(jobId: String, deviceId: String, claimToken: String): Boolean =
        savePendingSmsReport(
            PendingSmsReport(
                jobId = jobId,
                deviceId = deviceId,
                status = "not_sent",
                error = null,
                claimToken = claimToken,
                stage = STAGE_CLAIMED
            )
        )

    /** Mark the local point after which the SMS call may have happened. */
    fun markPendingSmsInvoking(jobId: String): Boolean {
        if (preferences.getString(KEY_PENDING_REPORT_JOB_ID, null) != jobId) return false
        return preferences.edit()
            .putString(KEY_PENDING_REPORT_STAGE, STAGE_INVOKING)
            .putLong(KEY_LAST_SMS_DISPATCH_AT, System.currentTimeMillis())
            .commit()
    }

    /** Save the final/ambiguous SMS outcome synchronously before any network report. */
    fun savePendingSmsOutcome(report: PendingSmsReport): Boolean {
        if (preferences.getString(KEY_PENDING_REPORT_JOB_ID, null) != report.jobId) return false
        val existingStage = preferences.getString(KEY_PENDING_REPORT_STAGE, null)
        val existingStatus = preferences.getString(KEY_PENDING_REPORT_STATUS, null)
        // A sent callback may have persisted a definitive result just before
        // the coroutine's timeout path returns "unknown". Never overwrite it.
        if (existingStage == STAGE_RESULT &&
            existingStatus in setOf("submitted", "failed") &&
            report.status == "unknown"
        ) {
            return true
        }
        return savePendingSmsReport(report.copy(stage = STAGE_RESULT))
    }

    fun savePendingSmsReport(report: PendingSmsReport): Boolean {
        return preferences.edit()
            .putString(KEY_PENDING_REPORT_JOB_ID, report.jobId)
            .putString(KEY_PENDING_REPORT_DEVICE_ID, report.deviceId)
            .putString(KEY_PENDING_REPORT_STATUS, report.status)
            .putString(KEY_PENDING_REPORT_ERROR, report.error.orEmpty())
            .putString(KEY_PENDING_REPORT_CLAIM_TOKEN, report.claimToken.orEmpty())
            .putString(KEY_PENDING_REPORT_STAGE, report.stage)
            .putBoolean(KEY_PENDING_REPORT_RETRYABLE, report.retryable)
            .putString(KEY_PENDING_REPORT_FAILURE_CODE, report.failureCode.orEmpty())
            .commit()
    }

    /**
     * SMS sent broadcasts can arrive after the service process was recreated.
     * The receiver writes the result directly to this journal before notifying
     * the in-memory coroutine, so a process death cannot discard the callback.
     */
    fun recordPendingSmsOutcome(
        jobId: String,
        status: String,
        error: String?,
        retryable: Boolean,
        failureCode: String?
    ): Boolean {
        if (status !in setOf("submitted", "failed", "unknown")) return false
        if (preferences.getString(KEY_PENDING_REPORT_JOB_ID, null) != jobId) return false

        val stage = preferences.getString(KEY_PENDING_REPORT_STAGE, STAGE_RESULT)
        val existingStatus = preferences.getString(KEY_PENDING_REPORT_STATUS, null)
        if (stage != STAGE_INVOKING && !(stage == STAGE_RESULT && existingStatus == "unknown")) {
            return false
        }

        return savePendingSmsReport(
            PendingSmsReport(
                jobId = jobId,
                deviceId = preferences.getString(KEY_PENDING_REPORT_DEVICE_ID, "").orEmpty(),
                status = status,
                error = error,
                claimToken = preferences.getString(KEY_PENDING_REPORT_CLAIM_TOKEN, null)
                    ?.takeIf { it.isNotBlank() },
                stage = STAGE_RESULT,
                retryable = retryable,
                failureCode = failureCode
            )
        )
    }

    fun pendingSmsReport(): PendingSmsReport? {
        val jobId = preferences.getString(KEY_PENDING_REPORT_JOB_ID, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        val status = preferences.getString(KEY_PENDING_REPORT_STATUS, null)
            ?.takeIf { it in setOf("submitted", "failed", "unknown", "not_sent") } ?: return null

        return PendingSmsReport(
            jobId = jobId,
            deviceId = preferences.getString(KEY_PENDING_REPORT_DEVICE_ID, "").orEmpty(),
            status = status,
            error = preferences.getString(KEY_PENDING_REPORT_ERROR, null)?.takeIf { it.isNotBlank() },
            claimToken = preferences.getString(KEY_PENDING_REPORT_CLAIM_TOKEN, null)
                ?.takeIf { it.isNotBlank() },
            // Older app releases stored only the result and did not persist a stage.
            stage = preferences.getString(KEY_PENDING_REPORT_STAGE, STAGE_RESULT) ?: STAGE_RESULT,
            retryable = preferences.getBoolean(KEY_PENDING_REPORT_RETRYABLE, false),
            failureCode = preferences.getString(KEY_PENDING_REPORT_FAILURE_CODE, null)
                ?.takeIf { it.isNotBlank() }
        )
    }

    fun clearPendingSmsReport(jobId: String) {
        if (preferences.getString(KEY_PENDING_REPORT_JOB_ID, null) != jobId) return
        preferences.edit()
            .remove(KEY_PENDING_REPORT_JOB_ID)
            .remove(KEY_PENDING_REPORT_DEVICE_ID)
            .remove(KEY_PENDING_REPORT_STATUS)
            .remove(KEY_PENDING_REPORT_ERROR)
            .remove(KEY_PENDING_REPORT_CLAIM_TOKEN)
            .remove(KEY_PENDING_REPORT_STAGE)
            .remove(KEY_PENDING_REPORT_RETRYABLE)
            .remove(KEY_PENDING_REPORT_FAILURE_CODE)
            .commit()
    }

    companion object {
        private const val KEY_SUBSCRIPTION_ID = "selected_subscription_id"
        private const val KEY_RELAY_ENABLED = "relay_enabled"
        private const val KEY_RELAY_BOOT_COUNT = "relay_auto_start_boot_count"
        private const val KEY_SMS_SETUP_UNLOCKED = "sms_setup_unlocked"
        private const val KEY_SECONDARY_SUBSCRIPTION_ID = "secondary_subscription_id"
        private const val KEY_ALTERNATE_SIMS_ENABLED = "alternate_sims_enabled"
        private const val KEY_NEXT_SIM_SLOT = "next_sim_slot"
        private const val KEY_RELAY_CONNECTION_STATE = "relay_connection_state"
        private const val KEY_RELAY_STATUS_MESSAGE = "relay_status_message"
        private const val KEY_RELAY_STATUS_UPDATED_AT = "relay_status_updated_at"
        private const val KEY_RELAY_LAST_SUCCESSFUL_POLL_AT = "relay_last_successful_poll_at"
        private const val KEY_RELAY_FAILURE_COUNT = "relay_failure_count"
        private const val KEY_LAST_SMS_DISPATCH_AT = "last_sms_dispatch_at"
        private const val KEY_PENDING_REPORT_JOB_ID = "pending_sms_report_job_id"
        private const val KEY_PENDING_REPORT_DEVICE_ID = "pending_sms_report_device_id"
        private const val KEY_PENDING_REPORT_STATUS = "pending_sms_report_status"
        private const val KEY_PENDING_REPORT_ERROR = "pending_sms_report_error"
        private const val KEY_PENDING_REPORT_CLAIM_TOKEN = "pending_sms_report_claim_token"
        private const val KEY_PENDING_REPORT_STAGE = "pending_sms_report_stage"
        private const val KEY_PENDING_REPORT_RETRYABLE = "pending_sms_report_retryable"
        private const val KEY_PENDING_REPORT_FAILURE_CODE = "pending_sms_report_failure_code"

        const val STAGE_CLAIMED = "claimed"
        const val STAGE_INVOKING = "invoking"
        const val STAGE_RESULT = "result"
    }
}

data class PendingSmsReport(
    val jobId: String,
    val deviceId: String,
    val status: String,
    val error: String?,
    val claimToken: String? = null,
    val stage: String = AppPreferences.STAGE_RESULT,
    val retryable: Boolean = false,
    val failureCode: String? = null
)
