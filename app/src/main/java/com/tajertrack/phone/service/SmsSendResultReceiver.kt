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
            SmsManager.RESULT_ERROR_NO_SERVICE ->
                SmsSendOutcome("failed", "لا توجد تغطية شبكة جوال على الشريحة المختارة.")
            SmsManager.RESULT_ERROR_RADIO_OFF ->
                SmsSendOutcome("failed", "الشريحة المختارة مغلقة أو وضع الطيران مفعّل.")
            SmsManager.RESULT_ERROR_NULL_PDU ->
                SmsSendOutcome("failed", "تعذر على Android تجهيز محتوى الرسالة.")
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                SmsSendOutcome("failed", "رفضت شبكة الهاتف إرسال الرسالة. تحقق من رصيد الشريحة وإعدادات الرسائل.")
            else ->
                SmsSendOutcome("failed", "فشل إرسال SMS من Android (رمز النتيجة " + resultCode + ").")
        }
        SmsResultBus.complete(jobId, outcome)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.tajertrack.phone.action.SMS_SENT"
        const val EXTRA_JOB_ID = "sms_job_id"
    }
}
