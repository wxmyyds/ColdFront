package io.github.wxmyyds.coldfront.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageFlowsTest {
    @Test
    fun `snapshot decoder never reparses a corrupt document merely because time passed`() = runTest {
        val snapshots = MutableStateFlow("[")
        val errors = mutableListOf<IOException>()
        var collections = 0
        var decodes = 0
        val values = mutableListOf<Int>()
        val decoded = flow {
            collections++
            snapshots.collect { emit(it) }
        }.decodeStorageSnapshots(errors::add) {
            decodes++
            ProfileJson.parse(it).profiles.size
        }.retryStorageReads { throw AssertionError("Unexpected upstream read failure", it) }
        val collection = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { decoded.toList(values) }
        assertEquals(1, decodes)
        assertEquals(1, errors.size)
        assertTrue(values.isEmpty())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, decodes)
        assertEquals(1, collections)
        assertEquals(1, errors.size)

        snapshots.value = "{"
        runCurrent()
        assertEquals(2, decodes)
        assertEquals(1, errors.size)
        snapshots.value = "[]"
        runCurrent()
        assertEquals(listOf(0), values)
        snapshots.value = "["
        runCurrent()
        assertEquals(4, decodes)
        assertEquals(2, errors.size)
        assertEquals(listOf(0), values)
        collection.cancel()
        snapshots.value = "[]"
        runCurrent()
        assertEquals(4, decodes)
    }

    @Test
    fun `snapshot decoder propagates cancellation and bugs without reporting`() = runTest {
        for (failure in listOf(CancellationException("cancelled decode"), IllegalStateException("decoder bug"))) {
            val errors = mutableListOf<IOException>()
            assertSame(failure, expectFailure<Exception> {
                flowOf("snapshot").decodeStorageSnapshots(errors::add) { throw failure }.collect {}
            })
            assertTrue(errors.isEmpty())
        }
    }

    @Test
    fun `snapshot decoder does not catch upstream downstream or reporter failures`() = runTest {
        val failure = IOException("not a recoverable snapshot")
        val errors = mutableListOf<IOException>()
        assertSame(failure, expectFailure<IOException> {
            flow<String> { throw failure }.decodeStorageSnapshots(errors::add) { it }.collect {}
        })
        assertSame(failure, expectFailure<IOException> {
            flowOf("snapshot").decodeStorageSnapshots(errors::add) { it }.collect { throw failure }
        })
        assertTrue(errors.isEmpty())
        assertSame(failure, expectFailure<IOException> {
            flowOf("[").decodeStorageSnapshots({ throw failure }) { ProfileJson.parse(it) }.collect {}
        })
    }

    @Test
    fun `read retries back off for two seconds and report again only after delivering recovery`() = runTest {
        val firstFailure = IOException("first outage")
        val repeatedFailure = IOException("still offline")
        val secondFailure = IOException("second outage")
        val errors = mutableListOf<IOException>()
        val values = mutableListOf<Int>()
        var collections = 0
        val reads = flow {
            when (++collections) {
                1 -> {
                    emit(1)
                    throw firstFailure
                }
                2 -> throw repeatedFailure
                3 -> {
                    emit(2)
                    throw secondFailure
                }
                else -> {
                    emit(3)
                    awaitCancellation()
                }
            }
        }.retryStorageReads(errors::add)
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { reads.toList(values) }
        assertEquals(listOf(1), values)
        assertEquals(listOf(firstFailure), errors)
        advanceTimeBy(1_999)
        runCurrent()
        assertEquals(1, collections)
        assertEquals(listOf(1), values)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, collections)
        assertEquals(listOf(firstFailure), errors)
        assertEquals(listOf(1), values)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, collections)
        assertEquals(listOf(1, 2), values)
        assertEquals(listOf(firstFailure, secondFailure), errors)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(4, collections)
        assertEquals(listOf(1, 2, 3), values)
        assertEquals(listOf(firstFailure, secondFailure), errors)
    }

    @Test
    fun `retry failure streak belongs to each collector and cancellation ends its backoff`() = runTest {
        val failure = IOException("offline")
        val errors = mutableListOf<IOException>()
        var collections = 0
        val reads = flow<Int> {
            collections++
            throw failure
        }.retryStorageReads(errors::add)
        val first = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { reads.collect {} }
        val second = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { reads.collect {} }
        assertEquals(2, collections)
        assertEquals(listOf(failure, failure), errors)
        first.cancel()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(3, collections) // Only the second collector resubscribes.
        assertEquals(2, errors.size)
        second.cancel()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, collections)
        assertEquals(2, errors.size)
    }

    @Test
    fun `retry operator does not retry cancellation bugs downstream IO or its own reporter`() = runTest {
        for (failure in listOf(CancellationException("cancelled"), IllegalStateException("bug"))) {
            var collections = 0
            val errors = mutableListOf<IOException>()
            assertSame(failure, expectFailure<Exception> {
                flow<Int> {
                    collections++
                    throw failure
                }.retryStorageReads(errors::add).collect {}
            })
            assertEquals(1, collections)
            assertTrue(errors.isEmpty())
        }
        val failure = IOException("consumer or reporter")
        val errors = mutableListOf<IOException>()
        assertSame(failure, expectFailure<IOException> {
            flowOf(1).retryStorageReads(errors::add).collect { throw failure }
        })
        assertTrue(errors.isEmpty())
        var reports = 0
        assertSame(failure, expectFailure<IOException> {
            flow<Int> { throw IOException("upstream read") }.retryStorageReads {
                reports++
                throw failure
            }.collect {}
        })
        assertEquals(1, reports)
    }
}
