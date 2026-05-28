package com.aistudio.sharmakhata.pqmzvk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // Chips, small badges
    small = RoundedCornerShape(8.dp),         // Text fields, small cards
    medium = RoundedCornerShape(12.dp),       // Buttons, dialogs
    large = RoundedCornerShape(16.dp),        // Cards, bottom sheets
    extraLarge = RoundedCornerShape(24.dp)    // Large containers, FAB
)

// ===== NAMED SHAPE TOKENS (for explicit usage) =====

/** Stat cards, dashboard cards */
val CardShape = RoundedCornerShape(16.dp)

/** Customer list cards */
val ListCardShape = RoundedCornerShape(14.dp)

/** Buttons (primary, outlined, text) */
val ButtonShape = RoundedCornerShape(12.dp)

/** Text input fields */
val TextFieldShape = RoundedCornerShape(14.dp)

/** Status badges, tags */
val BadgeShape = RoundedCornerShape(6.dp)

/** Dialogs, alerts */
val DialogShape = RoundedCornerShape(20.dp)

/** Bottom sheets */
val BottomSheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

/** FAB */
val FabShape = RoundedCornerShape(16.dp)

/** Avatar circles */
val AvatarShape = RoundedCornerShape(50)

/** Action grid items */
val ActionCardShape = RoundedCornerShape(14.dp)

/** Icon containers inside action cards */
val ActionIconShape = RoundedCornerShape(12.dp)

/** Search bar */
val SearchBarShape = RoundedCornerShape(14.dp)

/** Snackbar */
val SnackbarShape = RoundedCornerShape(12.dp)

/** Top app bar (if rounded) */
val TopBarShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
