package com.tajertrack.phone.service

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.content.ContextCompat
import com.tajertrack.phone.data.AppPreferences

class SmsSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SMS_DELIVERED) {
            handleDeliveryReport(context, intent)
            return
        }

        if (intent.action != ACTION_SMS_SENT) return
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
                if (android.os.Build.VERSION.SDK_INT >= 27 && resultCode == SmsManager.RESULT_ERROR_LIMIT_EXCEEDED) {
                    SmsSendOutcome(
                        status = "failed",
                        error = "بلغ الهاتف حد الإرسال المؤقت. ستتم إعادة المحاولة بعد فترة تهدئة.",
                        retryable = true,
                        failureCode = "android_send_limit"
                    )
                } else if (android.os.Build.VERSION.SDK_INT >= 30 && resultCode == SmsManager.RESULT_ERROR_FDN_CHECK_FAILURE) {
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

    private fun handleDeliveryReport(context: Context, intent: Intent) {
        val jobId = intent.getStringExtra(EXTRA_JOB_ID)?.takeIf(String::isNotBlank) ?: return
        val claimToken = intent.getStringExtra(EXTRA_CLAIM_TOKEN)?.takeIf(String::isNotBlank) ?: return
        val pdu = intent.getByteArrayExtra(EXTRA_PDU)

        val statusCode = runCatching {
            if (pdu.isNullOrEmpty()) return@runCatching null
            val format = intent.getStringExtra(EXTRA_FORMAT) ?: SmsMessage.FORMAT_3GPP
            val message = SmsMessage.createFromPdu(pdu, format) ?: return@runCatching null
            if (!message.isStatusReportMessage) return@runCatching null
            message.status
        }.getOrNull()

        // GSM 0 and CDMA 2<<16 are the platform's explicit positive-receipt
        // values. GSM 64..127 are permanent TP-Status failures. Other values
        // do not prove delivery, so retain an honest "unknown" result.
        val deliveryStatus = when {
            statusCode == 0 || statusCode == (2 shl 16) -> "delivered"
            statusCode != null && statusCode in 64..127 -> "failed"
            else -> "unknown"
        }

        val pendingResult = goAsync()
        try {
            SmsDeliveryReportWorker.enqueue(
                context = context,
                jobId = jobId,
                claimToken = claimToken,
                deliveryStatus = deliveryStatus,
                statusCode = statusCode,
                reportedAt = System.currentTimeMillis()
            ).result.addListener(
                { pendingResult.finish() },
                ContextCompat.getMainExecutor(context)
            )
        } catch (error: Exception) {
            Log.e(TAG, "Could not persist the SMS delivery callback for job $jobId.", error)
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_SMS_SENT = "com.tajertrack.phone.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.tajertrack.phone.action.SMS_DELIVERED"
        const val EXTRA_JOB_ID = "sms_job_id"
        const val EXTRA_CLAIM_TOKEN = "sms_claim_token"
        const val EXTRA_PDU = "pdu"
        const val EXTRA_FORMAT = "format"
        private const val TAG = "SmsDeliveryReport"
    }
}
