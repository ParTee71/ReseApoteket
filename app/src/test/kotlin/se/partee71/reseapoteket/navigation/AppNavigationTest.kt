package se.partee71.reseapoteket.navigation

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import kotlin.test.assertNotNull
import org.junit.Test
import se.partee71.reseapoteket.core.model.RegimenKind

/** Varje nyckel har en skärm i `appEntries` – en saknad skulle krascha appen (fallbacken är ett fel). */
class AppNavigationTest {

    private val owner = object : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    /** En av varje nyckeltyp; när = AppKey får en ny typ följer listan med (kompilatorn kräver det). */
    private val keys: List<AppKey> = listOf(
        TripsKey,
        PersonsKey,
        FamilyItemsKey,
        SettingsKey,
        PersonDetailKey("p1"),
        PersonEditKey(),
        RegimenEditKey("p1", RegimenKind.MEDICINE),
        FamilyItemEditKey(),
        TripEditKey("t1"),
        PackingListKey("t1"),
        HouseholdEditKey,
        ComponentGalleryKey,
    ).onEach { key ->
        // Uttömmande: en ny nyckeltyp utan gren här ger ett kompileringsfel.
        when (key) {
            TripsKey, PersonsKey, FamilyItemsKey, SettingsKey, is PersonDetailKey, is PersonEditKey,
            is RegimenEditKey, is FamilyItemEditKey, is TripEditKey, is PackingListKey, HouseholdEditKey,
            ComponentGalleryKey,
            -> Unit
        }
    }

    @Test
    fun `varje nyckel har en egen skärm`() {
        val entries = appEntries(AppBackStack(), owner)
        // En nyckel utan skärm når fallbacken, som kastar; en med skärm ger sin post.
        keys.forEach { key -> assertNotNull(entries(key)) }
    }
}
