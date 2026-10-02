package se.partee71.reseapoteket.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import se.partee71.reseapoteket.data.common.HouseholdScope
import se.partee71.reseapoteket.data.datastore.settingsDataStore
import se.partee71.reseapoteket.data.export.CacheExportFiles
import se.partee71.reseapoteket.data.export.ExportFiles
import se.partee71.reseapoteket.data.household.HouseholdSession

/** Scope som lever lika länge som appen – för delade StateFlows. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun householdScope(session: HouseholdSession): HouseholdScope

    @Binds
    abstract fun exportFiles(files: CacheExportFiles): ExportFiles

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun clock(): Clock = Clock.System

        /** Enhetens tidszon – "i dag" i förhandsvisningarna (REC-4). */
        @Provides
        fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

        @Provides
        @Singleton
        fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
            settingsDataStore { context.preferencesDataStoreFile("settings") }
    }
}
