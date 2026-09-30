package se.partee71.reseapoteket.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import se.partee71.reseapoteket.core.model.DoseSchedule
import se.partee71.reseapoteket.core.model.Person
import se.partee71.reseapoteket.core.model.Regimen
import se.partee71.reseapoteket.core.model.Trip
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.data.common.CollectionFactory
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.dataError
import se.partee71.reseapoteket.data.firestore.Paths

/**
 * Kontraktet för `EntityCollection` (skill firestore-data-layer). Körs mot `FakeCollection` i
 * JVM i varje PR och mot riktig `FirestoreCollection` + Firebase-emulatorn som instrumenttest –
 * så att fake och verklighet bevisligen beter sig lika.
 */
abstract class CollectionContract {

    /** Ett nytt, tomt och skrivbart hushåll per test, med rå åtkomst förbi codecen. */
    interface Environment {
        val householdId: String
        val collections: CollectionFactory

        /** Tiden samlingarnas klocka anger. */
        val now: Instant

        /** Skriver ett dokument som det är, som en annan appversion skulle göra. */
        suspend fun writeRaw(path: String, id: String, doc: Doc)

        /** Läser ett dokument som det är lagrat (tidpunkter som [Instant]). */
        suspend fun readRaw(path: String, id: String): Doc?

        /** Hushållets data får ett nyare format än appen. */
        fun makeHouseholdNewerThanApp()
    }

    protected abstract fun createEnvironment(): Environment

    private lateinit var env: Environment
    private val persons get() = env.collections.persons()
    private val regimens get() = env.collections.regimens("p1")
    private val trips get() = env.collections.trips()
    private val personsPath get() = Paths.persons(env.householdId)
    private val regimensPath get() = Paths.regimens(env.householdId, "p1")

    @Before
    fun setUpEnvironment() {
        env = createEnvironment()
    }

    private fun contract(block: suspend () -> Unit) = runBlocking { withTimeout(TIMEOUT_MS) { block() } }

    private suspend fun <T> Flow<T>.awaitMatching(predicate: (T) -> Boolean): T = first(predicate)

    @Test
    fun upsert_syns_i_observe_sorterat_pa_sortOrder() = contract {
        persons.upsert(Person("b", "Bertil", sortOrder = 2)).getOrThrow()
        persons.upsert(Person("a", "Anna", sortOrder = 1)).getOrThrow()
        val list = persons.observe().awaitMatching { it.size == 2 }
        assertEquals(listOf("a", "b"), list.map { it.id })
    }

    @Test
    fun observe_och_get_pa_id_ger_dokumentet_eller_null_for_okant_id() = contract {
        persons.upsert(Person("a", "Anna")).getOrThrow()
        assertEquals("Anna", persons.observe("a").awaitMatching { it != null }?.name)
        assertEquals("Anna", persons.get("a").getOrThrow()?.name)
        assertNull(persons.get("finns-inte").getOrThrow())
        assertNull(persons.observe("finns-inte").first())
    }

    @Test
    fun upsert_pa_samma_id_ersatter_kanda_falt() = contract {
        persons.upsert(Person("a", "Anna", emoji = "🦊")).getOrThrow()
        persons.upsert(Person("a", "Anna-Karin", emoji = "")).getOrThrow()
        val stored = persons.observe("a").awaitMatching { it?.name == "Anna-Karin" }
        assertEquals("", stored?.emoji)
    }

    @Test
    fun delete_tar_bort_dokumentet() = contract {
        persons.upsert(Person("a", "Anna")).getOrThrow()
        persons.observe().awaitMatching { it.size == 1 }
        persons.delete("a").getOrThrow()
        persons.observe().awaitMatching { it.isEmpty() }
        assertNull(env.readRaw(personsPath, "a"))
    }

    @Test
    fun setArchived_vaxlar_arkiverat_utan_att_rora_andra_falt() = contract {
        persons.upsert(Person("a", "Anna")).getOrThrow()
        persons.setArchived("a", true).getOrThrow()
        assertEquals(true, persons.observe("a").awaitMatching { it?.archived == true }?.archived)
        persons.setArchived("a", false).getOrThrow()
        assertEquals("Anna", persons.observe("a").awaitMatching { it?.archived == false }?.name)
    }

    @Test
    fun okant_falt_fran_en_nyare_app_bevaras_vid_upsert() = contract {
        env.writeRaw(personsPath, "a", mapOf("name" to "Anna", "favoritfärg" to "grön"))
        val person = persons.observe("a").awaitMatching { it != null }!!
        persons.upsert(person.copy(name = "Anna B")).getOrThrow()
        persons.observe("a").awaitMatching { it?.name == "Anna B" }
        assertEquals("grön", env.readRaw(personsPath, "a")?.get("favoritfärg"))
    }

