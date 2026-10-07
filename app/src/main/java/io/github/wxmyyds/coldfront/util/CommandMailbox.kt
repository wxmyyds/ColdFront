package io.github.wxmyyds.coldfront.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * A lifecycle-owned, single-consumer mailbox for complete suspending commands, not lock permits.
 * Producers submit work; only the consumer executes it. Cancelling a reply skips work that has
 * not started, but cannot cancel an already-started transaction or accepted device operation.
 * Callers must not recursively await this same mailbox from one of its commands.
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
                // A command-local cancellation does not kill unrelated queued commands.
                // Lifecycle cancellation, however, must terminate the consumer.
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
            // Also close replies if the scope was cancelled before launch could run.
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

    /** Stop accepting new commands and drain admitted ones, then release the consumer. */
    fun close() {
        commands.close()
    }
}
