package com.tajertrack.phone.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager

class SmsSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val outcome = when (resultCode) {
            Activity.RESULT_OK -> SmsSendOutcome(status = "submitted")
            SmsManager.RESULT_ERROR_NO_SERVICE -> SmsSendOutcome("failed", "No mobile network service was available.")
            SmsManager.RESULT_ERROR_RADIO_OFF -> SmsSendOutcome("failed", "The selected SIM radio is turned off.")
            SmsManager.RESULT_ERROR_NULL_PDU -> SmsSendOutcome("failed", "Android could not create the SMS payload.")
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> SmsSendOutcome("failed", "The mobile network rejected the SMS submission.")
            else -> SmsSendOutcome("failed", "Android SMS send failed (result $resultCode).")
        }
        SmsResultBus.complete(jobId, outcome)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.tajertrack.phone.action.SMS_SENT"
        const val EXTRA_JOB_ID = "sms_job_id"
    }
}
