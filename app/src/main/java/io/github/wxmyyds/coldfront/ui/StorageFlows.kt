package io.github.wxmyyds.coldfront.ui

import io.github.wxmyyds.coldfront.data.retryStorageReads
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Keep the last complete value during retries; null may be used for a not-yet-loaded snapshot. */
internal fun <T> Flow<T>.storageStateIn(
    scope: CoroutineScope,
    initialValue: T,
    reportError: (IOException) -> Unit,
): StateFlow<T> = retryStorageReads(reportError).stateIn(scope, SharingStarted.Eagerly, initialValue)
