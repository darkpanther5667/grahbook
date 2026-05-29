package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppAvatar
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerListItem
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onCustomerClick: (String) -> Unit,
    onAddCustomer: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
) {
    val dbState by viewModel.dbState.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondaryLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCustomer,
                containerColor = StitchTeal,
                contentColor = Color.White,
                shape = FabShape
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add customer")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = dbState is UiState.Loading,
            onRefresh = { viewModel.fetchData(context) },
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
                            .padding(Spacing.large)
                    ) {
                        repeat(6) {
                            ShimmerListItem()
                            Spacer(modifier = Modifier.height(Spacing.small))
                        }
                    }
                }
                is UiState.Error -> {
                    EmptyState(
                        message = "Error loading customers",
                        description = (dbState as UiState.Error).message,
                        icon = Icons.Default.Error,
                        actionLabel = "Retry",
                        onAction = { viewModel.fetchData(context) }
                    )
                }
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    if (db.customers.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.Person,
                            message = "No customers yet",
                            description = "Add your first customer to get started",
                            actionLabel = "Add Customer",
                            onAction = onAddCustomer
                        )
                    } else {
                        CustomersList(
                            db = db,
                            onCustomerClick = onCustomerClick,
                            onAddCustomer = onAddCustomer,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            selectedFilter = selectedFilter,
                            onFilterChange = { selectedFilter = it }
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
    onCustomerClick: (String) -> Unit,
    onAddCustomer: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    // Calculate balances for filtering
    fun getBalance(customer: Customer): Double {
        val transactions = db.transactions.filter { it.customerId == customer.id }
        val bills = db.bills.filter { it.customerId == customer.id }
        val payments = transactions.filter { it.type == "payment" }.sumOf { it.amount }
        val credits = transactions.filter { it.type == "credit" }.sumOf { it.amount }
        val billTotal = bills.sumOf { it.total }
        return credits + billTotal - payments
    }

    val filteredCustomers = db.customers.filter { customer ->
        val matchesSearch = customer.name.contains(searchQuery, ignoreCase = true) ||
            (customer.phone?.contains(searchQuery, ignoreCase = true) == true)
        val balance = getBalance(customer)
        val matchesFilter = when (selectedFilter) {
            "With Outstanding" -> balance > 0
            "Paid" -> balance <= 0
            else -> true
        }
        matchesSearch && matchesFilter
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search by name or phone...", color = TextTertiaryLight) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = StitchTeal)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondaryLight)
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.small),
            shape = SearchBarShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = IndigoPrimary,
                unfocusedIndicatorColor = CardBorder,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = TextPrimaryLight,
                unfocusedTextColor = TextPrimaryLight
            ),
            singleLine = true
        )

        // Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.large, vertical = Spacing.small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            item {
                FilterChip(
                    selected = selectedFilter == "All",
                    onClick = { onFilterChange("All") },
                    label = { Text("All (${db.customers.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StitchTeal,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextSecondaryLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "With Outstanding",
                    onClick = { onFilterChange("With Outstanding") },
                    label = { Text("With Outstanding") },
                    leadingIcon = if (selectedFilter == "With Outstanding") {
                        { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorRed,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextSecondaryLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "Paid",
                    onClick = { onFilterChange("Paid") },
                    label = { Text("Paid") },
                    leadingIcon = if (selectedFilter == "Paid") {
                        { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SuccessGreen,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextSecondaryLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.xxxlarge),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Icon(
                        Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        tint = TextTertiaryLight,
                        modifier = Modifier.size(IconSize.huge)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No customers matching \"$searchQuery\""
                        else "No ${selectedFilter.lowercase()} customers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryLight,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { onSearchChange("") }) {
                            Text("Clear search", color = IndigoPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Spacing.large,
                    end = Spacing.large,
                    top = Spacing.small,
                    bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap)
            ) {
                itemsIndexed(filteredCustomers, key = { _, c -> c.id }) { index, customer ->
                    CustomerCard(
                        customer = customer,
                        db = db,
                        colorIndex = abs(customer.id.hashCode()) % AvatarColors.size,
                        onClick = { onCustomerClick(customer.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerCard(
    customer: Customer,
    db: FullDatabase,
    colorIndex: Int,
    onClick: () -> Unit
) {
    // Calculate balance
    val transactions = db.transactions.filter { it.customerId == customer.id }
    val bills = db.bills.filter { it.customerId == customer.id }
    val payments = transactions.filter { it.type == "payment" }.sumOf { it.amount }
    val credits = transactions.filter { it.type == "credit" }.sumOf { it.amount }
    val billTotal = bills.sumOf { it.total }
    val balance = credits + billTotal - payments

    val balanceColor = when {
        balance > 0 -> AmountDue
        balance < 0 -> AmountCredit
        else -> AmountNeutral
    }
    val balanceLabel = when {
        balance > 0 -> "Due"
        balance < 0 -> "You Get"
        else -> "Settled"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ListCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored avatar
            AppAvatar(
                name = customer.name,
                size = ComponentSize.avatarLarge,
                colorIndex = colorIndex
            )

            Spacer(modifier = Modifier.width(Spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = StitchTeal.copy(alpha = 0.6f),
                        modifier = Modifier.size(IconSize.xsmall)
                    )
                    Text(
                        text = customer.phone ?: "No phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Balance amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.formatCurrency(abs(balance)),
                    style = AmountSmallStyle,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Text(
                    text = balanceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = balanceColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(Spacing.small))

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = TextTertiaryLight,
                modifier = Modifier.size(IconSize.medium)
            )
        }
    }
}
