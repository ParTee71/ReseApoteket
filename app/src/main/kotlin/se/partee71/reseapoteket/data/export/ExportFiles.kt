package se.partee71.reseapoteket.data.export

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.suspendRunCatching
import se.partee71.reseapoteket.di.IoDispatcher

/** Var exportfilen läggs innan den delas (SET-5). */
interface ExportFiles {
    /** Sparar [text] som [name] och ger en `content://`-adress som delningsmenyn får läsa. */
    suspend fun save(name: String, text: String): Result<String>

    /** Tar bort en export som är äldre än [maxAge] (SET-5) – den har hunnit delas. */
    suspend fun cleanUp(now: Instant, maxAge: Duration = KEEP)

    companion object {
        val KEEP: Duration = 1.hours
    }
}

/**
 * En enda fil i appens cache (`exports/`, se `res/xml/export_paths.xml`) – en tidigare export tas
 * bort först, och den rensas vid appstart när den är äldre än [ExportFiles.KEEP], så att hälsodata
 * inte ligger kvar på enheten. Filen lämnar appen bara via delningsmenyn, på användarens uttryckliga handling.
 */
class CacheExportFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher
) : ExportFiles {
    override suspend fun save(name: String, text: String): Result<String> = suspendRunCatching({ DataError.Unknown }) {
        withContext(io) {
            val dir = File(context.cacheDir, DIR).apply {
                deleteRecursively()
                mkdirs()
            }
            val file = File(dir, name).apply { writeText(text) }
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()
        }
    }

    override suspend fun cleanUp(now: Instant, maxAge: Duration) {
        withContext(io) {
            val dir = File(context.cacheDir, DIR)
            val newest = dir.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: return@withContext
            if (now.toEpochMilliseconds() - newest >= maxAge.inWholeMilliseconds) dir.deleteRecursively()
        }
    }

    private companion object {
        const val DIR = "exports"
        const val AUTHORITY_SUFFIX = ".exports"
    }
}
