@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package se.partee71.reseapoteket.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.ui.theme.AppTypography

enum class TopBarSize {
    /** Toppnivåskärm: stor rundad rubrik som krymper vid rullning. */
    Large,

    /** Underskärm: tillbakaknapp och liten rubrik. */
    Small,
}

/**
 * Skärmrubriken. [onBack] ger en tillbakaknapp; [actions] står till höger (t.ex. `AppMenu`).
 * [scrollBehavior] kopplas av ramarna (`EntityListScreen`, `EntityEditScreen`).
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    size: TopBarSize = TopBarSize.Large,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background)
    val navigation: @Composable () -> Unit = {
        onBack?.let { AppIconButton(R.drawable.ic_arrow_back, stringResource(R.string.back), it, variant = IconButtonVariant.Tonal) }
    }
    val heading = Modifier.semantics { heading() }
    when (size) {
        TopBarSize.Large -> LargeFlexibleTopAppBar(
            title = { Text(title, style = AppTypography.screenTitle, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = heading) },
            modifier = modifier,
            subtitle = subtitle?.let { { Text(it, style = AppTypography.itemSubtitle) } },
            navigationIcon = navigation,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
        TopBarSize.Small -> TopAppBar(
            title = { Text(title, style = AppTypography.sectionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = heading) },
            modifier = modifier,
            navigationIcon = navigation,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}
