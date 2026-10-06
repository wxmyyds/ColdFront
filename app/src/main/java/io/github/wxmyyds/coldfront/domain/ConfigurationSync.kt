package io.github.wxmyyds.coldfront.domain

import java.util.UUID

/** Initialization may publish CONNECTED only after every discovered config characteristic replied. */
internal fun configurationReadComplete(
    required: Set<UUID>,
    received: Set<UUID>,
): Boolean = received.containsAll(required)
