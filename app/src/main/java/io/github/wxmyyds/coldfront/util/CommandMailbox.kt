package io.github.wxmyyds.coldfront.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * A lifecycle-owned mailbox with one consumer for complete suspending commands. Cancelling a
 * reply skips queued work, while an admitted transaction and accepted device operation continue.
 * Do not await this mailbox recursively from one of its commands.
 */
internal class CommandMailbox(scope: CoroutineScope) {
    private class Command<T>(val action: suspend () -> T) {
        val reply = CompletableDeferred<T>()

        suspend fun run() {
            if (!reply.isActive) return
            try {
                reply.complete(action())
            } catch (cause: CancellationException) {
                reply.cancel(cause)
                // A command cancellation affects its reply only. Scope cancellation stops the consumer.
                currentCoroutineContext().ensureActive()
            } catch (cause: Exception) {
                reply.completeExceptionally(cause)
            }
        }
    }

    private val commands = Channel<Command<*>>(
        capacity = Channel.UNLIMITED,
        onUndeliveredElement = { it.reply.cancel() },
    )
    init {
        scope.launch {
            var active: Command<*>? = null
            try {
                for (command in commands) {
                    active = command
                    command.run()
                    active = null
                }
            } finally {
                active?.reply?.cancel()
                commands.cancel()
            }
        }.invokeOnCompletion {
            // Close replies if cancellation happens before the consumer starts.
            commands.cancel()
        }
    }

    suspend fun <T> execute(action: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val command = Command(action)
        if (commands.trySend(command).isFailure) throw CancellationException("Command mailbox is closed")
        return try {
            command.reply.await()
        } catch (cause: CancellationException) {
            command.reply.cancel(cause)
            throw cause
        }
    }

    /** Stop accepting commands, drain admitted work, and then release the consumer. */
    fun close() {
        commands.close()
    }
}
