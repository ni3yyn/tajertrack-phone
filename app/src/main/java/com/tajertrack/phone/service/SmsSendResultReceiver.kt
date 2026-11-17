package com.tajertrack.phone.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.tajertrack.phone.data.AppPreferences

class SmsSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val outcome = when (resultCode) {
            Activity.RESULT_OK -> SmsSendOutcome(
                status = "submitted",
                failureCode = "android_sms_manager_accepted"
            )

            SmsManager.RESULT_ERROR_NO_SERVICE -> SmsSendOutcome(
                status = "failed",
                error = "لا توجد تغطية شبكة جوال على الشريحة المختارة. ستبقى الرسالة في قائمة الانتظار.",
                retryable = true,
                failureCode = "no_service"
            )

            SmsManager.RESULT_ERROR_RADIO_OFF -> SmsSendOutcome(
                status = "failed",
                error = "الشريحة المختارة مغلقة أو وضع الطيران مفعّل. ستتم إعادة المحاولة بعد عودة الشبكة.",
                retryable = true,
                failureCode = "radio_off"
            )

            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                val radioError = intent.getIntExtra("errorCode", -1)
                SmsSendOutcome(
                    status = "failed",
                    error = if (radioError >= 0) {
                        "رفضت الشبكة إرسال الرسالة (رمز المودم $radioError). ستتم إعادة المحاولة تدريجياً."
                    } else {
                        "تعذر على الشبكة إرسال الرسالة مؤقتاً. ستتم إعادة المحاولة تدريجياً."
                    },
                    retryable = true,
                    failureCode = "generic_failure"
                )
            }

            SmsManager.RESULT_ERROR_NULL_PDU -> SmsSendOutcome(
                status = "failed",
                error = "تعذر على Android تجهيز محتوى الرسالة. لن تُعاد المحاولة تلقائياً قبل تصحيح السبب.",
                retryable = false,
                failureCode = "null_pdu"
            )

            else -> {
                if (Build.VERSION.SDK_INT >= 27 && resultCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED) {
                    SmsSendOutcome(
                        status = "failed",
                        error = "بلغ الهاتف حد الإرسال المؤقت. ستتم إعادة المحاولة بعد فترة تهدئة.",
                        retryable = true,
                        failureCode = "android_send_limit"
                    )
                } else if (Build.VERSION.SDK_INT >= 30 && resultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
                    SmsSendOutcome(
                        status = "failed",
                        error = "رفض الهاتف الإرسال بسبب إعدادات الأرقام المسموح بها FDN. راجع إعدادات الشريحة.",
                        retryable = false,
                        failureCode = "fdn_check_failure"
                    )
                } else {
                    SmsSendOutcome(
                        status = "unknown",
                        error = "أعاد Android نتيجة SMS غير معروفة (الرمز $resultCode). لم تتم إعادة الرسالة تلقائياً لتجنب التكرار.",
                        failureCode = "unrecognized_android_result"
                    )
                }
            }
        }

        // Persist the callback in SharedPreferences before waking the service.
        // The broadcast can arrive after Android recreates the app process.
        AppPreferences(context).recordPendingSmsOutcome(
            jobId = jobId,
            status = outcome.status,
            error = outcome.error,
            retryable = outcome.retryable,
            failureCode = outcome.failureCode
        )
        SmsResultBus.complete(jobId, outcome)
    }

    companion object {
        const val ACTION_SMS_SENT = "com.tajertrack.phone.action.SMS_SENT"
        const val EXTRA_JOB_ID = "sms_job_id"
    }
}
