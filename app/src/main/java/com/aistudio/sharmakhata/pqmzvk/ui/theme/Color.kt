package com.aistudio.sharmakhata.pqmzvk.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.material3.MaterialTheme

// === BRAND PALETTE ===
// Primary — Deep Indigo (trust, intelligence)
val Brand900 = Color(0xFF1E1B4B)   // darkest bg
val Brand800 = Color(0xFF312E81)   // card bg dark
val Brand700 = Color(0xFF3730A3)   // elevated surface
val Brand600 = Color(0xFF4338CA)   // border, divider
val Brand500 = Color(0xFF4F46E5)   // primary action
val Brand400 = Color(0xFF6366F1)   // hover state
val Brand300 = Color(0xFFA5B4FC)   // muted accent
val Brand200 = Color(0xFFC7D2FE)   // disabled text
val Brand100 = Color(0xFFE0E7FF)   // light surface tint

// Accent — Saffron (energy, India, money in motion)
val Saffron600 = Color(0xFFD45900)  // pressed state
val Saffron500 = Color(0xFFFF6B00)  // primary accent (true saffron/orange color for modern contrast)
val Saffron400 = Color(0xFFFF8C33)  // hover
val Saffron300 = Color(0xFFFFAC66)  // muted
val Saffron100 = Color(0xFFFFF0E6)  // tint surface

// Semantic — Financial
val RupeeGreen = Color(0xFF10B981)   // money received, positive
val RupeeGreenDim = Color(0xFF059669) // darker variant
val DebtRed = Color(0xFFEF4444)      // outstanding, overdue
val DebtRedDim = Color(0xFFDC2626)   // darker variant
val PendingAmber = Color(0xFFF59E0B) // pending, partial
val PendingAmberDim = Color(0xFFD97706)

// Neutrals
val Ink900 = Color(0xFF070B14)    // true black bg
val Ink800 = Color(0xFF0E1322)    // page background
val Ink700 = Color(0xFF161F35)    // card background
val Ink600 = Color(0xFF222E4D)    // elevated card
val Ink500 = Color(0xFF32426C)    // border / divider
val Ink400 = Color(0xFF4C5E8A)    // disabled elements
val Ink300 = Color(0xFF7285B5)    // placeholder text
val Ink200 = Color(0xFF98A9D4)    // secondary text
val Ink100 = Color(0xFFCAD4F0)    // primary text dim
val Ink000 = Color(0xFFF1F4FA)    // primary text light
val White  = Color(0xFFFFFFFF)    // pure white — use sparingly

// Light Mode Surfaces (for light theme variant)
val LightBg         = Color(0xFFF8FAFC)
val LightCard       = Color(0xFFFFFFFF)
val LightCardRaised = Color(0xFFF1F5F9)
val LightBorder     = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

// ============================================================
// BACKWARD COMPATIBILITY ALIASES
// ============================================================
val WhatsAppPrimary = Saffron500
val WhatsAppPrimaryDark = Saffron600
val WhatsAppPrimaryLight = Saffron400
val PrimaryContainer = Brand800
val OnPrimaryContainer = Brand100

val AccentBlue = Brand500
val AccentBlueDark = Brand700
val AccentBlueContainer = Brand800
val AccentBlueOnContainer = Brand200

val IndigoPrimary = Brand500
val IndigoLight = Brand300
val IndigoDark = Brand700
val IndigoContainer = Brand800
val IndigoOnContainer = Brand100

val EmeraldSecondary = RupeeGreen
val EmeraldLight = RupeeGreenDim
val EmeraldDark = Color(0xFF005523)
val EmeraldContainer = Color(0xFF0D2A1C)
val EmeraldOnContainer = RupeeGreen

val AmberWarning = PendingAmber
val AmberLight = PendingAmberDim
val AmberDark = Color(0xFF664400)
val AmberContainer = Color(0xFF2E220D)
val AmberOnContainer = PendingAmber

val ErrorRed = DebtRed
val ErrorRedLight = DebtRedDim
val ErrorRedDark = Color(0xFF93000A)
val ErrorContainer = Color(0xFF3B0A11)
val ErrorOnContainer = DebtRed

val OrangeDanger = Saffron500
val OrangeLight = Saffron400
val OrangeDark = Saffron600

val SuccessGreen = RupeeGreen
val SuccessGreenLight = RupeeGreenDim
val SuccessGreenDark = Color(0xFF005523)
val WarningOrange = PendingAmber

