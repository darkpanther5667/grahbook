package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.components.HamburgerAppBar
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerListItem
import com.aistudio.sharmakhata.pqmzvk.ui.components.AmountText
import com.aistudio.sharmakhata.pqmzvk.ui.components.GrahbookAmountType
import com.aistudio.sharmakhata.pqmzvk.ui.components.CustomerAvatar
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.CustomerViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.LiveSyncManager
import androidx.compose.ui.res.stringResource
import com.aistudio.sharmakhata.pqmzvk.R
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: CustomerViewModel,
    onMenuClick: () -> Unit = {},
    shopInitial: String = "S",
    onBack: () -> Unit,
    onCustomerClick: (String) -> Unit,
    onAddCustomer: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
) {
    val dbState by viewModel.dbState.collectAsState()
    val pagedCustomers = viewModel.customersPagingData.collectAsLazyPagingItems()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            HamburgerAppBar(
                title = stringResource(R.string.customers_title),
                onMenuClick = onMenuClick,
                shopInitial = shopInitial,
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            val primaryColor = MaterialTheme.colorScheme.primary
            val primaryVariant = if (primaryColor == Brand500) Brand700 else Saffron600
            Box(
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(GrahbookRadius.md))
                    .background(Brush.horizontalGradient(listOf(primaryColor, primaryVariant)))
                    .clickable { onAddCustomer() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                    Text("+ Naya Customer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = dbState is UiState.Loading,
            onRefresh = { scope.launch { LiveSyncManager.forceRefresh() } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (dbState) {
                is UiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        repeat(6) {
                            ShimmerListItem()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    EmptyState(
                        message = stringResource(R.string.error_loading_customers),
                        description = (dbState as UiState.Error).message,
                        icon = Icons.Default.Error,
                        actionLabel = stringResource(R.string.retry),
                        onAction = { scope.launch { LiveSyncManager.forceRefresh() } }
                    )
                }
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    if (db.customers.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Person,
                            message = stringResource(R.string.no_customers_yet),
                            description = stringResource(R.string.add_first_customer),
                            actionLabel = stringResource(R.string.add_customer_action),
                            onAction = onAddCustomer
                        )
                    } else {
                        CustomersList(
                            db = db,
                            pagedCustomers = pagedCustomers,
                            onCustomerClick = onCustomerClick,
                            onAddCustomer = onAddCustomer,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomersList(
    db: FullDatabase,
    pagedCustomers: LazyPagingItems<Customer>,
    onCustomerClick: (String) -> Unit,
    onAddCustomer: () -> Unit,
    viewModel: CustomerViewModel
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.filter.collectAsState()

    // Calculate Total Outstanding
    val totalOutstanding = remember(db) {
        db.customers.sumOf { customer ->
            val transactions = db.transactions.filter { it.customerId == customer.id }
            val bills = db.bills.filter { it.customerId == customer.id }
            val payments = transactions.filter { it.type == "payment" }.sumOf { it.amount }
            val credits = transactions.filter { it.type == "credit" }.sumOf { it.amount }
            val billTotal = bills.sumOf { it.total }
            credits + billTotal - payments
        }.coerceAtLeast(0.0)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pill Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Naam ya phone number...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontFamily = DMSans) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            shape = RoundedCornerShape(GrahbookRadius.pill),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true
        )

        // Sticky Header Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink900)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.outstanding_label),
                style = MaterialTheme.typography.bodySmall,
                color = Ink300,
                fontWeight = FontWeight.SemiBold
            )
            AmountText(
                amount = (totalOutstanding * 100).toLong(),
                type = GrahbookAmountType.OUTSTANDING,
                size = 20.sp
            )
        }

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChipItem(
                    selected = selectedFilter == "All",
                    onClick = { viewModel.setFilter("All") },
                    text = stringResource(R.string.all_with_count, db.customers.size)
                )
            }
            item {
                FilterChipItem(
                    selected = selectedFilter == "With Outstanding",
                    onClick = { viewModel.setFilter("With Outstanding") },
                    text = stringResource(R.string.with_outstanding)
                )
            }
            item {
                FilterChipItem(
                    selected = selectedFilter == "Paid",
                    onClick = { viewModel.setFilter("Paid") },
                    text = stringResource(R.string.paid_filter)
                )
            }
        }

        if (pagedCustomers.itemCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        tint = Ink400,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) stringResource(R.string.no_customers_matching, searchQuery)
                        else {
                            val localizedFilter = when (selectedFilter) {
                                "With Outstanding" -> stringResource(R.string.with_outstanding).lowercase()
                                "Paid" -> stringResource(R.string.paid_filter).lowercase()
                                else -> ""
                            }
                            stringResource(R.string.no_filter_customers, localizedFilter)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink200,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { viewModel.setSearchQuery("") }) {
                            Text(stringResource(R.string.clear_search), color = Brand300, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 8.dp,
                    end = 20.dp,
                    bottom = 90.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(count = pagedCustomers.itemCount, key = { index -> pagedCustomers[index]?.id ?: index }) { index ->
                    val customer = pagedCustomers[index]
                    if (customer != null) {
                        CustomerItemRow(
                            customer = customer,
                            db = db,
                            onClick = { onCustomerClick(customer.id) }
                        )
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
            .height(36.dp)
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
private fun CustomerItemRow(
    customer: Customer,
    db: FullDatabase,
    onClick: () -> Unit
) {
    val transactions = db.transactions.filter { it.customerId == customer.id }
    val bills = db.bills.filter { it.customerId == customer.id }
    val payments = transactions.filter { it.type == "payment" }.sumOf { it.amount }
    val credits = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val billTotal = bills.sumOf { it.total }
    val balance = credits + billTotal - payments

    val latestTimestamp = remember(transactions, bills, customer) {
        val txTime = transactions.mapNotNull { it.timestamp }.maxOrNull()
        val billTime = bills.mapNotNull { it.createdAt }.maxOrNull()
        val cTime = customer.createdAt
        listOfNotNull(txTime, billTime, cTime).maxOrNull()
    }
    val relativeTime = remember(latestTimestamp) {
        FormatUtils.getRelativeTimeSpan(latestTimestamp)
    }

    val balanceType = when {
        balance > 0 -> GrahbookAmountType.OUTSTANDING
        balance < 0 -> GrahbookAmountType.RECEIVED
        else -> GrahbookAmountType.NEUTRAL
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomerAvatar(
                name = customer.name,
                outstandingPaise = (balance * 100).toLong(),
                size = 44.dp
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = customer.phone ?: stringResource(R.string.no_phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AmountText(
                    amount = (abs(balance) * 100).toLong(),
                    type = balanceType,
                    size = 16.sp
                )
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
