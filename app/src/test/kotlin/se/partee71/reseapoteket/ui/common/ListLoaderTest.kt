package se.partee71.reseapoteket.ui.common

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.data.common.DataError

class ListLoaderTest {

    @Test
    fun `försök igen börjar om från laddning och läser källan på nytt`() = runTest(UnconfinedTestDispatcher()) {
        var attempts = 0
        val source = flow {
            attempts++
            if (attempts == 1) throw DataError.Offline
            emit(listOf("Solkräm"))
        }
        val loader = ListLoader(source, backgroundScope)
        loader.state.test {
            assertEquals(ListUiState.Error(DataError.Offline), expectMostRecentItem())
            loader.retry()
            assertEquals(ListUiState.Content(listOf("Solkräm")), expectMostRecentItem())
        }
        assertEquals(2, attempts)
    }

    @Test
    fun `activeOnly döljer arkiverade`() = runTest {
        val people = listOf(Person("a", "Anna"), Person("b", "Bo", archived = true))
        activeOnly(people).test {
            assertEquals(listOf("Anna"), awaitItem().map { it.name })
            awaitComplete()
        }
    }

    private fun activeOnly(people: List<Person>) = flowOf(people).activeOnly()
}
