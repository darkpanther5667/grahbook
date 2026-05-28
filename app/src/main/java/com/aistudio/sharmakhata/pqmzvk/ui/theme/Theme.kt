package com.aistudio.sharmakhata.pqmzvk.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================
// LIGHT COLOR SCHEME
// ============================================================
private val LightColorScheme = lightColorScheme(
    // Primary — Brand indigo
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,

    // Secondary — Emerald for positive actions
    secondary = EmeraldSecondary,
    onSecondary = Color.White,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = EmeraldOnContainer,

    // Tertiary — Amber for pending/warning
    tertiary = AmberWarning,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,

    // Error
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorOnContainer,

    // Background
    background = AppBackground,
    onBackground = TextPrimaryLight,

    // Surface
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = IndigoPrimary,

    // Outline
    outline = OutlineLight,
    outlineVariant = CardBorder,

    // Inverse (for snackbars, tooltips)
    inverseSurface = Slate800,
    inverseOnSurface = Slate50,
    inversePrimary = IndigoLight,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.32f)
)

// ============================================================
// DARK COLOR SCHEME
// ============================================================
private val DarkColorScheme = darkColorScheme(
    // Primary
    primary = IndigoLight,
    onPrimary = IndigoDark,
    primaryContainer = IndigoDark,
    onPrimaryContainer = IndigoLight,

    // Secondary
    secondary = EmeraldLight,
    onSecondary = EmeraldDark,
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = EmeraldLight,

    // Tertiary
    tertiary = AmberLight,
    onTertiary = AmberDark,
    tertiaryContainer = AmberDark,
    onTertiaryContainer = AmberLight,

    // Error
    error = ErrorRedLight,
    onError = ErrorRedDark,
    errorContainer = ErrorRedDark,
    onErrorContainer = ErrorRedLight,

    // Background
    background = BackgroundDark,
    onBackground = TextPrimaryDark,

    // Surface
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = IndigoLight,

    // Outline
    outline = OutlineDark,
    outlineVariant = DividerDark,

    // Inverse
    inverseSurface = Slate100,
    inverseOnSurface = Slate900,
    inversePrimary = IndigoPrimary,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.6f)
)

@Composable
fun GrahbookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            while (context is android.content.ContextWrapper) {
                if (context is Activity) break
                context = context.baseContext
            }
            if (context is Activity) {
                val window = context.window
                // Edge-to-edge: transparent bars, let content draw behind
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
