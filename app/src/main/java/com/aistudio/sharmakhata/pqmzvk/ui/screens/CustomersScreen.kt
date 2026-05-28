package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerListItem
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

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
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
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
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
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
                            .padding(16.dp)
                    ) {
                        repeat(6) {
                            ShimmerListItem()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                        Text(
                            "Error: ${(dbState as UiState.Error).message}",
                            color = ErrorRed,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.fetchData(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) { Text("Retry") }
                    }
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
                        CustomersList(db, onCustomerClick, onAddCustomer, searchQuery) { searchQuery = it }
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
    onSearchChange: (String) -> Unit
) {
    val customers = db.customers.filter { customer ->
        customer.name.contains(searchQuery, ignoreCase = true) ||
        (customer.phone?.contains(searchQuery, ignoreCase = true) == true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search customers...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = IndigoPrimary)
            },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = IndigoPrimary,
                unfocusedIndicatorColor = CardBorder,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            singleLine = true
        )

        if (customers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.PersonSearch,
                        contentDescription = null,
                        tint = TextSecondaryLight,
                        modifier = Modifier.size(72.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No customers found matching \"$searchQuery\"" else "No customers yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotEmpty()) {
                        TextButton(onClick = { onSearchChange("") }) {
                            Text("Clear search", color = IndigoPrimary)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(customers, key = { it.id }) { customer ->
                    CustomerCard(
                        customer = customer,
                        db = db,
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
        balance > 0 -> ErrorRed
        balance < 0 -> SuccessGreen
        else -> TextSecondaryLight
    }
    val balancePrefix = when {
        balance > 0 -> "₹"
        balance < 0 -> "+₹"
        else -> "₹"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar - first letter in gradient circle
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(IndigoPrimary, IndigoDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = customer.name.firstOrNull()?.uppercase()?.toString() ?: "C",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = IndigoPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
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
                    text = "${balancePrefix}${FormatUtils.formatCurrency(kotlin.math.abs(balance))}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Text(
                    text = when {
                        balance > 0 -> "Due"
                        balance < 0 -> "You Get"
                        else -> "Settled"
                    },
                    fontSize = 10.sp,
                    color = balanceColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View details",
                tint = TextSecondaryLight,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
