package se.partee71.reseapoteket.data.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** `combineByKey`: flödet för nyckeln byts bara när nyckeln ändras, och utan blandade värden. */
class KeyedFlowsTest {

    private data class Trip(val id: String, val name: String)

    @Test
    fun `samma nyckel följer källan utan att flödet för nyckeln startas om`() = runTest {
        val trips = MutableStateFlow(listOf(Trip("t1", "Kreta")))
        val starts = mutableListOf<Set<String>>()
        val counts = MutableStateFlow(1)
        val flow = trips.combineByKey({ list -> list.map { it.id }.toSet() }, { ids -> counts.onStart { starts += ids } }) { list, count ->
            list.map { it.name } to count
        }
        flow.test {
            assertEquals(listOf("Kreta") to 1, awaitItem())
            trips.value = listOf(Trip("t1", "Kreta igen"))
            assertEquals(listOf("Kreta igen") to 1, awaitItem())
            counts.value = 2
            assertEquals(listOf("Kreta igen") to 2, awaitItem())
            assertEquals(listOf(setOf("t1")), starts)
        }
    }

    @Test
    fun `en ny nyckel kombineras aldrig med den förra nyckelns data`() = runTest { neverMixesKeys() }

    @Test
    fun `en ny nyckel kombineras aldrig med den förra nyckelns data – inte heller utan kö`() = runTest(UnconfinedTestDispatcher()) { neverMixesKeys() }

    private suspend fun neverMixesKeys() {
        val trips = MutableStateFlow(listOf(Trip("t1", "Kreta")))
        val data = mapOf(setOf("t1") to MutableSharedFlow<String>(replay = 1), setOf("t1", "t2") to MutableSharedFlow(replay = 1))
        data.getValue(setOf("t1")).tryEmit("för t1")
        val flow = trips.combineByKey({ list -> list.map { it.id }.toSet() }, { ids -> data.getValue(ids) }) { list, value -> list.size to value }
        flow.test {
            assertEquals(1 to "för t1", awaitItem())
            trips.value = listOf(Trip("t1", "Kreta"), Trip("t2", "Rom"))
            expectNoEvents()
            data.getValue(setOf("t1", "t2")).emit("för t1 och t2")
            assertEquals(2 to "för t1 och t2", awaitItem())
        }
    }
}
