package io.github.wxmyyds.coldfront.service

import io.github.wxmyyds.coldfront.data.retryStorageReads
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/**
 * Seed notifications before reading the stored language, so BLE updates do not wait on storage.
 * Retry only language reads, not the combined BLE stream. Keep [onStart] outside the retry so a
 * later storage outage retains the last language instead of resetting to the system locale.
 */
internal fun Flow<String>.recoverNotificationLanguage(
    reportError: (IOException) -> Unit,
): Flow<String> = retryStorageReads(reportError).onStart { emit("system") }
