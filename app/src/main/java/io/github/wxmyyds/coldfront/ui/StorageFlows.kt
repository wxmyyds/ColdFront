package io.github.wxmyyds.coldfront.ui

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

/** Retry disk reads without inventing defaults, swallowing cancellation, or retrying programming errors. */
internal fun <T> Flow<T>.retryStorageReads(reportError: (IOException) -> Unit): Flow<T> =
    retryWhen { cause, attempt ->
        if (cause !is IOException) return@retryWhen false
        if (attempt == 0L) reportError(cause)
        delay(2_000)
        true
    }

/** Keep the last complete value during retries; null may be used for a not-yet-loaded snapshot. */
internal fun <T> Flow<T>.storageStateIn(
    scope: CoroutineScope,
    initialValue: T,
    reportError: (IOException) -> Unit,
): StateFlow<T> = retryStorageReads(reportError).stateIn(scope, SharingStarted.Eagerly, initialValue)
