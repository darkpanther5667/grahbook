package com.aistudio.sharmakhata.pqmzvk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*

// ============================================================
// BUTTONS
// ============================================================

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
        ),
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.low,
            pressedElevation = Elevation.medium
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(Modifier.width(Spacing.small))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = ButtonShape
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(Modifier.width(Spacing.small))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        shape = ButtonShape,
        border = ButtonDefaults.outlinedButtonBorder(enabled)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(Modifier.width(Spacing.small))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun WhatsAppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.buttonHeight),
        colors = ButtonDefaults.buttonColors(
            containerColor = WhatsAppGreen,
            contentColor = Color.White
        ),
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.low,
            pressedElevation = Elevation.medium
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.small))
            Spacer(Modifier.width(Spacing.small))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ============================================================
// STATUS BADGES
// ============================================================

enum class BillStatus { PAID, UNPAID, PARTIAL, OVERDUE }

@Composable
fun StatusBadge(
    status: BillStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        BillStatus.PAID -> Triple(BadgePaidBg, BadgePaidText, "Paid")
        BillStatus.UNPAID -> Triple(BadgeUnpaidBg, BadgeUnpaidText, "Unpaid")
        BillStatus.PARTIAL -> Triple(BadgePartialBg, BadgePartialText, "Partial")
        BillStatus.OVERDUE -> Triple(BadgeOverdueBg, BadgeOverdueText, "Overdue")
    }

    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bgColor)
            .padding(horizontal = Spacing.small, vertical = Spacing.xxsmall)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatusBadge(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(BadgeShape)
            .background(bgColor)
            .padding(horizontal = Spacing.small, vertical = Spacing.xxsmall)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ============================================================
// AVATAR
// ============================================================

@Composable
fun AppAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = ComponentSize.avatarMedium,
    colorIndex: Int = 0
) {
    val gradient = AvatarColors[colorIndex % AvatarColors.size]
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors = gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = when {
                size >= ComponentSize.avatarLarge -> MaterialTheme.typography.titleMedium
                size >= ComponentSize.avatarMedium -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================
// SECTION HEADER
// ============================================================

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (action != null) {
            action()
        }
    }
}

// ============================================================
// AMOUNT TEXT
// ============================================================

@Composable
fun AmountText(
    amount: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AmountMediumStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    prefix: String = ""
) {
    Text(
        text = "$prefix$amount",
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

// ============================================================
// INFO ROW (label-value pair for detail screens)
// ============================================================

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            trailing()
        } else {
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

// ============================================================
// DIVIDER
// ============================================================

@Composable
fun AppDivider(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

// ============================================================
// SCREEN WRAPPER (consistent padding + scroll)
// ============================================================

@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenPadding),
        content = content
    )
}

// ============================================================
// MODERN STAT CARD (Vyapaar/MyBillBook style)
// Elevated card with gradient-tinted icon container, label, large amount value
// ============================================================

@Composable
fun ModernStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // Icon in gradient-tinted container
            Box(
                modifier = Modifier
                    .size(ComponentSize.iconContainerMedium)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(gradient.map { it.copy(alpha = 0.15f) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = gradient.first(),
                    modifier = Modifier.size(IconSize.small)
                )
            }

            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Value (large, bold amount)
            Text(
                text = value,
                style = AmountDisplayStyle,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================
// CUSTOMER LIST ITEM (for customer list screens)
// Avatar, name, phone, outstanding amount with color, chevron
// ============================================================

@Composable
fun CustomerListItem(
    name: String,
    phone: String?,
    outstandingAmount: String,
    outstandingLabel: String,
    amountColor: Color,
    colorIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ListCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored gradient avatar
            AppAvatar(
                name = name,
                size = ComponentSize.avatarLarge,
                colorIndex = colorIndex
            )

            Spacer(modifier = Modifier.width(Spacing.medium))

            // Name + phone
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!phone.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xsmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = IndigoPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(IconSize.xsmall)
                        )
                        Text(
                            text = phone,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Outstanding amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = outstandingAmount,
                    style = AmountMediumStyle,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    maxLines = 1
                )
                Text(
                    text = outstandingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = amountColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(Spacing.small))

            // Chevron
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = TextTertiaryLight,
                modifier = Modifier.size(IconSize.medium)
            )
        }
    }
}
