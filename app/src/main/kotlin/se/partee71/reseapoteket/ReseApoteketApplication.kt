package se.partee71.reseapoteket

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import se.partee71.reseapoteket.data.export.ExportFiles
import se.partee71.reseapoteket.di.ApplicationScope

@HiltAndroidApp
class ReseApoteketApplication : Application() {

    @Inject lateinit var exports: ExportFiles

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // En delad export med hälsodata ligger inte kvar i cachen (SET-5).
        scope.launch { exports.cleanUp(clock.now()) }
    }
}
