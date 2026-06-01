package com.aistudio.sharmakhata.pqmzvk.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    BottomNavItem("home", "Home", Icons.Default.Home),
    BottomNavItem("customers", "Customers", Icons.Default.Group),
    BottomNavItem("bills", "Bills", Icons.Default.Receipt),
    BottomNavItem("inventory", "Inventory", Icons.Default.Inventory2),
    BottomNavItem("reports", "Reports", Icons.Default.Assessment),
)

@Composable
fun FloatingBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(64.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(GrahbookRadius.xxl),
            color = Ink700,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navItems.forEach { item ->
                    val selected = currentRoute == item.route
                    
                    var isPressed by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "nav_item_scale"
                    )

                    val tintColor by animateColorAsState(
                        targetValue = if (selected) Saffron500 else Ink300,
                        label = "nav_item_tint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .scale(scale)
                            .clip(RoundedCornerShape(GrahbookRadius.lg))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPressed = true
                                isPressed = false
                                onNavigate(item.route)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .then(
                                    if (selected) Modifier.background(Brand600.copy(alpha = 0.3f), CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = tintColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tintColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = Syne,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
