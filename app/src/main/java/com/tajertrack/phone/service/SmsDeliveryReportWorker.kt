package com.tajertrack.phone.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tajertrack.phone.data.ApiClient
import com.tajertrack.phone.data.GatewayHttpException
import com.tajertrack.phone.data.GatewayProtocolException
import java.util.concurrent.TimeUnit

/**
 * Delivers a carrier receipt to TajerTrack even when the phone temporarily
 * loses connectivity or Android kills the app after the broadcast.
 *
 * The random claim token is scoped to this one SMS job. No long-lived sender
 * or manager credential is stored in WorkManager's input data.
 */
class SmsDeliveryReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val claimToken = inputData.getString(KEY_CLAIM_TOKEN)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val deliveryStatus = inputData.getString(KEY_DELIVERY_STATUS)
            ?.takeIf { it in setOf("delivered", "failed", "unknown") }
            ?: return Result.failure()
        val statusCode = inputData.getInt(KEY_STATUS_CODE, -1).takeIf { it >= 0 }
        val reportedAt = inputData.getLong(KEY_REPORTED_AT, 0L)
            .takeIf { it > 0L } ?: System.currentTimeMillis()

        return try {
            ApiClient().reportDelivery(
                jobId = jobId,
                claimToken = claimToken,
                deliveryStatus = deliveryStatus,
                statusCode = statusCode,
                reportedAt = reportedAt
            )
            Result.success()
        } catch (error: GatewayHttpException) {
            // Invalid/expired one-job capabilities and jobs already made
            // ineligible cannot recover by retrying. Server/network errors can.
            if (error.statusCode in setOf(400, 401, 403, 404, 409)) {
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (error: GatewayProtocolException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_JOB_ID = "delivery_job_id"
        private const val KEY_CLAIM_TOKEN = "delivery_claim_token"
        private const val KEY_DELIVERY_STATUS = "delivery_status"
        private const val KEY_STATUS_CODE = "delivery_status_code"
        private const val KEY_REPORTED_AT = "delivery_reported_at"

        fun enqueue(
            context: Context,
            jobId: String,
            claimToken: String,
            deliveryStatus: String,
            statusCode: Int?,
            reportedAt: Long
        ): androidx.work.Operation {
            val data = Data.Builder()
                .putString(KEY_JOB_ID, jobId)
                .putString(KEY_CLAIM_TOKEN, claimToken)
                .putString(KEY_DELIVERY_STATUS, deliveryStatus)
                .putInt(KEY_STATUS_CODE, statusCode ?: -1)
                .putLong(KEY_REPORTED_AT, reportedAt)
                .build()

            val request = OneTimeWorkRequestBuilder<SmsDeliveryReportWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            // A later, stronger receipt (for example delivered after an
            // unparseable interim report) must replace an older queued report.
            return WorkManager.getInstance(context).enqueueUniqueWork(
                "sms-delivery-$jobId",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
