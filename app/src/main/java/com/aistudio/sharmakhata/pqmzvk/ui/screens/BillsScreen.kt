package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.paging.compose.collectAsLazyPagingItems
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.model.Bill
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerLoading
import com.aistudio.sharmakhata.pqmzvk.ui.components.AmountText
import com.aistudio.sharmakhata.pqmzvk.ui.components.GrahbookAmountType
import com.aistudio.sharmakhata.pqmzvk.ui.components.StatusBadge
import com.aistudio.sharmakhata.pqmzvk.ui.components.GrahbookStatus
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.BillingViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    viewModel: BillingViewModel,
    customerId: String,
    onBack: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val dbState by viewModel.dbState.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val pagedBills = remember(customerId) {
        viewModel.billsPagingData(customerId)
    }.collectAsLazyPagingItems()
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(operationState) {
        when (operationState) {
            is OperationState.Success -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Success).message)
                viewModel.resetOperationState()
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Error).message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    // Confirmation Dialog in custom Grahbook style
    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Mark as Paid?", color = Ink000, fontFamily = Syne, fontWeight = FontWeight.Bold) },
            text = { Text("This will mark the bill as paid. Continue?", color = Ink100) },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog?.let { viewModel.markBillPaid(context, it) }
                        showConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RupeeGreen),
                    shape = RoundedCornerShape(GrahbookRadius.pill)
                ) { Text("Yes, Mark Paid", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel", color = Ink300) }
            },
            shape = RoundedCornerShape(GrahbookRadius.lg),
            containerColor = Ink700
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills", fontFamily = Syne, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Ink000)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink800,
                    titleContentColor = Ink000
                )
            )
        },
        containerColor = Ink800,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = dbState is UiState.Loading,
            onRefresh = { scope.launch { com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.LiveSyncManager.forceRefresh() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Ink800)
        ) {
            when (dbState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { ShimmerLoading() }
                }
                is UiState.Error -> {
                    EmptyState(
                        message = "Error loading bills",
                        description = (dbState as UiState.Error).message,
                        icon = Icons.Default.Error,
                        actionLabel = "Retry",
                        onAction = { scope.launch { com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.LiveSyncManager.forceRefresh() } }
                    )
                }
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    val selectedFilter by viewModel.billFilter.collectAsState()
                    val allBillsForCustomer = db.bills.filter { it.customerId == customerId }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Ink800)
                    ) {
                        // Filter Chips Row
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                FilterChipItem(
                                    selected = selectedFilter == "All",
                                    onClick = { viewModel.setBillFilter("All") },
                                    text = "Sab"
                                )
                            }
                            item {
                                FilterChipItem(
                                    selected = selectedFilter == "Paid",
                                    onClick = { viewModel.setBillFilter("Paid") },
                                    text = "Paid"
                                )
                            }
                            item {
                                FilterChipItem(
                                    selected = selectedFilter == "Unpaid",
                                    onClick = { viewModel.setBillFilter("Unpaid") },
                                    text = "Unpaid"
                                )
                            }
                        }

                        if (pagedBills.itemCount == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.ReceiptLong,
                                        contentDescription = null,
                                        tint = Ink400,
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Text(
                                        text = if (allBillsForCustomer.isEmpty()) "No bills found for this customer"
                                        else "No ${selectedFilter.lowercase()} bills",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Ink100,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(count = pagedBills.itemCount, key = { index -> pagedBills[index]?.id ?: index }) { index ->
                                    val bill = pagedBills[index]
                                    if (bill != null) {
                                        val customerName = db.customers.find { it.id == bill.customerId }?.name ?: "Unknown"
                                        InvoiceCard(
                                            bill = bill,
                                            customerName = customerName,
                                            onSendWhatsApp = { viewModel.sendInvoiceOnWhatsApp(context, bill.id) },
                                            onMarkPaid = { showConfirmDialog = bill.id },
                                            onOpenPdf = { onOpenPdf(bill.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    selected: Boolean,
    onClick: () -> Unit,
    text: String
) {
    val brush = if (selected) {
        Brush.horizontalGradient(listOf(Brand500, Brand600))
    } else {
        Brush.horizontalGradient(listOf(Ink600, Ink600))
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(GrahbookRadius.pill))
            .background(brush)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = Syne,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (selected) Color.White else Ink200
            )
        )
    }
}

@Composable
private fun InvoiceCard(
    bill: Bill,
    customerName: String,
    onSendWhatsApp: () -> Unit,
    onMarkPaid: () -> Unit,
    onOpenPdf: () -> Unit,
) {
    val isPaid = bill.status == "paid"
    val status = when {
        isPaid -> GrahbookStatus.PAID
        bill.status == "overdue" -> GrahbookStatus.LOW_STOCK // low stock has same warning amber color
        else -> GrahbookStatus.UNPAID
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GrahbookRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Ink700)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: Invoice # and Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Invoice #${bill.id.take(8).uppercase()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink000
                    )
                    Text(
                        text = FormatUtils.formatDate(bill.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink300
                    )
                }

                StatusBadge(status = status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Customer name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Brand300,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink200,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink300
                )
                AmountText(
                    amount = (bill.total * 100).toLong(),
                    type = if (isPaid) GrahbookAmountType.RECEIVED else GrahbookAmountType.OUTSTANDING,
                    size = 20.sp
                )
            }

            // Items count if any
            if (bill.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${bill.items.size} item(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink300
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Ink600)
            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // PDF Button
                OutlinedButton(
                    onClick = onOpenPdf,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(GrahbookRadius.md),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand300),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Ink500)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF", fontSize = 12.sp, fontFamily = Syne)
                }

                // WhatsApp Button
                OutlinedButton(
                    onClick = onSendWhatsApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(GrahbookRadius.md),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Ink500)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", fontSize = 12.sp, fontFamily = Syne)
                }

                // Mark Paid Button (only if unpaid)
                if (!isPaid) {
                    Button(
                        onClick = onMarkPaid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(GrahbookRadius.md),
                        colors = ButtonDefaults.buttonColors(containerColor = RupeeGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paid", fontSize = 12.sp, fontFamily = Syne, color = Color.White)
                    }
                }
            }
        }
    }
}
