package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import com.aistudio.sharmakhata.pqmzvk.R
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
                TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            shape = RoundedCornerShape(GrahbookRadius.lg),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bills", fontFamily = Syne, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                .background(MaterialTheme.colorScheme.background)
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
                            .background(MaterialTheme.colorScheme.background)
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
                                    text = stringResource(R.string.all_bills)
                                )
                            }
                            item {
                                FilterChipItem(
                                    selected = selectedFilter == "Paid",
                                    onClick = { viewModel.setBillFilter("Paid") },
                                    text = stringResource(R.string.paid_bills)
                                )
                            }
                            item {
                                FilterChipItem(
                                    selected = selectedFilter == "Unpaid",
                                    onClick = { viewModel.setBillFilter("Unpaid") },
                                    text = stringResource(R.string.unpaid_bills)
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
                                    val localizedFilter = when (selectedFilter) {
                                        "Paid" -> stringResource(R.string.paid_bills).lowercase()
                                        "Unpaid" -> stringResource(R.string.unpaid_bills).lowercase()
                                        else -> ""
                                    }
                                    Text(
                                        text = if (allBillsForCustomer.isEmpty()) stringResource(R.string.no_bills_for_customer)
                                        else stringResource(R.string.no_filter_bills, localizedFilter),
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
                                        val customerName = db.customers.find { it.id == bill.customerId }?.name ?: stringResource(R.string.unknown)
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
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(GrahbookRadius.pill))
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = RoundedCornerShape(GrahbookRadius.pill)
            )
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
                color = textColor
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
        bill.status == "overdue" -> GrahbookStatus.LOW_STOCK
        else -> GrahbookStatus.UNPAID
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Visual left semantic stripe
            val stripeColor = when (status) {
                GrahbookStatus.PAID -> RupeeGreen
                GrahbookStatus.LOW_STOCK -> PendingAmber
                else -> DebtRed
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(stripeColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Top row: Invoice # and Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.invoice_hash, bill.id.take(8).uppercase()),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = FormatUtils.formatDate(bill.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = stringResource(R.string.total_amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        text = stringResource(R.string.items_count, bill.items.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.pdf_label), fontSize = 12.sp, fontFamily = Syne)
                    }

                    // WhatsApp Button
                    OutlinedButton(
                        onClick = onSendWhatsApp,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF25D366)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.whatsapp_label), fontSize = 12.sp, fontFamily = Syne)
                    }

                    // Mark Paid Button (only if unpaid)
                    if (!isPaid) {
                        Button(
                            onClick = onMarkPaid,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RupeeGreen)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.mark_paid_button), fontSize = 12.sp, fontFamily = Syne, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
