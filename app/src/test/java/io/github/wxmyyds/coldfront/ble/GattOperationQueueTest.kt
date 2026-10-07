package io.github.wxmyyds.coldfront.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GattOperationQueueTest {
    private val success = GattOperationQueue.Result(true)

    @Test
    fun `callback must match owner target identity and operation kind`() = runTest {
        val owner = Any()
        // Equal targets must not count as the same characteristic/descriptor instance.
        data class Target(val uuid: String)
        val target = Target("1012")
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val operation = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { true }) { true }
        }
        runCurrent()
        assertFalse(queue.complete(Any(), target, GattOperationQueue.Kind.WRITE, success))
        assertFalse(queue.complete(owner, Target("1012"), GattOperationQueue.Kind.WRITE, success))
        assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
        assertFalse(operation.isCompleted)
        assertTrue(queue.complete(owner, target, GattOperationQueue.Kind.WRITE, success))
        assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.WRITE, success))
        advanceUntilIdle()
        assertTrue(operation.await().success)
    }

    @Test
    fun `accepted operation retains lane when its caller is cancelled`() = runTest {
        val owner = Any()
        val first = Any()
        val second = Any()
        val starts = mutableListOf<Any>()
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val original = launch {
            queue.execute(owner, first, GattOperationQueue.Kind.WRITE, 1000, { true }) {
                starts += first
                true
            }
        }
        runCurrent()
        original.cancel()
        val next = async {
            queue.execute(owner, second, GattOperationQueue.Kind.WRITE, 1000, { true }) {
                starts += second
                true
            }
        }
        runCurrent()
        assertEquals(listOf(first), starts)
        val callbackTime = testScheduler.currentTime
        assertTrue(queue.complete(owner, first, GattOperationQueue.Kind.WRITE, success))
        runCurrent()
        assertEquals(callbackTime, testScheduler.currentTime)
        assertEquals(listOf(first, second), starts)
        assertFalse(queue.complete(owner, first, GattOperationQueue.Kind.WRITE, success))
        assertTrue(queue.complete(owner, second, GattOperationQueue.Kind.WRITE, success))
        advanceUntilIdle()
        assertTrue(next.await().success)
    }

    @Test
    fun `cancelled service style waiter does not cancel manager owned smart command`() = runTest {
        val owner = Any()
        val target = Any()
        var committed = false
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        // Same ownership used by setSmartAndAwait: manager owns async, service only awaits.
        val managerCommand = backgroundScope.async {
            val result = queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { true }) { true }
            committed = result.success
            result.success
        }
        val serviceWaiter = launch { managerCommand.await() }
        runCurrent()
        serviceWaiter.cancel()
        runCurrent()
        assertTrue(managerCommand.isActive)
        assertFalse(committed)
        assertTrue(queue.complete(owner, target, GattOperationQueue.Kind.WRITE, success))
        runCurrent()
        assertTrue(managerCommand.await())
        assertTrue(committed)
    }

    @Test
    fun `superseded accepted operation still closes session on missing callback`() = runTest {
        val owner = Any()
        val target = Any()
        var fresh = true
        var timedOut = false
        val queue = GattOperationQueue(onTimeout = { timedOut = true })
        val operation = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { fresh }) { true }
        }
        runCurrent()
        fresh = false
        advanceUntilIdle()
        assertTrue(timedOut)
        assertFalse(operation.await().success)
    }

    @Test
    fun `optional timeout quarantines late callbacks and blocks retry for every kind`() = runTest {
        for (kind in GattOperationQueue.Kind.entries) {
            val owner = Any()
            val target = Any()
            var starts = 0
            val queue = GattOperationQueue(onTimeout = { error("Optional timeout must not poison owner") })
            val first = async {
                queue.execute(owner, target, kind, 100, { true }, poisonOnTimeout = false) { starts++; true }
            }
            runCurrent()
            // Queue the retry before timeout, as periodic telemetry can already be waiting.
            val next = async {
                queue.execute(owner, target, kind, 100, { true }, poisonOnTimeout = false) { starts++; true }
            }
            advanceTimeBy(100)
            runCurrent()
            assertFalse(first.await().success)
            assertFalse(next.await().success)
            assertEquals(1, starts)
            assertFalse(queue.complete(owner, target, kind, success))

            // Permanent quarantine cannot be drained or downgraded by a lighter retry.
            assertFalse(queue.complete(owner, target, kind, success))
            val afterLateCallback = queue.execute(owner, target, kind, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
            assertFalse(afterLateCallback.success)
            assertFalse(queue.execute(owner, target, kind, 100, { true }) { starts++; true }.success)
            assertEquals(1, starts)
        }
    }

    @Test
    fun `quarantine reports degradation once but keeps old callbacks isolated from a new owner`() = runTest {
        val old = Any()
        val replacement = Any()
        val target = Any()
        val degradedOwners = mutableListOf<Any>()
        val queue = GattOperationQueue(
            onTimeout = { error("Optional timeout must not poison owner") },
            onQuarantined = { degradedOwners += it },
        )
        val timedOut = async {
            queue.execute(old, target, GattOperationQueue.Kind.READ, 100, { true }, poisonOnTimeout = false) { true }
        }
        advanceUntilIdle()
        assertFalse(timedOut.await().success)
        assertEquals(listOf(old), degradedOwners)
        assertFalse(queue.complete(old, target, GattOperationQueue.Kind.READ, success))
        assertFalse(queue.execute(old, target, GattOperationQueue.Kind.READ, 100, { true }, poisonOnTimeout = false) {
            error("Quarantined read must not reach the platform")
        }.success)
        assertEquals(listOf(old), degradedOwners)

        // Keep the old owner's tombstone while the replacement has a pending operation.
        val resumed = async {
            queue.execute(replacement, target, GattOperationQueue.Kind.READ, 100, { true }, poisonOnTimeout = false) { true }
        }
        runCurrent()
        assertFalse(queue.complete(old, target, GattOperationQueue.Kind.READ, success))
        assertFalse(resumed.isCompleted)
        assertTrue(queue.complete(replacement, target, GattOperationQueue.Kind.READ, success))
        advanceUntilIdle()
        assertTrue(resumed.await().success)
        assertEquals(listOf(old), degradedOwners)
    }

    @Test
    fun `fatal timeout or explicitly rejected telemetry does not report quarantine`() = runTest {
        val owner = Any()
        val target = Any()
        var fatalTimeouts = 0
        val queue = GattOperationQueue(
            onTimeout = { fatalTimeouts++ },
            onQuarantined = { error("Fatal timeout and explicit rejection must not report degradation") },
        )
        val fatal = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 100, { true }) { true }
        }
        advanceUntilIdle()
        assertFalse(fatal.await().success)
        assertEquals(1, fatalTimeouts)
        val rejected = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true }, poisonOnTimeout = false) { false }
        }
        advanceUntilIdle()
        assertFalse(rejected.await().success)
    }

    @Test
    fun `timeout tombstones are scoped to owner identity target identity and kind`() = runTest {
        data class Token(val id: String)
        for (quarantineOnTimeout in listOf(false, true)) for (kind in GattOperationQueue.Kind.entries) {
            val owner = Token("gatt")
            val target = Token("target")
            val queue = GattOperationQueue(onTimeout = { error("Unexpected fatal timeout") })
            val timedOut = async {
                queue.execute(owner, target, kind, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = quarantineOnTimeout) { true }
            }
            advanceUntilIdle()
            assertFalse(timedOut.await().success)

            val unaffected = listOf(
                Triple(Token("gatt"), target, kind), // Equal owner, different identity.
                Triple(owner, Token("target"), kind), // Equal target, different identity.
            ) + GattOperationQueue.Kind.entries.filterNot { it == kind }.map { Triple(owner, target, it) }
            for ((nextOwner, nextTarget, nextKind) in unaffected) {
                var started = false
                val next = async {
                    queue.execute(nextOwner, nextTarget, nextKind, 100, { true }) { started = true; true }
                }
                runCurrent()
                assertTrue(started)
                assertFalse(queue.complete(owner, target, kind, success))
                assertFalse(next.isCompleted)
                assertTrue(queue.complete(nextOwner, nextTarget, nextKind, success))
                advanceUntilIdle()
                assertTrue(next.await().success)
            }
        }
    }

    @Test
    fun `draining a lane does not erase sibling tombstones or complete unrelated pending work`() = runTest {
        data class Token(val id: String)
        val owner = Token("gatt")
        val target = Token("target")
        val lanes = listOf(
            Triple(owner, target, GattOperationQueue.Kind.READ),
            Triple(owner, target, GattOperationQueue.Kind.WRITE),
            Triple(owner, Token("target"), GattOperationQueue.Kind.READ),
            Triple(Token("gatt"), target, GattOperationQueue.Kind.READ),
        )
        val queue = GattOperationQueue(onTimeout = { error("Unexpected fatal timeout") })
        for ((laneOwner, laneTarget, kind) in lanes) {
            val operation = async {
                queue.execute(laneOwner, laneTarget, kind, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = false) { true }
            }
            advanceUntilIdle()
            assertFalse(operation.await().success)
        }
        assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
        var blockedStarts = 0
        for ((laneOwner, laneTarget, kind) in lanes.drop(1)) {
            assertFalse(queue.execute(laneOwner, laneTarget, kind, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { blockedStarts++; true }.success)
            assertEquals(0, blockedStarts)
        }
        val retry = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true }) { true }
        }
        runCurrent()
        for ((laneOwner, laneTarget, kind) in lanes.drop(1)) {
            assertFalse(queue.complete(laneOwner, laneTarget, kind, success))
            assertFalse(retry.isCompleted)
        }
        assertTrue(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
        advanceUntilIdle()
        assertTrue(retry.await().success)
    }

    @Test
    fun `abort clears drainable and permanent tombstones only for that owner identity`() = runTest {
        data class Owner(val address: String)
        val owner = Owner("same-address")
        val otherOwner = Owner("same-address")
        val targets = listOf(Any(), Any())
        val queue = GattOperationQueue(onTimeout = { error("Unexpected fatal timeout") })
        for (gatt in listOf(owner, otherOwner)) {
            for (target in targets) for (kind in GattOperationQueue.Kind.entries) {
                val operation = async {
                    queue.execute(gatt, target, kind, 100, { true },
                        poisonOnTimeout = false, quarantineOnTimeout = target === targets.first()) { true }
                }
                advanceUntilIdle()
                assertFalse(operation.await().success)
            }
        }
        queue.abort(owner)
        for (target in targets) for (kind in GattOperationQueue.Kind.entries) {
            // Reuse the synthetic token solely to verify teardown removed its quarantine.
            var starts = 0
            val operation = async {
                queue.execute(owner, target, kind, 100, { true }) { starts++; true }
            }
            runCurrent()
            assertEquals(1, starts)
            assertTrue(queue.complete(owner, target, kind, success))
            advanceUntilIdle()
            assertTrue(operation.await().success)
            var otherStarts = 0
            val stillQuarantined = queue.execute(otherOwner, target, kind, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) {
                otherStarts++
                false
            }
            assertEquals(0, otherStarts)
            assertFalse(stillQuarantined.success)
            assertFalse(queue.complete(otherOwner, target, kind, success))
        }
    }

    @Test
    fun `accepted timeout invalidates session without retry or next start`() = runTest {
        val owner = Any()
        val target = Any()
        var current = true
        var starts = 0
        var timeouts = 0
        val queue = GattOperationQueue(onTimeout = {
            assertTrue(it === owner)
            current = false
            timeouts++
        })
        val first = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 1000, { current }) { starts++; true }
        }
        val next = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 1000, { current }) { starts++; true }
        }
        advanceUntilIdle()
        assertEquals(1, starts)
        assertEquals(1, timeouts)
        assertFalse(first.await().success)
        assertFalse(next.await().success)
        assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
    }

    @Test
    fun `non-permanent timeout blocks retry until old callback drains without committing its value`() = runTest {
        for (kind in GattOperationQueue.Kind.entries) {
            val owner = Any()
            val target = Any()
            var starts = 0
            val degradedOwners = mutableListOf<Any>()
            val committed = mutableListOf<GattOperationQueue.Result>()
            val queue = GattOperationQueue(
                onTimeout = { error("Non-permanent timeout must not poison the owner") },
                onQuarantined = { degradedOwners += it },
            )
            fun callback(value: GattOperationQueue.Result): Boolean {
                val matched = queue.complete(owner, target, kind, value)
                if (matched && value.success) committed += value
                return matched
            }
            val first = async {
                queue.execute(owner, target, kind, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
            }
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertFalse(first.await().success)
            assertEquals(1, starts)
            assertEquals(listOf(owner), degradedOwners)

            // B arrives after A timed out, but must never reach the platform yet.
            val blocked = async {
                queue.execute(owner, target, kind, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
            }
            runCurrent()
            assertFalse(blocked.await().success)
            assertEquals(1, starts)
            // Elapsed time is not evidence that Android has delivered A's callback.
            advanceTimeBy(10_000)
            assertFalse(queue.execute(owner, target, kind, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }.success)
            assertEquals(1, starts)
            assertEquals(listOf(owner, owner, owner), degradedOwners)

            val oldValue = GattOperationQueue.Result(true, byteArrayOf(1), rssi = -80)
            assertFalse(callback(oldValue))
            assertTrue(committed.isEmpty())
            assertFalse(callback(oldValue)) // Unsolicited once the tombstone is gone.

            // Only a new B, started after draining A, can accept and commit B's callback.
            val retry = async {
                queue.execute(owner, target, kind, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
            }
            runCurrent()
            assertEquals(2, starts)
            assertFalse(retry.isCompleted)
            val newValue = GattOperationQueue.Result(true, byteArrayOf(2), rssi = -50)
            assertTrue(callback(newValue))
            advanceUntilIdle()
            assertEquals(newValue, retry.await())
            assertEquals(listOf(newValue), committed)
        }
    }

    @Test
    fun `retry already waiting at a non-permanent timeout cannot start before drain`() = runTest {
        val owner = Any()
        val target = Any()
        var starts = 0
        val queue = GattOperationQueue(onTimeout = { error("Unexpected fatal timeout") })
        val first = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
        }
        runCurrent()
        val waiting = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
        }
        runCurrent()
        assertFalse(waiting.isCompleted)
        advanceTimeBy(100)
        runCurrent()
        assertFalse(first.await().success)
        assertFalse(waiting.await().success)
        assertEquals(1, starts)
        // A failed callback also drains the old accepted operation, without retrying it.
        assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, GattOperationQueue.Result(false)))
        val retry = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true }) { starts++; true }
        }
        runCurrent()
        assertEquals(2, starts)
        assertFalse(retry.isCompleted)
        assertTrue(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
        advanceUntilIdle()
        assertTrue(retry.await().success)
    }

    @Test
    fun `periodic read upgrades undrained initial read and reports degradation after readiness`() = runTest {
        val owner = Any()
        val target = Any()
        var ready = false
        var starts = 0
        var reports = 0
        val degradedOwners = mutableListOf<Any>()
        val queue = GattOperationQueue(
            onTimeout = { error("Optional read must not poison the owner") },
            onQuarantined = {
                reports++
                // Mirrors the manager: initialization cannot expose degradation yet.
                if (ready) degradedOwners += it
            },
        )
        val initial = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
        }
        advanceUntilIdle()
        assertFalse(initial.await().success)
        assertEquals(1, reports)
        assertTrue(degradedOwners.isEmpty())

        ready = true
        val periodic = async {
            queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                poisonOnTimeout = false) { starts++; true }
        }
        runCurrent()
        assertFalse(periodic.await().success)
        assertEquals(1, starts)
        assertEquals(2, reports)
        assertEquals(listOf(owner), degradedOwners)
        // Once upgraded, neither callbacks nor lighter requests can lift quarantine.
        repeat(2) {
            assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
            assertFalse(queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }.success)
        }
        assertEquals(1, starts)
        assertEquals(2, reports)
    }

    @Test
    fun `strict retry of undrained initial read fails owner without starting platform work`() = runTest {
        for (quarantineOnTimeout in listOf(false, true)) {
            val owner = Any()
            val target = Any()
            var current = true
            var starts = 0
            val failedOwners = mutableListOf<Any>()
            val queue = GattOperationQueue(onTimeout = {
                failedOwners += it
                current = false
            })
            val initial = async {
                queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { current },
                    poisonOnTimeout = false, quarantineOnTimeout = false) { starts++; true }
            }
            advanceUntilIdle()
            assertFalse(initial.await().success)
            // Superseded requests must not escalate a timeout on behalf of a stale intent.
            assertFalse(queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { false }) {
                starts++; true
            }.success)
            assertTrue(failedOwners.isEmpty())

            val strict = async {
                queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { current },
                    poisonOnTimeout = true, quarantineOnTimeout = quarantineOnTimeout) { starts++; true }
            }
            val waiting = async {
                queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { current }) { starts++; true }
            }
            runCurrent()
            assertFalse(strict.await().success)
            assertFalse(waiting.await().success)
            assertEquals(1, starts)
            assertEquals(listOf(owner), failedOwners)
            assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
        }
    }

    @Test
    fun `explicit failure retries only after callback and rejection may retry`() = runTest {
        val owner = Any()
        val target = Any()
        var starts = 0
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val operation = async {
            queue.execute(owner, target, GattOperationQueue.Kind.DESCRIPTOR, 1000, { true }) { starts++; true }
        }
        runCurrent()
        advanceTimeBy(500)
        assertEquals(1, starts)
        queue.complete(owner, target, GattOperationQueue.Kind.DESCRIPTOR, GattOperationQueue.Result(false))
        runCurrent()
        advanceTimeBy(119)
        runCurrent()
        assertEquals(1, starts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, starts)
        queue.complete(owner, target, GattOperationQueue.Kind.DESCRIPTOR, success)
        advanceUntilIdle()
        assertTrue(operation.await().success)

        starts = 0
        val rejected = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { true }) {
                starts++
                if (starts == 1) false else {
                    queue.complete(owner, target, GattOperationQueue.Kind.WRITE, success)
                    true
                }
            }
        }
        advanceUntilIdle()
        assertEquals(2, starts)
        assertTrue(rejected.await().success)
    }

    @Test
    fun `new intent invalidates queued work and retry but does not release accepted lane`() = runTest {
        val owner = Any()
        val target = Any()
        var generation = 1
        val startedGenerations = mutableListOf<Int>()
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val first = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { generation == 1 }) {
                startedGenerations += 1
                true
            }
        }
        runCurrent()
        generation = 2
        val second = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { generation == 2 }) {
                startedGenerations += 2
                true
            }
        }
        runCurrent()
        generation = 3
        val third = async {
            queue.execute(owner, target, GattOperationQueue.Kind.WRITE, 1000, { generation == 3 }) {
                startedGenerations += 3
                true
            }
        }
        runCurrent()
        assertEquals(listOf(1), startedGenerations)
        queue.complete(owner, target, GattOperationQueue.Kind.WRITE, GattOperationQueue.Result(false))
        runCurrent()
        assertEquals(listOf(1, 3), startedGenerations)
        queue.complete(owner, target, GattOperationQueue.Kind.WRITE, success)
        advanceUntilIdle()
        assertFalse(first.await().success)
        assertFalse(second.await().success)
        assertTrue(third.await().success)
    }

    @Test
    fun `session invalidation during retry delay prevents platform retry`() = runTest {
        val owner = Any()
        var current = true
        var starts = 0
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val operation = async {
            queue.execute(owner, Any(), GattOperationQueue.Kind.WRITE, 1000, { current }) { starts++; false }
        }
        runCurrent()
        current = false
        advanceUntilIdle()
        assertFalse(operation.await().success)
        assertEquals(1, starts)
    }

    @Test
    fun `lane availability changes only on timeout drain or teardown not elapsed time`() = runTest {
        for (permanent in listOf(false, true)) {
            val owner = Any()
            val target = Any()
            val queue = GattOperationQueue(onTimeout = { error("Unexpected fatal timeout") })
            val before = queue.availabilityChanges.value
            assertTrue(queue.isAvailable(owner, target, GattOperationQueue.Kind.READ))
            assertFalse(queue.hasTimedOut(owner))
            val result = async {
                queue.execute(owner, target, GattOperationQueue.Kind.READ, 100, { true },
                    poisonOnTimeout = false, quarantineOnTimeout = permanent) { true }
            }
            advanceUntilIdle()
            assertFalse(result.await().success)
            val blocked = queue.availabilityChanges.value
            assertTrue(blocked > before)
            assertFalse(queue.isAvailable(owner, target, GattOperationQueue.Kind.READ))
            assertTrue(queue.isAvailable(owner, target, GattOperationQueue.Kind.WRITE))
            assertTrue(queue.hasTimedOut(owner))
            advanceTimeBy(60_000)
            assertEquals(blocked, queue.availabilityChanges.value)
            assertFalse(queue.complete(owner, target, GattOperationQueue.Kind.READ, success))
            assertEquals(!permanent, queue.isAvailable(owner, target, GattOperationQueue.Kind.READ))
            assertEquals(!permanent, queue.availabilityChanges.value > blocked)
            queue.abort(owner)
            assertTrue(queue.isAvailable(owner, target, GattOperationQueue.Kind.READ))
            assertFalse(queue.hasTimedOut(owner))
        }
    }

    @Test
    fun `old abort and callback cannot complete a replacement session operation`() = runTest {
        val old = Any()
        val replacement = Any()
        val target = Any()
        var current = old
        val queue = GattOperationQueue(onTimeout = { error("Unexpected timeout") })
        val first = async {
            queue.execute(old, target, GattOperationQueue.Kind.RSSI, 1000, { current === old }) { true }
        }
        runCurrent()
        current = replacement
        queue.abort(old)
        val next = async {
            queue.execute(replacement, target, GattOperationQueue.Kind.RSSI, 1000, { current === replacement }) { true }
        }
        runCurrent()
        queue.abort(old)
        assertFalse(queue.complete(old, target, GattOperationQueue.Kind.RSSI, success))
        assertFalse(next.isCompleted)
        queue.complete(replacement, target, GattOperationQueue.Kind.RSSI, success)
        advanceUntilIdle()
        assertFalse(first.await().success)
        assertTrue(next.await().success)
    }
}
