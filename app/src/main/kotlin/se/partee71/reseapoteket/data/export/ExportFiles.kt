package se.partee71.reseapoteket.data.export

import android.content.Context
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.data.common.suspendRunCatching

/** Var exportfilen läggs innan den delas (SET-5). */
interface ExportFiles {
    /** Sparar [text] som [name] och ger en `content://`-adress som delningsmenyn får läsa. */
    suspend fun save(name: String, text: String): Result<String>
}

/**
 * En enda fil i appens cache (`exports/`, se `res/xml/export_paths.xml`) – en tidigare export tas
 * bort först, så att hälsodata inte samlas på enheten. Filen lämnar appen bara via delningsmenyn,
 * på användarens uttryckliga handling.
 */
class CacheExportFiles @Inject constructor(@ApplicationContext private val context: Context) : ExportFiles {
    override suspend fun save(name: String, text: String): Result<String> = suspendRunCatching({ DataError.Unknown }) {
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
            val file = File(dir, name).apply { writeText(text) }
            FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()
        }
    }

    private companion object {
        const val DIR = "exports"
        const val AUTHORITY_SUFFIX = ".exports"
    }
}
