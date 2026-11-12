package com.tajertrack.phone.service

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

data class SmsSendOutcome(val status: String, val error: String? = null)

/** In-process handoff from Android's SMS sent PendingIntent back to the foreground relay. */
object SmsResultBus {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<SmsSendOutcome>>()

    fun register(jobId: String): CompletableDeferred<SmsSendOutcome> {
        val deferred = CompletableDeferred<SmsSendOutcome>()
        pending.put(jobId, deferred)?.cancel()
        return deferred
    }

    fun complete(jobId: String, result: SmsSendOutcome) {
        pending.remove(jobId)?.complete(result)
    }

    fun remove(jobId: String) {
        pending.remove(jobId)?.cancel()
    }
}
