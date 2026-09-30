package se.partee71.reseapoteket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import se.partee71.reseapoteket.ui.AppRoot
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReseApoteketTheme {
                AppRoot()
            }
        }
    }
}
