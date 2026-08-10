package app.materialfrontend.ui.theme

import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.materialfrontend.AppController
import app.materialfrontend.model.Settings
import app.materialfrontend.model.ThemeMode
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

val LocalApp = staticCompositionLocalOf<AppController> { error("No AppController") }

/** Seed colour picked from the focused game's artwork (null = user seed). */
val LocalArtworkSeed = compositionLocalOf<MutableState<Color?>> { error("No seed state") }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialFrontendTheme(settings: Settings, artworkSeed: Color?, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    DynamicMaterialExpressiveTheme(
        seedColor = (if (settings.colorFromArtwork) artworkSeed else null) ?: Color(settings.seedColor),
        motionScheme = if (settings.reduceMotion) MotionScheme.standard() else MotionScheme.expressive(),
        isDark = dark,
        isAmoled = settings.themeMode == ThemeMode.AMOLED,
        style = PaletteStyle.TonalSpot,
        typography = remember { flexTypography() },
        animate = true,
        content = content,
    )
}
