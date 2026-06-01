package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.DashboardViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.LiveSyncManager
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import kotlinx.coroutines.launch
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import androidx.compose.ui.res.stringResource
import com.aistudio.sharmakhata.pqmzvk.R
import com.aistudio.sharmakhata.pqmzvk.ui.components.AmountText
import com.aistudio.sharmakhata.pqmzvk.ui.components.GrahbookAmountType
import com.aistudio.sharmakhata.pqmzvk.ui.components.CustomerAvatar
import com.aistudio.sharmakhata.pqmzvk.ui.components.MetricCard
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onMenuClick: () -> Unit = {},
    shopInitial: String = "V",
    onNavigateToCustomers: () -> Unit,
    onNavigateToWebView: () -> Unit,
    onCreateInvoice: () -> Unit = {},
    onAddCustomer: () -> Unit = {},
    onRecordPayment: () -> Unit = {},
    onViewReports: () -> Unit = {},
    onSendReminder: () -> Unit = {},
    onWhatsApp: () -> Unit = {}
) {
    val dbState by viewModel.dbState.collectAsState()
    val reportState by viewModel.reportState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val shopName = remember(dbState) {
        when (dbState) {
            is UiState.Success -> (dbState as UiState.Success).data.shop?.name ?: context.getString(R.string.default_shop_name)
            else -> context.getString(R.string.default_shop_name)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Ink800)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // === CUSTOM TOP BAR ===
            DashboardTopBar(
                shopName = shopName,
                onMenuClick = onMenuClick,
                shopInitial = storeNameFirstLetter(shopName),
                isBotActive = true
            )

            // === SYNC STATUS STRIP ===
            SyncStatusStrip(reportState is UiState.Loading)

            // === MAIN CONTENT ===
            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = reportState is UiState.Loading,
                onRefresh = { scope.launch { LiveSyncManager.forceRefresh() } },
                modifier = Modifier.fillMaxSize()
            ) {
                when (reportState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Saffron500)
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = DebtRed, modifier = Modifier.size(48.dp))
                                Text(stringResource(R.string.could_not_load_dashboard), style = MaterialTheme.typography.bodyLarge, color = Ink000)
                                Text((reportState as UiState.Error).message, style = MaterialTheme.typography.bodySmall, color = Ink200, textAlign = TextAlign.Center)
                                Button(
                                    onClick = { scope.launch { LiveSyncManager.forceRefresh() } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Brand500, contentColor = Color.White),
                                    shape = RoundedCornerShape(GrahbookRadius.md)
                                ) { Text(stringResource(R.string.retry)) }
                            }
                        }
                    }
                    is UiState.Success -> {
                        val report = (reportState as UiState.Success).data
                        DashboardContent(
                            report = report,
                            dbState = dbState,
                            onCreateInvoice = onCreateInvoice,
                            onAddCustomer = onAddCustomer,
                            onRecordPayment = onRecordPayment,
                            onViewReports = onViewReports,
                            onSendReminder = onSendReminder,
                            onNavigateToCustomers = onNavigateToCustomers,
                            onWhatsApp = onWhatsApp
                        )
                    }
                }
            }
        }
    }
}

private fun storeNameFirstLetter(name: String): String {
    return (name.firstOrNull()?.uppercase() ?: "S").toString()
}

@Composable
private fun SyncStatusStrip(isSyncing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(Ink900),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSyncing) Icons.Default.Sync else Icons.Default.Wifi,
            contentDescription = null,
            tint = if (isSyncing) Saffron500 else RupeeGreen,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isSyncing) "Syncing..." else "Last synced: 2 min ago",
            style = MaterialTheme.typography.bodySmall,
            color = Ink300
        )
    }
}

