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

    // Secondary — Stitch Teal for positive actions
    secondary = StitchTeal,
    onSecondary = Color.White,
    secondaryContainer = StitchTealContainer,
    onSecondaryContainer = StitchTealOnContainer,

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
// DARK COLOR SCHEME (WhatsApp Green primary — Stitch design)
// ============================================================
private val DarkColorScheme = darkColorScheme(
    // Primary — WhatsApp Green
    primary = WhatsAppPrimary,
    onPrimary = Color(0xFF00331A),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    // Secondary — Blue accent
    secondary = AccentBlue,
    onSecondary = Color(0xFF001A40),
    secondaryContainer = AccentBlueContainer,
    onSecondaryContainer = AccentBlueOnContainer,

    // Tertiary — Amber for pending/warning
    tertiary = AmberWarning,
    onTertiary = Color(0xFF331A00),
    tertiaryContainer = AmberDark,
    onTertiaryContainer = AmberLight,

    // Error
    error = ErrorRed,
    onError = Color(0xFF330000),
    errorContainer = ErrorRedDark,
    onErrorContainer = ErrorRedLight,

    // Background — Stitch dark
    background = StitchBg,
    onBackground = StitchTextPrimary,

    // Surface
    surface = StitchSurface,
    onSurface = StitchTextPrimary,
    surfaceVariant = StitchSurfaceVariant,
    onSurfaceVariant = StitchTextSecondary,
    surfaceTint = WhatsAppPrimary,

    // Outline
    outline = StitchBorder,
    outlineVariant = StitchBorder,

    // Inverse
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = WhatsAppPrimaryDark,

    // Scrim
    scrim = Color.Black.copy(alpha = 0.6f)
)

@Composable
fun GrahbookTheme(
    darkTheme: Boolean = true,
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
