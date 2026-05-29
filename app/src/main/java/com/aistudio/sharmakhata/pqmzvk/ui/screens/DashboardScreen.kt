package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
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
    val isOffline by viewModel.isOffline.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    val shopName = remember(dbState) {
        when (dbState) {
            is UiState.Success -> (dbState as UiState.Success).data.shop?.name ?: "WhatsApp Billing"
            else -> "WhatsApp Billing"
        }
    }

    val ownerName = remember(dbState) {
        when (dbState) {
            is UiState.Success -> (dbState as UiState.Success).data.shop?.owner ?: ""
            else -> ""
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // WhatsApp Green gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(WhatsAppPrimary, WhatsAppPrimaryDark),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
                    )
                )
        )

        Scaffold(
            topBar = {
                Column {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = shopName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = ownerName.ifBlank { "Your Business" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateToWebView) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = "Web Dashboard",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (shopName.firstOrNull()?.uppercase() ?: "W").toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->

            PullToRefreshBox(
                state = pullToRefreshState,
                isRefreshing = reportState is UiState.Loading,
                onRefresh = { viewModel.fetchData(context) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (reportState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = WhatsAppPrimary)
                        }
                    }
                    is UiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                                Text(
                                    "Could not load dashboard",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    (reportState as UiState.Error).message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StitchTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { viewModel.fetchData(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Retry") }
                            }
                        }
                    }
                    is UiState.Success -> {
                        val report = (reportState as UiState.Success).data
                        DashboardContent(
                            report = report,
                            shopName = shopName,
                            dbState = dbState,
                            isOffline = isOffline,
                            pendingCount = pendingCount,
                            onNavigateToCustomers = onNavigateToCustomers,
                            onCreateInvoice = onCreateInvoice,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    report: DailyReport,
    shopName: String,
    dbState: UiState<FullDatabase>,
    isOffline: Boolean,
    pendingCount: Int,
    onNavigateToCustomers: () -> Unit,
    onCreateInvoice: () -> Unit,
    viewModel: MainViewModel
) {
    val totalOutstanding = report.outstanding.sumOf { it.balance }
    val pendingBillCount = report.outstanding.size
    val customerCount = when (dbState) {
        is UiState.Success -> (dbState as UiState.Success).data.customers.size
        else -> 0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp)
    ) {
        // Offline banner
        if (isOffline) {
            item {
                Surface(
                    color = AmberWarning.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "You're offline" + if (pendingCount > 0) " — $pendingCount changes pending" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // ===== STAT CARDS (2x2 grid) =====
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total Outstanding",
                        value = FormatUtils.formatCurrency(totalOutstanding),
                        icon = Icons.Outlined.AccountBalanceWallet,
                        iconBg = GradientOrange,
                        valueColor = OrangeDanger,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Today Collection",
                        value = FormatUtils.formatCurrency(report.paymentTotal),
                        icon = Icons.Outlined.TrendingUp,
                        iconBg = GradientWhatsApp,
                        valueColor = WhatsAppPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Pending Invoices",
                        value = pendingBillCount.toString(),
                        icon = Icons.Outlined.PendingActions,
                        iconBg = GradientAmber,
                        valueColor = AmberWarning,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Total Clients",
                        value = customerCount.toString(),
                        icon = Icons.Outlined.People,
                        iconBg = GradientSky,
                        valueColor = AccentBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ===== CREATE INVOICE BUTTON =====
        item {
            Button(
                onClick = onCreateInvoice,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppPrimary,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Invoice", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // ===== TODAY'S COLLECTION BANNER =====
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WhatsAppPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Today's Collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FormatUtils.formatCurrency(report.paymentTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "${report.billsCount} bill${if (report.billsCount != 1) "s" else ""} created today",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // ===== TRANSACTIONS SECTION =====
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT TRANSACTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextTertiary,
                    letterSpacing = 0.5.sp
                )
                TextButton(onClick = onNavigateToCustomers) {
                    Text(
                        "View All",
                        color = WhatsAppPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Transactions list
        when (dbState) {
            is UiState.Success -> {
                val db = (dbState as UiState.Success).data
                val recentBills = db.bills
                    .sortedByDescending { it.createdAt }
                    .take(5)
                val recentTransactions = db.transactions
                    .sortedByDescending { it.timestamp }
                    .take(5)

                if (recentBills.isEmpty() && recentTransactions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = StitchSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = StitchTextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    "No recent activity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StitchTextSecondary
                                )
                                Text(
                                    "Bills and payments will appear here",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StitchTextTertiary
                                )
                            }
                        }
                    }
                } else {
                    val items = mutableListOf<TransactionItem>()
                    recentBills.forEach { bill ->
                        val customer = db.customers.find { it.id == bill.customerId }
                        val name = customer?.name ?: "Unknown"
                        val colorIndex = (customer?.id?.hashCode()?.mod(AvatarColors.size))?.let { kotlin.math.abs(it) } ?: 0
                        val statusColor = when (bill.status) {
                            "paid" -> WhatsAppPrimary
                            "unpaid" -> OrangeDanger
                            else -> AmberWarning
                        }
                        val statusLabel = when (bill.status) {
                            "paid" -> "Paid"
                            "unpaid" -> "Unpaid"
                            else -> "Pending"
                        }
                        items.add(
                            TransactionItem(
                                name = name,
                                subtitle = "Invoice #${bill.id.take(8).uppercase()}",
                                amount = FormatUtils.formatCurrency(bill.total),
                                amountColor = statusColor,
                                statusLabel = statusLabel,
                                statusColor = statusColor,
                                time = FormatUtils.formatShortDate(bill.createdAt),
                                colorIndex = colorIndex
                            )
                        )
                    }
                    recentTransactions.forEach { tx ->
                        val customer = db.customers.find { it.id == tx.customerId }
                        val name = customer?.name ?: "Unknown"
                        val isPayment = tx.type == "payment"
                        val colorIndex = (customer?.id?.hashCode()?.mod(AvatarColors.size))?.let { kotlin.math.abs(it) } ?: 0
                        items.add(
                            TransactionItem(
                                name = name,
                                subtitle = if (isPayment) "Payment received" else "Credit given",
                                amount = "${if (isPayment) "+" else "-"}${FormatUtils.formatCurrency(tx.amount)}",
                                amountColor = if (isPayment) WhatsAppPrimary else ErrorRed,
                                statusLabel = if (isPayment) "Received" else "Due",
                                statusColor = if (isPayment) WhatsAppPrimary else ErrorRed,
                                time = FormatUtils.formatShortDate(tx.timestamp),
                                colorIndex = colorIndex
                            )
                        )
                    }
                    val sortedItems = items.take(6)

                    items(sortedItems) { item ->
                        TransactionRow(item)
                    }
                }
            }
            else -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = WhatsAppPrimary)
                    }
                }
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

// ===== STAT CARD (Stitch design) =====
@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    iconBg: List<Color>,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = StitchSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(iconBg)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = StitchTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 20.sp
            )
        }
    }
}

// ===== TRANSACTION ITEM DATA =====
private data class TransactionItem(
    val name: String,
    val subtitle: String,
    val amount: String,
    val amountColor: Color,
    val statusLabel: String,
    val statusColor: Color,
    val time: String,
    val colorIndex: Int
)

@Composable
private fun TransactionRow(item: TransactionItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StitchSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            val gradient = AvatarColors[item.colorIndex % AvatarColors.size]
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors = gradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name.firstOrNull()?.uppercase()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextTertiary
                    )
                    Text(
                        text = item.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = StitchTextTertiary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = item.amount,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = item.amountColor
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(item.statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = item.statusColor,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