    @Test
    fun tomt_valfritt_falt_forsvinner_vid_upsert() = contract {
        regimens.upsert(Regimen("r1", name = "Levaxin", packageSize = 100)).getOrThrow()
        regimens.upsert(Regimen("r1", name = "Levaxin", packageSize = null)).getOrThrow()
        assertNull(regimens.observe("r1").awaitMatching { it?.packageSize == null }?.packageSize)
        assertNull(env.readRaw(regimensPath, "r1")?.get("packageSize"))
    }

    @Test
    fun okand_dosering_fran_en_nyare_app_bevaras_nar_andra_falt_andras() = contract {
        val newer = mapOf("type" to "VID_FULLMANE", "fas" to 3L)
        env.writeRaw(regimensPath, "r1", mapOf("name" to "Nytt", "schedule" to newer))
        val regimen = regimens.observe("r1").awaitMatching { it != null }!!
        assertEquals(DoseSchedule.Unknown(newer), regimen.schedule)
        regimens.upsert(regimen.copy(notes = "Till kvällen")).getOrThrow()
        regimens.observe("r1").awaitMatching { it?.notes == "Till kvällen" }
        assertEquals(newer, env.readRaw(regimensPath, "r1")?.get("schedule"))
    }

    @Test
    fun byte_fran_okand_till_kand_dosering_sparar_den_kanda() = contract {
        env.writeRaw(regimensPath, "r1", mapOf("name" to "Nytt", "schedule" to mapOf("type" to "VID_FULLMANE")))
        val regimen = regimens.observe("r1").awaitMatching { it != null }!!
        regimens.upsert(regimen.copy(schedule = DoseSchedule.Daily(dosesPerDay = 2))).getOrThrow()
        val stored = regimens.observe("r1").awaitMatching { it?.schedule is DoseSchedule.Daily }
        assertEquals(DoseSchedule.Daily(dosesPerDay = 2), stored?.schedule)
    }

    @Test
    fun createdAt_raderas_inte_av_en_modell_utan_varde_och_updatedAt_satts_vid_skrivning() = contract {
        val created = Instant.fromEpochSeconds(1_700_000_000)
        trips.upsert(Trip("t1", "Kreta", createdAt = created)).getOrThrow()
        trips.upsert(Trip("t1", "Kreta igen", createdAt = null)).getOrThrow()
        val stored = trips.observe("t1").awaitMatching { it?.name == "Kreta igen" }!!
        assertEquals(created, stored.createdAt)
        assertEquals(env.now, stored.updatedAt)
    }

    @Test
    fun batch_skriver_och_tar_bort_i_ett_svep() = contract {
        persons.batch(upserts = listOf(Person("a", "Anna"), Person("b", "Bertil"))).getOrThrow()
        persons.observe().awaitMatching { it.size == 2 }
        persons.batch(upserts = listOf(Person("c", "Cecilia")), deletes = listOf("a")).getOrThrow()
        val list = persons.observe().awaitMatching { it.map(Person::id).toSet() == setOf("b", "c") }
        assertEquals(setOf("b", "c"), list.map { it.id }.toSet())
        assertEquals(setOf("b", "c"), persons.getAll().getOrThrow().map { it.id }.toSet())
    }

    @Test
    fun skrivning_vagras_nar_hushallets_data_ar_nyare_an_appen() = contract {
        persons.upsert(Person("a", "Anna")).getOrThrow()
        persons.observe().awaitMatching { it.size == 1 }
        env.makeHouseholdNewerThanApp()
        assertEquals(DataError.UpdateRequired, persons.upsert(Person("a", "Kapad")).dataError())
        assertEquals(DataError.UpdateRequired, persons.delete("a").dataError())
        assertEquals(DataError.UpdateRequired, persons.batch(listOf(Person("b"))).dataError())
        assertEquals("Anna", env.readRaw(personsPath, "a")?.get("name"))
        assertTrue(Schema.isNewerThanApp(Schema.CURRENT_VERSION + 1))
    }

    @Test
    fun newId_ger_unika_slumpade_id() = contract {
        val ids = List(50) { persons.newId() }.toSet()
        assertEquals(50, ids.size)
        assertFalse(ids.any { it.isBlank() })
        assertNotEquals(persons.newId(), persons.newId())
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