// ===== CUSTOM TOP BAR =====
@Composable
private fun DashboardTopBar(
    shopName: String,
    onMenuClick: () -> Unit,
    shopInitial: String,
    isBotActive: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ink800)
            .statusBarsPadding()
            .height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shop Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brand600)
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shopInitial,
                    color = Color.White,
                    style = TextStyle(
                        fontFamily = Syne,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                )
            }
            
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Namaste 👋",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink300
                )
                Text(
                    text = shopName,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink000,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // WhatsApp Bot Status
            IconButton(onClick = onMenuClick) {
                Box {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "Bot Status",
                        tint = Ink200
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isBotActive) RupeeGreen else Ink400)
                            .align(Alignment.TopEnd)
                    )
                }
            }

            // Notification bell
            IconButton(onClick = {}) {
                Box {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Ink200
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(DebtRed)
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}

// ===== DASHBOARD CONTENT =====
@Composable
private fun DashboardContent(
    report: DailyReport,
    dbState: UiState<FullDatabase>,
    onCreateInvoice: () -> Unit,
    onAddCustomer: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewReports: () -> Unit,
    onSendReminder: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val totalOutstanding = if (report.outstanding.isNotEmpty()) report.outstanding.sumOf { it.balance } else 0.0
    val overdueCount = report.outstanding.size

    val db = when (dbState) {
        is UiState.Success -> (dbState as UiState.Success).data
        else -> null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp), // space for bottom nav
        verticalArrangement = Arrangement.spacedBy(GrahbookSpacing.lg)
    ) {
        // ===== SECTION 1: SUMMARY CARDS =====
        item {
            Spacer(modifier = Modifier.height(GrahbookSpacing.md))
            Text(
                text = "AAJK KA HISAB",
                style = TextStyle(
                    fontFamily = Syne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Saffron500,
                    letterSpacing = 2.sp
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = GrahbookSpacing.xs)
            )
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    MetricCard(
                        label = "Outstanding",
                        amount = (totalOutstanding * 100).toLong(),
                        trend = if (totalOutstanding > 0) 12.5f else null,
                        accentColor = DebtRed,
                        type = GrahbookAmountType.OUTSTANDING,
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = onNavigateToCustomers
                    )
                }
                item {
                    MetricCard(
                        label = "Today's Collection",
                        amount = (report.paymentTotal * 100).toLong(),
                        trend = 8.3f,
                        accentColor = RupeeGreen,
                        type = GrahbookAmountType.RECEIVED,
                        icon = Icons.Default.TrendingUp
                    )
                }
                item {
                    MetricCard(
                        label = "Pending Bills",
                        amount = (overdueCount * 100).toLong(), // standard map to value
                        trend = null,
                        accentColor = PendingAmber,
                        type = GrahbookAmountType.PENDING,
                        icon = Icons.Default.ReceiptLong
                    )
                }
                item {
                    MetricCard(
                        label = "Low Stock",
                        amount = 0L,
                        trend = null,
                        accentColor = Saffron500,
                        type = GrahbookAmountType.NEUTRAL,
                        icon = Icons.Default.Inventory2
                    )
                }
            }
        }

        // ===== SECTION 2: QUICK ACTIONS =====
        item {
            Text(
                text = "JALDI KARO",
                style = TextStyle(
                    fontFamily = Syne,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Ink300,
                    letterSpacing = 2.sp
                ),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(GrahbookSpacing.xs))
            QuickActionsCard(
                onCreateInvoice = onCreateInvoice,
                onAddCustomer = onAddCustomer,
                onRecordPayment = onRecordPayment,
                onSendReminder = onSendReminder
            )
        }

        // ===== SECTION 3: WHATSAPP BOT STATUS CARD =====
        item {
            WhatsAppBotActiveCard(onWhatsApp = onWhatsApp)
        }

        // ===== SECTION 4: RECENT TRANSACTIONS =====
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = TextStyle(
                        fontFamily = Syne,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Ink000
                    )
                )
                Text(
                    text = "Sab Dekho →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand300,
                    modifier = Modifier.clickable { onNavigateToCustomers() }
                )
            }
        }

        // ===== TRANSACTIONS LIST =====
        if (db != null && db.transactions.isNotEmpty()) {
            val recentTxns = db.transactions.sortedByDescending { it.timestamp }.take(5)
            items(recentTxns) { txn ->
                val customer = db.customers.find { it.id == txn.customerId }
                val name = customer?.name ?: "Unknown"
                val isCredit = txn.type == "credit" || txn.type == "bill"
                
                TransactionItem(
                    name = name,
                    detail = if (isCredit) "Bill Created" else "Payment Received",
                    outstanding = (txn.amount * 100).toLong(),
                    isPositive = !isCredit,
                    time = "2h ago",
                    onClick = { onNavigateToCustomers() }
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(GrahbookRadius.lg))
                        .background(Ink700),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Ink400, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(GrahbookSpacing.xs))
                        Text(
                            text = "No transactions yet. Pehla bill banao!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ink300
                        )
                    }
                }
            }
        }
    }
}

// ===== QUICK ACTIONS CARD =====
@Composable
private fun QuickActionsCard(
    onCreateInvoice: () -> Unit,
    onAddCustomer: () -> Unit,
    onRecordPayment: () -> Unit,
    onSendReminder: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(GrahbookRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Ink700)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = GrahbookSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val actions = listOf(
                Triple("New Bill", Icons.Default.ReceiptLong, Brush.horizontalGradient(listOf(Saffron500, Saffron600))) to onCreateInvoice,
                Triple("Add Customer", Icons.Default.PersonAdd, Brush.horizontalGradient(listOf(Brand500, Brand600))) to onAddCustomer,
                Triple("Record Payment", Icons.Default.Payments, Brush.horizontalGradient(listOf(RupeeGreen, RupeeGreenDim))) to onRecordPayment,
                Triple("Send Reminder", Icons.Default.Notifications, Brush.horizontalGradient(listOf(Color(0xFF128C7E), Color(0xFF075E54)))) to onSendReminder
            )

            actions.forEach { (info, onClick) ->
                val (label, icon, brush) = info
                var isPressed by remember { mutableStateOf(false) }
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                    label = "action_scale"
                )

                Column(
                    modifier = Modifier
                        .scale(scale)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isPressed = true
                            isPressed = false
                            onClick()
                        }
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(brush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ===== WHATSAPP BOT ACTIVE CARD =====
@Composable
private fun WhatsAppBotActiveCard(onWhatsApp: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(GrahbookRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val dotScale by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_scale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF0A3D2E), Color(0xFF0D1F3C))
                    )
                )
                .clickable { onWhatsApp() }
                .padding(GrahbookSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .scale(dotScale)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RupeeGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bot Active",
                        style = MaterialTheme.typography.bodySmall,
                        color = RupeeGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AI Assistant\nReady hai",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ramesh ne 500 diya → ✓ Recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink200,
                    fontWeight = FontWeight.Normal
                )
            }
            
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = null,
                tint = Color(0xFF25D366),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ===== TRANSACTION ITEM =====
@Composable
private fun TransactionItem(
    name: String,
    detail: String,
    outstanding: Long,
    isPositive: Boolean,
    time: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomerAvatar(name = name, outstandingPaise = outstanding, size = 40.dp)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                color = Ink000,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = Ink300
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                amount = outstanding,
                type = if (isPositive) GrahbookAmountType.RECEIVED else GrahbookAmountType.OUTSTANDING,
                size = 17.sp
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodySmall,
                color = Ink400
            )
        }
    }
}
