package se.partee71.reseapoteket.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Appens former (skill `ui-style`): kort 28 dp, rader 18 dp, pills och knappar helt rundade. */
object AppShapes {
    val card: Shape = RoundedCornerShape(28.dp)
    val row: Shape = RoundedCornerShape(18.dp)
    val pill: Shape = CircleShape

    /** Ikonrutan i ett tomt tillstånd – rundare än ett kort, för att den är större. */
    val iconTile: Shape = RoundedCornerShape(40.dp)
}
