package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.data.model.Bill
import com.aistudio.sharmakhata.pqmzvk.data.model.Customer
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppAvatar
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerLoading
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onCustomerClick: (String) -> Unit,
    onBillClick: (String, String) -> Unit
) {
    val dbState by viewModel.dbState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(ComponentSize.textFieldHeight),
                    placeholder = { Text("Search customers and bills...", color = TextTertiaryLight) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Slate400)
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate500)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = SearchBarShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IndigoPrimary,
                        cursorColor = IndigoPrimary
                    )
                )
            }

            when (dbState) {
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ShimmerLoading()
                }
                is UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        message = "Error: ${(dbState as UiState.Error).message}",
                        icon = Icons.Default.Search
                    )
                }
                is UiState.Success -> {
                    val db = (dbState as UiState.Success).data
                    val customers = db.customers
                    val bills = db.bills

                    val filteredCustomers = if (searchQuery.isNotEmpty()) {
                        customers.filter { customer ->
                            customer.name.contains(searchQuery, ignoreCase = true) ||
                            (customer.phone?.contains(searchQuery, ignoreCase = true) == true)
                        }
                    } else emptyList()

                    val filteredBills = if (searchQuery.isNotEmpty()) {
                        bills.filter { bill ->
                            val customer = customers.find { it.id == bill.customerId }
                            customer?.name?.contains(searchQuery, ignoreCase = true) == true ||
                            bill.id.contains(searchQuery, ignoreCase = true)
                        }
                    } else emptyList()

                    if (searchQuery.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                message = "Type to search customers and bills",
                                icon = Icons.Default.Search
                            )
                        }
                    } else if (filteredCustomers.isEmpty() && filteredBills.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyState(
                                message = "No results found for \"$searchQuery\"",
                                icon = Icons.Default.Search
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = Spacing.screenPadding,
                                vertical = Spacing.small
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap)
                        ) {
                            if (filteredCustomers.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "CUSTOMERS",
                                        style = SectionOverlineStyle,
                                        modifier = Modifier.padding(
                                            top = Spacing.small,
                                            bottom = Spacing.medium
                                        )
                                    )
                                }
                                items(filteredCustomers) { customer ->
                                    CustomerSearchResult(
                                        customer = customer,
                                        onClick = { onCustomerClick(customer.id) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(Spacing.sectionGap)) }
                            }

                            if (filteredBills.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "BILLS",
                                        style = SectionOverlineStyle,
                                        modifier = Modifier.padding(
                                            top = Spacing.small,
                                            bottom = Spacing.medium
                                        )
                                    )
                                }
                                items(filteredBills) { bill ->
                                    val customer = customers.find { it.id == bill.customerId }
                                    BillSearchResult(
                                        bill = bill,
                                        customerName = customer?.name ?: "Unknown",
                                        customer = customer,
                                        onClick = { onBillClick(bill.customerId, bill.id) }
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

@Composable
private fun CustomerSearchResult(
    customer: Customer,
    onClick: () -> Unit
) {
    val colorIndex = abs(customer.id.hashCode()) % AvatarColors.size

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
            AppAvatar(
                name = customer.name,
                size = ComponentSize.avatarMedium,
                colorIndex = colorIndex
            )
            Spacer(modifier = Modifier.width(Spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = customer.phone ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
            }
        }
    }
}

@Composable
private fun BillSearchResult(
    bill: Bill,
    customerName: String,
    customer: Customer?,
    onClick: () -> Unit
) {
    val isPaid = bill.status == "paid"
    val statusColor = if (isPaid) BillPaid else BillUnpaid
    val statusBg = if (isPaid) EmeraldContainer else ErrorContainer

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
            // Receipt icon in colored container
            Box(
                modifier = Modifier
                    .size(ComponentSize.iconContainerMedium)
                    .clip(ActionIconShape)
                    .background(statusBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(IconSize.small)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Bill #${bill.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryLight
                )
            }
            // Bill total
            Text(
                text = FormatUtils.formatCurrency(bill.total),
                style = AmountSmallStyle,
                color = if (isPaid) AmountCredit else AmountDue
            )
        }
    }
}
