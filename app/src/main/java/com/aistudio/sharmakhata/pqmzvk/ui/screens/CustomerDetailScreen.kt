package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.model.Transaction
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppAvatar
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppDivider
import com.aistudio.sharmakhata.pqmzvk.ui.components.InfoRow
import com.aistudio.sharmakhata.pqmzvk.ui.components.StatusBadge
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    viewModel: MainViewModel,
    customerId: String,
    onBack: () -> Unit,
    onAddPayment: () -> Unit,
    onCreateBill: () -> Unit,
    onViewBills: () -> Unit,
    onViewLedger: () -> Unit,
) {
    val dbState by viewModel.dbState.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Show snackbar on operation state changes
    LaunchedEffect(operationState) {
        when (operationState) {
            is OperationState.Success -> {
                snackbarHostState.showSnackbar(
                    message = (operationState as OperationState.Success).message,
                    withDismissAction = true
                )
                viewModel.resetOperationState()
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar(
                    message = (operationState as OperationState.Error).message,
                    withDismissAction = true
                )
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customer Profile", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (dbState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = IndigoPrimary)
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Error: ${(dbState as UiState.Error).message}",
                            color = ErrorRed,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    val customer = db.customers.find { it.id == customerId }

                    if (customer == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextSecondaryLight)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Customer not found", color = TextSecondaryLight)
                            }
                        }
                    } else {
                        CustomerDetailContent(
                            customer = customer,
                            db = db,
                            onAddPayment = onAddPayment,
                            onCreateBill = onCreateBill,
                            onSendReminder = { viewModel.sendReminderOnWhatsApp(context, customer.id) },
                            onSendStatement = { viewModel.sendStatementOnWhatsApp(context, customer.id) },
                            onWhatsAppDirect = {
                                val payments = db.transactions.filter { it.customerId == customer.id && it.type == "payment" }.sumOf { it.amount }
                                val credits = db.transactions.filter { it.customerId == customer.id && it.type == "credit" }.sumOf { it.amount }
                                val billTotal = db.bills.filter { it.customerId == customer.id }.sumOf { it.total }
                                val outstanding = credits + billTotal - payments
                                val shopName = db.shop?.name ?: "Grahbook"
                                val msg = com.aistudio.sharmakhata.pqmzvk.util.WhatsAppUtils.buildReminderMessage(customer.name, outstanding, shopName)
                                com.aistudio.sharmakhata.pqmzvk.util.WhatsAppUtils.sendMessage(context, customer.phone ?: "", msg)
                            },
                            onViewBills = onViewBills,
                            onViewLedger = onViewLedger,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDetailContent(
    customer: Customer,
    db: FullDatabase,
    onAddPayment: () -> Unit,
    onCreateBill: () -> Unit,
    onSendReminder: () -> Unit,
    onSendStatement: () -> Unit,
    onWhatsAppDirect: () -> Unit,
    onViewBills: () -> Unit,
    onViewLedger: () -> Unit,
) {
    val transactions = db.transactions.filter { it.customerId == customer.id }
    val bills = db.bills.filter { it.customerId == customer.id }

    // Calculate outstanding balance
    val payments = transactions.filter { it.type == "payment" }.sumOf { it.amount }
    val credits = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val billTotal = bills.sumOf { it.total }
    val outstanding = credits + billTotal - payments

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ===== PROFILE HEADER (Vyapar-style) =====
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.large),
            shape = CardShape,
            elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.xlarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large avatar with gradient
                AppAvatar(
                    name = customer.name,
                    size = 72.dp,
                    colorIndex = abs(customer.id.hashCode()) % AvatarColors.size
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(IconSize.xsmall))
                    Text(
                        text = customer.phone ?: "No phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryLight
                    )
                }
            }
        }

        // ===== STATS ROW =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsCard(
                label = "Total Bills",
                value = bills.size.toString(),
                icon = Icons.Outlined.Receipt,
                color = IndigoPrimary,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                label = "Total Paid",
                value = FormatUtils.formatCurrency(payments),
                icon = Icons.Outlined.CheckCircle,
                color = SuccessGreen,
                modifier = Modifier.weight(1f)
            )
            StatsCard(
                label = "Outstanding",
                value = bills.filter { it.status != "paid" }.size.toString(),
                icon = Icons.Outlined.PendingActions,
                color = AmberWarning,
                modifier = Modifier.weight(1f)
            )
        }

        // ===== BALANCE CARD =====
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Outstanding Balance",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondaryLight,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = FormatUtils.formatCurrency(outstanding),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (outstanding > 0) ErrorRed else SuccessGreen
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (outstanding > 0) "Amount Due" else "No Outstanding Balance",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outstanding > 0) ErrorRed else SuccessGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ===== QUICK ACTION ICONS ROW (Vyapar-style) =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionIconButton(
                icon = Icons.Default.Payments,
                label = "Add Payment",
                color = SuccessGreen,
                onClick = onAddPayment
            )
            QuickActionIconButton(
                icon = Icons.Default.AddShoppingCart,
                label = "Create Bill",
                color = IndigoPrimary,
                onClick = onCreateBill
            )
            QuickActionIconButton(
                icon = Icons.Default.Notifications,
                label = "Reminder",
                color = AmberWarning,
                onClick = onSendReminder
            )
            QuickActionIconButton(
                icon = Icons.Default.Description,
                label = "Statement",
                color = Color(0xFF8B5CF6),
                onClick = onSendStatement
            )
            QuickActionIconButton(
                icon = Icons.Default.Chat,
                label = "WhatsApp",
                color = Color(0xFF25D366),
                onClick = onWhatsAppDirect
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== TAB ROW: Bills | Ledger | Details =====
        var selectedTab by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = IndigoPrimary,
                divider = { HorizontalDivider(color = CardBorder, thickness = 1.dp) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Bills", style = TabLabelStyle)
                        }
                    },
                    selectedContentColor = IndigoPrimary,
                    unselectedContentColor = TextSecondaryLight
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Ledger", style = TabLabelStyle)
                        }
                    },
                    selectedContentColor = IndigoPrimary,
                    unselectedContentColor = TextSecondaryLight
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Details", style = TabLabelStyle)
                        }
                    },
                    selectedContentColor = IndigoPrimary,
                    unselectedContentColor = TextSecondaryLight
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                // ===== BILLS TAB =====
                0 -> {
                    val sortedBills = bills.sortedByDescending { it.createdAt }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Bills (${bills.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onViewBills) {
                                    Text("View All", color = IndigoPrimary, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (sortedBills.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxlarge),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                                ) {
                                    Icon(Icons.Outlined.Receipt, contentDescription = null, tint = TextTertiaryLight, modifier = Modifier.size(IconSize.xlarge))
                                    Text("No bills yet", color = TextSecondaryLight, style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                sortedBills.take(5).forEachIndexed { index, bill ->
                                    BillRowItem(bill = bill, index = index, isLast = index == sortedBills.take(5).lastIndex)
                                }
                            }
                        }
                    }
                }

                // ===== LEDGER TAB =====
                1 -> {
                    val sortedTransactions = transactions.sortedByDescending { it.timestamp }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Transactions",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onViewLedger) {
                                    Text("View All", color = IndigoPrimary, fontWeight = FontWeight.Medium)
                                }
                            }

                            if (sortedTransactions.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxlarge),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                                ) {
                                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextTertiaryLight, modifier = Modifier.size(IconSize.xlarge))
                                    Text("No transactions yet", color = TextSecondaryLight, style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                sortedTransactions.take(5).forEachIndexed { index, tx ->
                                    TransactionTimelineItem(
                                        transaction = tx,
                                        isLast = index == sortedTransactions.take(5).lastIndex
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== DETAILS TAB =====
                2 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.flat)
                    ) {
                        Column(modifier = Modifier.padding(Spacing.xlarge)) {
                            InfoRow(label = "Phone", value = customer.phone ?: "Not set")
                            AppDivider()
                            InfoRow(label = "Customer ID", value = customer.id.take(12))
                            AppDivider()
                            InfoRow(label = "Total Bills", value = bills.size.toString())
                            AppDivider()
                            val paidBills = bills.count { it.status == "paid" }
                            InfoRow(label = "Paid Bills", value = paidBills.toString())
                            AppDivider()
                            InfoRow(
                                label = "Outstanding Amount",
                                value = FormatUtils.formatCurrency(outstanding),
                                valueColor = if (outstanding > 0) ErrorRed else SuccessGreen,
                                valueStyle = AmountMediumStyle
                            )
                            if (customer.createdAt != null) {
                                AppDivider()
                                InfoRow(label = "Customer Since", value = FormatUtils.formatDate(customer.createdAt))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BillRowItem(
    bill: com.aistudio.sharmakhata.pqmzvk.data.model.Bill,
    index: Int,
    isLast: Boolean
) {
    val isPaid = bill.status == "paid"
    val statusColor = if (isPaid) SuccessGreen else ErrorRed
    val statusLabel = if (isPaid) "Paid" else "Unpaid"
    val statusBg = if (isPaid) BadgePaidBg else BadgeUnpaidBg
    val statusText = if (isPaid) BadgePaidText else BadgeUnpaidText

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.iconContainerSmall)
                    .clip(ActionIconShape)
                    .background(statusBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(IconSize.xsmall)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bill #${bill.id.take(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = FormatUtils.formatDate(bill.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.formatCurrency(bill.total),
                    style = AmountSmallStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusBadge(
                    label = statusLabel,
                    bgColor = statusBg,
                    textColor = statusText
                )
            }
        }
        if (!isLast) {
            HorizontalDivider(color = DividerColor.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

@Composable
fun StatsCard(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = TextSecondaryLight,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickActionIconButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TransactionTimelineItem(
    transaction: Transaction,
    isLast: Boolean
) {
    val isPayment = transaction.type == "payment"
    val color = if (isPayment) SuccessGreen else ErrorRed
    val sign = if (isPayment) "+" else "-"
    val label = if (isPayment) "Payment Received" else "Credit Given"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(CardBorder)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${FormatUtils.formatDateTime(transaction.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
                if (!transaction.note.isNullOrBlank()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                }
            }
            Text(
                text = "$sign${FormatUtils.formatCurrency(transaction.amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
