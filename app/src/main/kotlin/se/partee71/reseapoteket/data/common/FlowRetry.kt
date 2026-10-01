package se.partee71.reseapoteket.data.common

import kotlin.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.retryWhen

/**
 * Börjar om flödet med växande paus ([first], dubblas upp till [max]) när [retryOn] godtar felet,
 * så att ett tillfälligt fel aldrig avslutar ett flöde som appen följer. [beforeRetry] kan sända
 * ett ersättningsvärde innan pausen. Andra fel släpps igenom.
 */
fun <T> Flow<T>.retryWithBackoff(
    first: Duration,
    max: Duration,
    retryOn: (Throwable) -> Boolean,
    beforeRetry: suspend FlowCollector<T>.(Throwable) -> Unit = {},
): Flow<T> = retryWhen { cause, attempt ->
    if (!retryOn(cause)) return@retryWhen false
    beforeRetry(cause)
    delay(backoff(first, max, attempt))
    true
}

/** Pausen före försök nummer [attempt] (från 0). */
internal fun backoff(first: Duration, max: Duration, attempt: Long): Duration =
    (first * (1L shl attempt.coerceAtMost(MAX_DOUBLINGS).toInt()).toDouble()).coerceAtMost(max)

private const val MAX_DOUBLINGS = 16L
