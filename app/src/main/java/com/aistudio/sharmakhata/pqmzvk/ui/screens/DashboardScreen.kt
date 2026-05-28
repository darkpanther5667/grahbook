package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.text.SimpleDateFormat
import java.util.*

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

    val pullToRefreshState = rememberPullToRefreshState()
    val context = LocalContext.current

    // Get business info from db
    val businessName = remember(dbState) {
        when (dbState) {
            is UiState.Success -> (dbState as UiState.Success).data.shop?.name ?: "Grahbook"
            else -> "Grahbook"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(IndigoPrimary, IndigoDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Text("Grahbook", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.5).sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onNavigateToWebView) {
                        Icon(Icons.Default.Language, contentDescription = "Web Dashboard", tint = IndigoPrimary)
                    }
                    IconButton(onClick = { viewModel.fetchData(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondaryLight)
                    }
                }
            )
        }
    ) { padding ->

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = reportState is UiState.Loading,
            onRefresh = { viewModel.fetchData(context) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (reportState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = IndigoPrimary)
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
                            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(56.dp))
                            Text(
                                "Could not load dashboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                (reportState as UiState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryLight,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { viewModel.fetchData(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Retry") }
                        }
                    }
                }
                is UiState.Success -> {
                    val report = (reportState as UiState.Success).data
                    DashboardContent(
                        report = report,
                        businessName = businessName,
                        dbState = dbState,
                        onNavigateToCustomers = onNavigateToCustomers,
                        onCreateInvoice = onCreateInvoice,
                        onAddCustomer = onAddCustomer,
                        onRecordPayment = onRecordPayment,
                        onViewReports = onViewReports,
                        onSendReminder = onSendReminder,
                        onWhatsApp = onWhatsApp,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    report: DailyReport,
    businessName: String,
    dbState: UiState<FullDatabase>,
    onNavigateToCustomers: () -> Unit,
    onCreateInvoice: () -> Unit,
    onAddCustomer: () -> Unit,
    onRecordPayment: () -> Unit,
    onViewReports: () -> Unit,
    onSendReminder: () -> Unit,
    onWhatsApp: () -> Unit,
    viewModel: MainViewModel
) {
    val scrollState = rememberScrollState()
    val currentDate = remember { SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date()) }
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ===== TOP GREETING SECTION =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "$greeting,",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondaryLight,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = businessName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentDate,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
            )
        }

        // ===== QUICK STAT CARDS ROW =====
        // Today's Sale | Total Customers | Pending Amount | Total Collection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatGradientCard(
                label = "Today's Sale",
                value = FormatUtils.formatCurrency(report.billsTotal),
                icon = Icons.Default.TrendingUp,
                gradientColors = listOf(IndigoPrimary, IndigoDark),
                modifier = Modifier.weight(1f)
            )
            StatGradientCard(
                label = "Customers",
                value = when (dbState) {
                    is UiState.Success -> (dbState as UiState.Success).data.customers.size.toString()
                    else -> "0"
                },
                icon = Icons.Default.People,
                gradientColors = listOf(EmeraldSecondary, EmeraldDark),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatGradientCard(
                label = "Pending",
                value = FormatUtils.formatCurrency(
                    report.outstanding.sumOf { it.balance }
                ),
                icon = Icons.Default.PendingActions,
                gradientColors = listOf(AmberWarning, AmberDark),
                modifier = Modifier.weight(1f)
            )
            StatGradientCard(
                label = "Collection",
                value = FormatUtils.formatCurrency(report.paymentTotal),
                icon = Icons.Default.AccountBalanceWallet,
                gradientColors = listOf(OrangeDanger, Color(0xFFC2410C)),
                modifier = Modifier.weight(1f)
            )
        }

        // ===== QUICK ACTION GRID =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 3x2 Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        icon = Icons.Default.AddCircle,
                        label = "New Invoice",
                        gradient = listOf(IndigoPrimary, IndigoDark),
                        onClick = onCreateInvoice,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        icon = Icons.Default.PersonAdd,
                        label = "Add Customer",
                        gradient = listOf(EmeraldSecondary, EmeraldDark),
                        onClick = onAddCustomer,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        icon = Icons.Default.Payments,
                        label = "Record Payment",
                        gradient = listOf(AmberWarning, AmberDark),
                        onClick = onRecordPayment,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        icon = Icons.Default.BarChart,
                        label = "View Reports",
                        gradient = listOf(OrangeDanger, Color(0xFFC2410C)),
                        onClick = onViewReports,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        icon = Icons.Default.Notifications,
                        label = "Send Reminder",
                        gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
                        onClick = onSendReminder,
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        icon = Icons.Default.Message,
                        label = "WhatsApp",
                        gradient = listOf(Color(0xFF25D366), Color(0xFF128C7E)),
                        onClick = onWhatsApp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ===== RECENT ACTIVITY =====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (dbState) {
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    val recentTransactions = db.transactions
                        .sortedByDescending { it.timestamp }
                        .take(5)

                    if (recentTransactions.isEmpty() && db.bills.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = TextSecondaryLight,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    "No recent activity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondaryLight
                                )
                            }
                        }
                    } else {
                        val recentBills = db.bills
                            .sortedByDescending { it.createdAt }
                            .take(3)

                        recentBills.forEach { bill ->
                            val customer = db.customers.find { it.id == bill.customerId }
                            ActivityTimelineItem(
                                icon = Icons.Default.Receipt,
                                title = "Bill #${bill.id.take(8)}",
                                subtitle = customer?.name ?: "Unknown",
                                amount = FormatUtils.formatCurrency(bill.total),
                                amountColor = if (bill.status == "paid") SuccessGreen else AmberWarning,
                                time = FormatUtils.formatShortDate(bill.createdAt),
                                isLast = bill == recentBills.last() && recentTransactions.isEmpty()
                            )
                        }

                        recentTransactions.forEachIndexed { index, tx ->
                            val customer = db.customers.find { it.id == tx.customerId }
                            val isPayment = tx.type == "payment"
                            ActivityTimelineItem(
                                icon = if (isPayment) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                title = if (isPayment) "Payment received" else "Credit given",
                                subtitle = customer?.name ?: "Unknown",
                                amount = "${if (isPayment) "+" else "-"}${FormatUtils.formatCurrency(tx.amount)}",
                                amountColor = if (isPayment) SuccessGreen else ErrorRed,
                                time = FormatUtils.formatShortDate(tx.timestamp),
                                isLast = index == recentTransactions.lastIndex
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = IndigoPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // View All button
            OutlinedButton(
                onClick = onNavigateToCustomers,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IndigoPrimary)
            ) {
                Text("View All Activity", fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatGradientCard(
    label: String,
    value: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = gradientColors,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
                .padding(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    icon: ImageVector,
    label: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(colors = gradient)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun ActivityTimelineItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    amount: String,
    amountColor: Color,
    time: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(CardBorder)
                )
            } else {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$subtitle • $time",
                        fontSize = 11.sp,
                        color = TextSecondaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = amount,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }
        }
    }
}
