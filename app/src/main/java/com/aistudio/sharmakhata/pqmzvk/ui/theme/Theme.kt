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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// === CUSTOM SHADOWS ===

val CardShadow = listOf(
    Shadow(color = Color(0xFF000000).copy(alpha = 0.4f), offset = Offset(0f, 4f), blurRadius = 16f),
    Shadow(color = Brand500.copy(alpha = 0.08f), offset = Offset(0f, 2f), blurRadius = 8f)
)

val ElevatedShadow = listOf(
    Shadow(color = Color(0xFF000000).copy(alpha = 0.5f), offset = Offset(0f, 8f), blurRadius = 24f),
    Shadow(color = Brand500.copy(alpha = 0.15f), offset = Offset(0f, 4f), blurRadius = 12f)
)

val ModalShadow = listOf(
    Shadow(color = Color(0xFF000000).copy(alpha = 0.6f), offset = Offset(0f, -4f), blurRadius = 32f),
    Shadow(color = Brand500.copy(alpha = 0.2f), offset = Offset(0f, -2f), blurRadius = 16f)
)

// === LIGHT COLOR SCHEME ===
private val LightColorScheme = lightColorScheme(
    primary = Brand500,
    onPrimary = Color.White,
    primaryContainer = LightCardRaised,
    onPrimaryContainer = Brand900,
    secondary = Saffron500,
    onSecondary = Color.White,
    secondaryContainer = Saffron100,
    onSecondaryContainer = Saffron600,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCardRaised,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = DebtRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// === DARK COLOR SCHEME ===
private val DarkColorScheme = darkColorScheme(
    primary = Saffron500,
    onPrimary = Color.Black,
    primaryContainer = Brand800,
    onPrimaryContainer = Brand100,
    secondary = Brand500,
    onSecondary = Color.White,
    secondaryContainer = Brand700,
    onSecondaryContainer = Brand200,
    background = Ink900,
    onBackground = Ink000,
    surface = Ink800,
    onSurface = Ink000,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink200,
    outline = Ink500,
    outlineVariant = Ink600,
    error = DebtRed,
    onError = Color.White,
    errorContainer = Color(0xFF930002),
    onErrorContainer = Color(0xFFFFDAD6)
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
