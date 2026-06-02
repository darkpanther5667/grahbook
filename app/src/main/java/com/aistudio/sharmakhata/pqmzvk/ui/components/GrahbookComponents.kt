package com.aistudio.sharmakhata.pqmzvk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

// === AMOUNT TYPE ENUM ===
enum class GrahbookAmountType {
    RECEIVED, OUTSTANDING, PENDING, NEUTRAL
}

// === AMOUNT TEXT ===
@Composable
fun AmountText(
    amount: Long,           // in paise/smallest unit
    type: GrahbookAmountType,
    size: TextUnit = 17.sp,
    modifier: Modifier = Modifier
) {
    val formatted = "₹${NumberFormat.getNumberInstance(Locale("en", "IN")).format(amount / 100.0)}"
    val color = when (type) {
        GrahbookAmountType.RECEIVED    -> RupeeGreen
        GrahbookAmountType.OUTSTANDING -> DebtRed
        GrahbookAmountType.PENDING     -> PendingAmber
        GrahbookAmountType.NEUTRAL     -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = formatted,
        style = TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            color = color
        ),
        modifier = modifier
    )
}

// === PRIMARY BUTTON ===
@Composable
fun GrahbookPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "button_scale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryVariant = if (primaryColor == Brand500) Brand700 else Saffron600
    val backgroundBrush = if (enabled) {
        Brush.horizontalGradient(listOf(primaryColor, primaryVariant))
    } else {
        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.outline))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(GrahbookRadius.md))
            .background(backgroundBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// === SECONDARY BUTTON ===
@Composable
fun GrahbookSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "button_scale"
    )

    val color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(GrahbookRadius.md))
            .border(
                width = 1.5.dp,
                color = color,
                shape = RoundedCornerShape(GrahbookRadius.md)
            )
            .background(Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// === DESTRUCTIVE BUTTON ===
@Composable
fun GrahbookDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && !isLoading) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "button_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(GrahbookRadius.md))
            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.8f))))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onError,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

// === METRIC CARD ===
@Composable
fun MetricCard(
    label: String,
    amount: Long,   // in paise
    trend: Float?,   // e.g. 12.5f (positive) or -3.2f (negative), null if no trend
    accentColor: Color,
    type: GrahbookAmountType,
    icon: ImageVector,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(160.dp)
            .height(105.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(GrahbookRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left Accent Border
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor)
            )
            
            // Content Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(GrahbookSpacing.md),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    
                    if (trend != null) {
                        val isPositive = trend >= 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(GrahbookRadius.pill))
                                .background(if (isPositive) RupeeGreen.copy(alpha = 0.15f) else DebtRed.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${if (isPositive) "↑" else "↓"} ${kotlin.math.abs(trend)}%",
                                style = TextStyle(
                                    fontFamily = DMSans,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) RupeeGreen else DebtRed
                                )
                            )
                        }
                    }
                }
                
                Column {
                    AmountText(amount = amount, type = type, size = 20.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// === CUSTOMER AVATAR ===
@Composable
fun CustomerAvatar(
    name: String,
    outstandingPaise: Long,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val initial = (name.firstOrNull()?.uppercase() ?: "S").toString()
    
    val (bgColor, textColor) = when {
        outstandingPaise == 0L -> Brand600 to Color.White
        outstandingPaise in 1..99999L -> PendingAmber.copy(alpha = 0.2f) to PendingAmber
        else -> DebtRed.copy(alpha = 0.2f) to DebtRed
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(width = 1.5.dp, color = textColor.copy(alpha = 0.3f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = TextStyle(
                fontFamily = Syne,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
                color = textColor
            )
        )
    }
}

// === STATUS BADGE ===
enum class GrahbookStatus {
    PAID, UNPAID, PARTIAL, ACTIVE, LOW_STOCK
}

@Composable
fun StatusBadge(
    status: GrahbookStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text) = when (status) {
        GrahbookStatus.PAID -> Triple(RupeeGreen.copy(alpha = 0.15f), RupeeGreen, "Paid ✓")
        GrahbookStatus.UNPAID -> Triple(DebtRed.copy(alpha = 0.15f), DebtRed, "Unpaid")
        GrahbookStatus.PARTIAL -> Triple(PendingAmber.copy(alpha = 0.15f), PendingAmber, "Partial")
        GrahbookStatus.ACTIVE -> Triple(Brand500.copy(alpha = 0.15f), Brand300, "Active")
        GrahbookStatus.LOW_STOCK -> Triple(PendingAmber.copy(alpha = 0.15f), PendingAmber, "Low Stock")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GrahbookRadius.pill))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = DMSans,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            )
        )
    }
}

// === TEXT FIELD ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrahbookTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    isAmountField: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    val isFocused = remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> DebtRed
            isFocused.value -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(durationMillis = 200),
        label = "border_color"
    )

    Column(modifier = modifier) {
        // Label
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = Syne,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = if (error != null) DebtRed else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = GrahbookSpacing.xs)
        )

        // Text Field Container
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(GrahbookRadius.md)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            leadingIcon = if (isAmountField) {
                {
                    Text(
                        text = "₹",
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Ink300
                        )
                    )
                }
            } else null,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            shape = RoundedCornerShape(GrahbookRadius.md)
        )

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = DebtRed,
                modifier = Modifier.padding(top = GrahbookSpacing.xs)
            )
        }
    }
}
