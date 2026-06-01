package com.aistudio.sharmakhata.pqmzvk.ui.sheets

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.BuildConfig
import com.aistudio.sharmakhata.pqmzvk.ui.components.GrahbookDestructiveButton
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val token = SessionManager.token
    val dbState by viewModel.dbState.collectAsState()
    val storeName = when (val s = dbState) {
        is UiState.Success -> s.data.shop?.name ?: "My Store"
        else -> "My Store"
    }
    val ownerName = when (val s = dbState) {
        is UiState.Success -> s.data.shop?.owner ?: ""
        else -> ""
    }
    val customerCount = when (val s = dbState) {
        is UiState.Success -> s.data.customers.size
        else -> 0
    }
    val billCount = when (val s = dbState) {
        is UiState.Success -> s.data.bills.size
        else -> 0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = GrahbookRadius.xxl, topEnd = GrahbookRadius.xxl),
        containerColor = Ink700,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(GrahbookRadius.pill))
                    .background(Ink400)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // === SHOP INFO HEADER ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(GradientIndigo)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = storeName.firstOrNull()?.uppercase() ?: "S",
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = 24.sp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = storeName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink000
                )
                if (ownerName.isNotBlank()) {
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink200
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(GrahbookRadius.pill))
                        .background(if (token != null) RupeeGreen.copy(alpha = 0.15f) else Ink500)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (token != null) "Active Account" else "Not logged in",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (token != null) RupeeGreen else Ink200
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // === STATS ROW ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(GrahbookRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = Ink600)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = customerCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Brand300
                        )
                        Text(
                            text = "Customers",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink200
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(GrahbookRadius.lg),
                    colors = CardDefaults.cardColors(containerColor = Ink600)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = billCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Saffron500
                        )
                        Text(
                            text = "Bills",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink200
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // === WHATSAPP BOT SECTION ===
            SectionHeader("WHATSAPP BOT")
            SheetSettingItem(
                icon = Icons.Default.SmartToy,
                iconTint = RupeeGreen,
                title = "AI WhatsApp Bot",
                subtitle = "Bot hamesha active hai — bas WhatsApp karo",
                trailing = {
                    Switch(
                        checked = true,
                        onCheckedChange = { },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = RupeeGreen,
                            uncheckedThumbColor = Slate400
                        )
                    )
                }
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            SheetSettingItem(
                icon = Icons.Default.Phone,
                iconTint = RupeeGreen,
                title = "Bot Number",
                subtitle = "+91 98765 43210"
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            // === PREFERENCES ===
            SectionHeader("PREFERENCES")
            SheetSettingItem(
                icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                iconTint = Saffron500,
                title = "Dark Mode",
                subtitle = if (isDarkTheme) "Dark mode active" else "Light mode active",
                trailing = {
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Brand500,
                            uncheckedThumbColor = Slate400
                        )
                    )
                }
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            SheetSettingItem(
                icon = Icons.Default.Notifications,
                iconTint = Saffron500,
                title = "Daily Summary Time",
                subtitle = "8:00 PM reminder active"
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            // === ABOUT ===
            SectionHeader("ABOUT")
            SheetSettingItem(
                icon = Icons.Default.Info,
                iconTint = Ink200,
                title = "Version",
                subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})"
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            SheetSettingItem(
                icon = Icons.Default.Sync,
                iconTint = RupeeGreen,
                title = "Sync Status",
                subtitle = if (token != null) "Active" else "Inactive"
            )
            HorizontalDivider(color = Ink600, modifier = Modifier.padding(horizontal = 24.dp))

            // Data Export
            SheetSettingItem(
                icon = Icons.Default.Share,
                iconTint = Brand300,
                title = "Data Export (CSV)",
                subtitle = "Share your data as CSV file",
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Download Grahbook - Billing App\nhttps://wpapp-xz9l.onrender.com")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                }
            )

            Spacer(Modifier.height(20.dp))

            // === LOGOUT ===
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                GrahbookDestructiveButton(
                    text = "Logout",
                    onClick = onLogout,
                    icon = Icons.AutoMirrored.Filled.Logout
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = TextStyle(
            fontFamily = Syne,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Ink300,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetSettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String = "",
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(GrahbookRadius.md))
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Ink000
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink200
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
