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

    /** Textfält – rundare än Material-standard men tydligt ett fält, inte en knapp. */
    val field: Shape = RoundedCornerShape(14.dp)

    /** Menyer. */
    val menu: Shape = RoundedCornerShape(16.dp)

    /** Bottom sheet: rundat upptill. */
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Liten ikonruta, t.ex. i en sektionsrubrik. */
    val smallTile: Shape = RoundedCornerShape(10.dp)

    /** Ikonrutan i ett tomt tillstånd – rundare än ett kort, för att den är större. */
    val iconTile: Shape = RoundedCornerShape(40.dp)
}
