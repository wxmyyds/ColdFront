package io.github.wxmyyds.coldfront.data

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen

/**
 * A bad document is a snapshot, not a failed subscription. Keep observing for its replacement,
 * without emitting a fabricated value or retrying the same document on a timer. Only decoding
 * is caught here: upstream failures and downstream collector failures retain their semantics.
 */
internal fun <S, T> Flow<S>.decodeStorageSnapshots(
    reportError: (IOException) -> Unit,
    decode: (S) -> T,
): Flow<T> = flow {
    var errorReported = false
    this@decodeStorageSnapshots.collect { snapshot ->
        val value = try {
            decode(snapshot)
        } catch (failure: IOException) {
            if (!errorReported) {
                errorReported = true
                reportError(failure)
            }
            return@collect
        }
        errorReported = false
        emit(value)
    }
}

/**
 * Actual read I/O has ended the subscription, so there is no next snapshot to wait for. Retry
 * with cancellable backoff, preserving downstream's last value. Report once per failure streak,
 * independently for each collector; a delivered value starts a new streak. Decode errors must
 * be handled per snapshot before reaching this operator. Cancellation/programming errors escape.
 */
internal fun <T> Flow<T>.retryStorageReads(reportError: (IOException) -> Unit): Flow<T> = flow {
    var errorReported = false
    this@retryStorageReads.retryWhen { cause, _ ->
        if (cause !is IOException) return@retryWhen false
        if (!errorReported) {
            errorReported = true
            reportError(cause)
        }
        delay(2_000)
        true
    }.collect { value ->
        emit(value)
        errorReported = false
    }
}
