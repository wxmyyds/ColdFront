package io.github.wxmyyds.coldfront.service

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationFlowsTest {
    @Test
    fun `BLE keeps updating through initial and repeated language failures without losing last language`() = runTest {
        val ble = MutableStateFlow(1)
        val failAgain = CompletableDeferred<Unit>()
        val firstFailure = IOException("first language read")
        val laterFailure = IOException("later outage")
        val errors = mutableListOf<IOException>()
        var subscriptions = 0
        val language = flow {
            when (++subscriptions) {
                1 -> throw firstFailure
                2 -> {
                    emit("zh")
                    failAgain.await()
                    throw laterFailure
                }
                else -> throw laterFailure
            }
        }.recoverNotificationLanguage(errors::add)
        val values = mutableListOf<Pair<Int, String>>()
        val collection = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            // Same topology as CoolerService: recover one input, never restart BLE combine.
            combine(ble, language) { state, localized -> state to localized }.toList(values)
        }
        runCurrent()
        assertEquals(1 to "system", values.last())
        assertEquals(listOf(firstFailure), errors)
        ble.value = 2
        runCurrent()
        assertEquals(2 to "system", values.last())

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(2 to "zh", values.last())
        ble.value = 3
        runCurrent()
        assertEquals(3 to "zh", values.last())
        failAgain.complete(Unit)
        runCurrent()
        assertEquals(listOf(firstFailure, laterFailure), errors)
        ble.value = 4
        runCurrent()
        assertEquals(4 to "zh", values.last())
        advanceTimeBy(6_000)
        runCurrent()
        ble.value = 5
        runCurrent()
        assertEquals(5 to "zh", values.last())
        assertTrue(values.dropWhile { it.second != "zh" }.all { it.second == "zh" })
        assertEquals(listOf(firstFailure, laterFailure), errors)

        collection.cancel()
        runCurrent()
        val previousSubscriptions = subscriptions
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(previousSubscriptions, subscriptions)
    }

    @Test
    fun `initial language read may suspend indefinitely without blocking BLE notifications`() = runTest {
        val firstRead = CompletableDeferred<String>()
        val ble = MutableStateFlow("connecting")
        val language = flow { emit(firstRead.await()) }.recoverNotificationLanguage {
            throw AssertionError("Unexpected language error", it)
        }
        val values = mutableListOf<Pair<String, String>>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(ble, language) { state, localized -> state to localized }.toList(values)
        }
        runCurrent()
        assertEquals("connecting" to "system", values.last())
        ble.value = "connected"
        runCurrent()
        assertEquals("connected" to "system", values.last())
        firstRead.complete("en")
        runCurrent()
        assertEquals("connected" to "en", values.last())
    }
}
