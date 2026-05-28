package com.aistudio.sharmakhata.pqmzvk.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================
// BRAND PRIMARY — Indigo
// ============================================================
val IndigoPrimary = Color(0xFF4F46E5)
val IndigoLight = Color(0xFF818CF8)
val IndigoDark = Color(0xFF312E81)
val IndigoContainer = Color(0xFFEEF2FF)      // Light tinted surface
val IndigoOnContainer = Color(0xFF3730A3)     // Text on indigo containers

// ============================================================
// BRAND SECONDARY — Emerald (for positive / money-in)
// ============================================================
val EmeraldSecondary = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF065F46)
val EmeraldContainer = Color(0xFFECFDF5)
val EmeraldOnContainer = Color(0xFF047857)

// ============================================================
// AMBER — Pending / Dues
// ============================================================
val AmberWarning = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFCD34D)
val AmberDark = Color(0xFFB45309)
val AmberContainer = Color(0xFFFFFBEB)
val AmberOnContainer = Color(0xFF92400E)

// ============================================================
// DANGER — Overdue / Error / Money-out
// ============================================================
val ErrorRed = Color(0xFFEF4444)
val ErrorRedLight = Color(0xFFFCA5A5)
val ErrorRedDark = Color(0xFFB91C1C)
val ErrorContainer = Color(0xFFFEF2F2)
val ErrorOnContainer = Color(0xFF991B1B)

val OrangeDanger = Color(0xFFF97316)
val OrangeLight = Color(0xFFFDBA74)
val OrangeDark = Color(0xFFC2410C)

// ============================================================
// SUCCESS — Paid / Received
// ============================================================
val SuccessGreen = Color(0xFF22C55E)
val SuccessGreenLight = Color(0xFF86EFAC)
val SuccessGreenDark = Color(0xFF15803D)

// ============================================================
// WARNING
// ============================================================
val WarningOrange = Color(0xFFF59E0B)

// ============================================================
// NEUTRALS — Slate scale
// ============================================================
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// ============================================================
// LIGHT THEME SURFACES
// ============================================================
val AppBackground = Slate100                  // Page background
val BackgroundLight = Slate50                 // Slightly lighter variant
val SurfaceLight = Color(0xFFFFFFFF)          // Cards, sheets
val SurfaceVariantLight = Slate50             // Elevated surfaces
val SurfaceHighLight = Color(0xFFFFFFFF)      // Highest elevation

val TextPrimaryLight = Slate900               // Headlines, body
val TextSecondaryLight = Slate500             // Captions, timestamps
val TextTertiaryLight = Slate400              // Placeholders, disabled
val TextOnPrimaryLight = Color(0xFFFFFFFF)    // Text on brand color

// ============================================================
// DARK THEME SURFACES
// ============================================================
val BackgroundDark = Slate900                 // Page background
val SurfaceDark = Slate800                    // Cards, sheets
val SurfaceVariantDark = Slate700             // Elevated surfaces
val SurfaceHighDark = Slate700               // Highest elevation

val TextPrimaryDark = Slate50                 // Headlines, body
val TextSecondaryDark = Slate400              // Captions, timestamps
val TextTertiaryDark = Slate500              // Placeholders, disabled

// ============================================================
// BORDERS & DIVIDERS
// ============================================================
val CardBorder = Slate200
val DividerColor = Slate200
val DividerDark = Color(0xFF334155)
val OutlineLight = Slate300
val OutlineDark = Slate600

// ============================================================
// FINANCIAL SEMANTIC COLORS
// ============================================================
val AmountDue = ErrorRed                      // Customer owes money
val AmountCredit = SuccessGreen               // You owe customer / money received
val AmountNeutral = Slate500                  // Settled / zero balance
val AmountPending = AmberWarning              // Pending / partial

val BillPaid = SuccessGreen
val BillUnpaid = ErrorRed
val BillPartial = AmberWarning
val BillOverdue = Color(0xFFDC2626)           // Stronger red for overdue

// ============================================================
// STATUS BADGE COLORS
// ============================================================
val BadgePaidBg = Color(0xFFDCFCE7)
val BadgePaidText = Color(0xFF166534)
val BadgeUnpaidBg = Color(0xFFFEE2E2)
val BadgeUnpaidText = Color(0xFF991B1B)
val BadgePartialBg = Color(0xFFFEF3C7)
val BadgePartialText = Color(0xFF92400E)
val BadgeOverdueBg = Color(0xFFFEE2E2)
val BadgeOverdueText = Color(0xFF7F1D1D)

// ============================================================
// WHATSAPP BRAND
// ============================================================
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppDark = Color(0xFF128C7E)
val WhatsAppLight = Color(0xFFDCF8C6)

// ============================================================
// GRADIENT PAIRS (for stat cards, avatars, action icons)
// ============================================================
val GradientIndigo = listOf(IndigoPrimary, IndigoDark)
val GradientEmerald = listOf(EmeraldSecondary, EmeraldDark)
val GradientAmber = listOf(AmberWarning, AmberDark)
val GradientOrange = listOf(OrangeDanger, OrangeDark)
val GradientPurple = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))
val GradientWhatsApp = listOf(WhatsAppGreen, WhatsAppDark)

// ============================================================
// AVATAR TINT COLORS (for customer list variety)
// ============================================================
val AvatarColors = listOf(
    listOf(IndigoPrimary, IndigoDark),
    listOf(EmeraldSecondary, EmeraldDark),
    listOf(AmberWarning, AmberDark),
    listOf(OrangeDanger, OrangeDark),
    listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
    listOf(Color(0xFF06B6D4), Color(0xFF0E7490)),
    listOf(Color(0xFF84CC16), Color(0xFF4D7C0F)),
)