// Slate aliases
val Slate50 = Ink000
val Slate100 = Ink100
val Slate200 = Ink200
val Slate300 = Ink300
val Slate400 = Ink400
val Slate500 = Ink200
val Slate600 = Ink500
val Slate700 = Ink600
val Slate800 = Ink700
val Slate900 = Ink800
val Slate950 = Ink900

// Financial semantic aliases
val AmountDue = DebtRed
val AmountCredit = RupeeGreen
val AmountNeutral = Ink200
val AmountPending = PendingAmber

val BillPaid = RupeeGreen
val BillUnpaid = DebtRed
val BillPartial = PendingAmber
val BillOverdue = DebtRedDim

// Status badge aliases
val BadgePaidBg = RupeeGreen.copy(alpha = 0.15f)
val BadgePaidText = RupeeGreen
val BadgeUnpaidBg = DebtRed.copy(alpha = 0.15f)
val BadgeUnpaidText = DebtRed
val BadgePartialBg = PendingAmber.copy(alpha = 0.15f)
val BadgePartialText = PendingAmber
val BadgeOverdueBg = DebtRedDim.copy(alpha = 0.15f)
val BadgeOverdueText = DebtRedDim

// Stitch compatibility aliases
// Teal / Sky aliases used by ProfileScreen & lock screen
val StitchTeal    = RupeeGreen          // vibrant teal-green accent
val StitchTealDark = RupeeGreenDim      // darker teal for gradients
val StitchSky     = Brand300            // muted indigo-sky blue

val StitchBg: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val StitchSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val StitchSurfaceLow: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

val StitchSurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val StitchSurfaceHighest: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val StitchSurfaceBright: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

val StitchSurfaceLowest: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

val StitchBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

val StitchOutline: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

val StitchTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onBackground

val StitchTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val StitchPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

val StitchPrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primaryContainer

val StitchOnPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimary

val StitchOnPrimaryContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onPrimaryContainer


val BackgroundDark = Ink800
val SurfaceDark = Ink700
val SurfaceVariantDark = Ink600
val SurfaceHighDark = Ink500
val TextPrimaryDark = Ink000
val TextSecondaryDark = Ink200
val TextTertiaryDark = Ink300

val AppBackground = LightBg
val BackgroundLight = LightBg
val SurfaceLight = LightCard
val SurfaceVariantLight = LightCardRaised
val SurfaceHighLight = LightCard

val TextPrimaryLight = LightTextPrimary
val TextSecondaryLight = LightTextSecondary
val TextTertiaryLight = LightTextSecondary
val TextOnPrimaryLight = Color.White

val CardBorder = Ink500
val DividerColor = Ink600
val DividerDark = Ink600
val OutlineLight = LightBorder
val OutlineDark = Ink500

val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppDark = Color(0xFF075E54)
val WhatsAppLight = Color(0xFFDCF8C6)

val StitchSecondary = Brand300
val StitchOnSecondary = Brand900
val StitchSecondaryContainer = Brand700
val StitchOnSecondaryContainer = Brand100

val StitchTertiary = Saffron300
val StitchOnTertiary = Saffron600
val StitchTertiaryContainer = Saffron100
val StitchOnTertiaryContainer = Saffron600

val StitchError = DebtRed
val StitchOnError = Color.White
val StitchErrorContainer = DebtRedDim

val SurfaceCard = LightCard
val SurfaceCardDark = Ink700

val BrandIndigo = Brand500
val BrandSaffron = Saffron500
val BrandGreen = RupeeGreen
val BrandRed = DebtRed

val StitchSurfaceVariant = Ink600
val StitchTextTertiary = Ink200

// Gradient Pairs
val GradientWhatsApp = listOf(WhatsAppGreen, WhatsAppDark)
val GradientTeal = listOf(RupeeGreen, RupeeGreenDim)
val GradientSky = listOf(Brand300, Brand500)
val GradientIndigo = listOf(Brand500, Brand700)
val GradientEmerald = listOf(RupeeGreen, RupeeGreenDim)
val GradientAmber = listOf(PendingAmber, PendingAmberDim)
val GradientOrange = listOf(Saffron400, Saffron600)
val GradientPurple = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))

val AvatarColors = listOf(
    listOf(Brand500, Brand700),
    listOf(Saffron500, Saffron600),
    listOf(RupeeGreen, RupeeGreenDim),
    listOf(PendingAmber, PendingAmberDim),
    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
    listOf(Color(0xFF06B6D4), Color(0xFF0E7490)),
    listOf(Color(0xFF84CC16), Color(0xFF4D7C0F))
)
