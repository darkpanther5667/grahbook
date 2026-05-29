package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.data.local.ItemEntity
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    items: List<ItemEntity>,
    onBack: () -> Unit,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onRefresh: () -> Unit = {},
    isLoading: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredItems by remember(items, searchQuery, selectedFilter) {
        derivedStateOf {
            items.filter { item ->
                val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (selectedFilter) {
                    "Low Stock" -> item.stock > 0 && item.stock <= item.lowStockAlert
                    "Out of Stock" -> item.stock == 0
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory", fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddItem,
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = FabShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        val pullToRefreshState = rememberPullToRefreshState()

        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                items.isEmpty() && !isLoading -> {
                    EmptyState(
                        icon = Icons.Outlined.Inventory2,
                        message = "No items yet",
                        description = "Add your first item to get started",
                        actionLabel = "Add Item",
                        onAction = onAddItem
                    )
                }
                else -> {
                    ItemsList(
                        items = filteredItems,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        onEditItem = onEditItem,
                        onDeleteItem = onDeleteItem,
                        totalItemCount = items.size
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemsList(
    items: List<ItemEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    onEditItem: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    totalItemCount: Int
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search items...", color = TextTertiaryLight) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = IndigoPrimary)
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
                    label = { Text("All ($totalItemCount)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextSecondaryLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "Low Stock",
                    onClick = { onFilterChange("Low Stock") },
                    label = { Text("Low Stock") },
                    leadingIcon = if (selectedFilter == "Low Stock") {
                        { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberWarning,
                        selectedLabelColor = Color.White,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = TextSecondaryLight
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            item {
                FilterChip(
                    selected = selectedFilter == "Out of Stock",
                    onClick = { onFilterChange("Out of Stock") },
                    label = { Text("Out of Stock") },
                    leadingIcon = if (selectedFilter == "Out of Stock") {
                        { Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp)) }
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
        }

        if (items.isEmpty()) {
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
                        Icons.Outlined.Inventory2,
                        contentDescription = null,
                        tint = TextTertiaryLight,
                        modifier = Modifier.size(IconSize.huge)
                    )
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No items matching \"$searchQuery\""
                        else "No ${selectedFilter.lowercase()} items",
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
                items(items, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        onEdit = { onEditItem(item.id) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemCard(
    item: ItemEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val stockColor = when {
        item.stock == 0 -> ErrorRed
        item.stock <= item.lowStockAlert -> AmberWarning
        else -> SuccessGreen
    }
    val stockLabel = when {
        item.stock == 0 -> "Out of Stock"
        item.stock <= item.lowStockAlert -> "Low Stock"
        else -> "In Stock"
    }
    val stockBg = when {
        item.stock == 0 -> ErrorRed.copy(alpha = 0.1f)
        item.stock <= item.lowStockAlert -> AmberWarning.copy(alpha = 0.1f)
        else -> SuccessGreen.copy(alpha = 0.1f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            // Item icon container
            Box(
                modifier = Modifier
                    .size(ComponentSize.iconContainerMedium)
                    .background(
                        IndigoPrimary.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(IconSize.small)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.medium))

            // Name + price
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xxsmall))
                Text(
                    text = "₹${FormatUtils.formatCurrency(item.price).removePrefix("₹")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Stock count + badge
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${item.stock}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = stockColor
                )
                Spacer(modifier = Modifier.height(Spacing.xxsmall))
                Box(
                    modifier = Modifier
                        .background(stockBg, BadgeShape)
                        .padding(horizontal = Spacing.small, vertical = Spacing.xxsmall)
                ) {
                    Text(
                        text = stockLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = stockColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.small))

            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit item",
                    tint = TextSecondaryLight,
                    modifier = Modifier.size(IconSize.small)
                )
            }

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = ErrorRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(IconSize.small)
                )
            }
        }
    }
}
