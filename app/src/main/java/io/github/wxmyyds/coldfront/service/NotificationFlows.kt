package io.github.wxmyyds.coldfront.service

import io.github.wxmyyds.coldfront.data.retryStorageReads
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

/**
 * Seed notifications before the first language read so BLE updates never wait for storage.
 * Retry only language reads, not the BLE combine. onStart is outside retry so an outage after
 * a successful read retains that language rather than resetting notifications to system locale.
 */
internal fun Flow<String>.recoverNotificationLanguage(
    reportError: (IOException) -> Unit,
): Flow<String> = retryStorageReads(reportError).onStart { emit("system") }
