package se.partee71.reseapoteket

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import se.partee71.reseapoteket.testing.captureLightAndDark
import se.partee71.reseapoteket.ui.theme.AppColors
import se.partee71.ui.theme.Spacing

/** Appikonen (NFR-19): adaptiv, med enfärgad temaikon, och satt i manifestet. */
@RunWith(RobolectricTestRunner::class)
class AppIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `appikonen är adaptiv med enfärgat lager och används av appen`() {
        for (icon in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val drawable = context.getDrawable(icon) as AdaptiveIconDrawable
            assertNotNull(drawable.foreground)
            assertNotNull(drawable.monochrome, "temaikon (Android 13+)")
        }
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }

    @Test
    fun `ikonens färger är temats – en källa`() {
        assertEquals(AppColors.light.primary.toArgb(), context.getColor(R.color.ic_launcher_background))
        assertEquals(AppColors.light.background.toArgb(), context.getColor(R.color.ic_launcher_symbol))
    }

    @Test
    fun `Appikon`() = captureLightAndDark("AppIcon_launcher") {
        Row(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            for (size in listOf(108.dp, 48.dp)) {
                AndroidView({ ImageView(it).apply { setImageResource(R.mipmap.ic_launcher) } }, Modifier.size(size))
            }
        }
    }
}
