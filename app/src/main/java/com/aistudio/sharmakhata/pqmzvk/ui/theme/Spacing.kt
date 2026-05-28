package com.aistudio.sharmakhata.pqmzvk.ui.theme

import androidx.compose.ui.unit.dp

// ============================================================
// SPACING SCALE (4dp base)
// ============================================================
object Spacing {
    val xxsmall = 2.dp
    val xsmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xlarge = 20.dp
    val xxlarge = 24.dp
    val xxxlarge = 32.dp
    val huge = 48.dp
    val gigantic = 64.dp

    /** Horizontal screen padding */
    val screenPadding = 16.dp

    /** Card inner padding */
    val cardPadding = 14.dp

    /** Spacing between list items */
    val listItemGap = 10.dp

    /** Spacing between sections on a screen */
    val sectionGap = 20.dp
}

// ============================================================
// ICON SIZES
// ============================================================
object IconSize {
    val xsmall = 14.dp
    val small = 18.dp
    val medium = 24.dp
    val large = 32.dp
    val xlarge = 40.dp
    val xxlarge = 48.dp
    val huge = 56.dp
}

// ============================================================
// ELEVATION
// ============================================================
object Elevation {
    val none = 0.dp
    val flat = 1.dp          // Minimal lift (timeline items)
    val low = 2.dp           // List cards
    val medium = 4.dp        // Stat cards, standard cards
    val high = 6.dp          // FABs, prominent cards
    val highest = 8.dp       // Dialogs, bottom sheets
    val pressed = 12.dp      // Pressed state
}

// ============================================================
// COMPONENT DIMENSIONS
// ============================================================
object ComponentSize {
    val avatarSmall = 36.dp
    val avatarMedium = 44.dp
    val avatarLarge = 52.dp

    val buttonHeight = 48.dp
    val buttonHeightSmall = 40.dp
    val buttonHeightLarge = 56.dp

    val textFieldHeight = 56.dp

    val topBarHeight = 56.dp
    val bottomNavHeight = 80.dp
    val fabSize = 56.dp

    val statCardMinHeight = 80.dp

    val iconContainerSmall = 32.dp
    val iconContainerMedium = 40.dp
    val iconContainerLarge = 48.dp
}
