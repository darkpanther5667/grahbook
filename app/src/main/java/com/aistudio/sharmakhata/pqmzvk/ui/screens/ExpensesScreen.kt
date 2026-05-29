package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.local.ExpenseEntity
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val expenseCategories = listOf(
    "Rent" to Icons.Default.Home,
    "Utilities" to Icons.Default.Bolt,
    "Salary" to Icons.Default.Groups,
    "Inventory" to Icons.Default.Inventory2,
    "Transport" to Icons.Default.LocalShipping,
    "Marketing" to Icons.Default.Campaign,
    "Food" to Icons.Default.Restaurant,
    "Office Supplies" to Icons.Default.Build,
    "Other" to Icons.Default.MoreHoriz
)

private fun categoryIcon(category: String): ImageVector {
    return expenseCategories.firstOrNull { it.first == category }?.second ?: Icons.Default.MoreHoriz
}

private fun formatTimestamp(epochMillis: Long): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        sdf.format(Date(epochMillis))
    } catch (_: Exception) {
        epochMillis.toString()
    }
}

private fun isToday(epochMillis: Long): Boolean {
    val cal = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayEnd = todayStart + 86_400_000 // 24 hours
    return epochMillis in todayStart until todayEnd
}

private fun isThisWeek(epochMillis: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val weekStart = cal.timeInMillis
    val weekEnd = weekStart + 7 * 86_400_000
    return epochMillis in weekStart until weekEnd
}

private fun isThisMonth(epochMillis: Long): Boolean {
    val cal = Calendar.getInstance()
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.add(Calendar.MONTH, 1)
    val monthEnd = cal.timeInMillis
    return epochMillis in monthStart until monthEnd
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    expenses: List<ExpenseEntity>,
    todayTotal: Double,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onDeleteExpense: (Long) -> Unit,
    onRefresh: () -> Unit = {},
    isLoading: Boolean = false
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val pullToRefreshState = rememberPullToRefreshState()

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filteredExpenses = remember(expenses, selectedFilter) {
        when (selectedFilter) {
            "Today" -> expenses.filter { isToday(it.createdAt) }
            "This Week" -> expenses.filter { isThisWeek(it.createdAt) }
            "This Month" -> expenses.filter { isThisMonth(it.createdAt) }
            else -> expenses
        }
    }

    val filterTotal = remember(filteredExpenses) {
        filteredExpenses.sumOf { it.amount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expenses", fontWeight = FontWeight.Bold) },
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
                onClick = onAddExpense,
                containerColor = IndigoPrimary,
                contentColor = Color.White,
                shape = FabShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add expense")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (expenses.isEmpty() && !isLoading) {
                EmptyState(
                    icon = Icons.Default.AccountBalance,
                    message = "No expenses yet",
                    description = "Track your business expenses by tapping the + button",
                    actionLabel = "Add Expense",
                    onAction = onAddExpense
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Summary card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.large, vertical = Spacing.small),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.cardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ComponentSize.iconContainerMedium)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            GradientOrange.map { it.copy(alpha = 0.15f) }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(IconSize.small)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedFilter == "All") "Total Expenses" else "$selectedFilter Expenses",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryLight,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatCurrencyTotal(filterTotal),
                                    style = AmountDisplayStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = ErrorRed,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${filteredExpenses.size} expense${if (filteredExpenses.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (filteredExpenses.size > 0) AmberWarning else TextSecondaryLight,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Filter chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.large, vertical = Spacing.small),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        val filters = listOf("All", "Today", "This Week", "This Month")
                        items(filters) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = TextSecondaryLight
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // Expense list
                    if (filteredExpenses.isEmpty()) {
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
                                    Icons.Outlined.SearchOff,
                                    contentDescription = null,
                                    tint = TextTertiaryLight,
                                    modifier = Modifier.size(IconSize.huge)
                                )
                                Text(
                                    text = "No expenses for \"$selectedFilter\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.Center
                                )
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
                            items(filteredExpenses, key = { it.id }) { expense ->
                                ExpenseCard(
                                    expense = expense,
                                    onDelete = { onDeleteExpense(expense.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatCurrencyTotal(amount: Double): String {
    val abs = kotlin.math.abs(amount)
    return if (abs >= 100_000) {
        "₹${String.format("%.1f", abs / 100_000)}L"
    } else if (abs >= 1_000) {
        "₹${String.format("%.1f", abs / 1_000)}K"
    } else {
        String.format("₹%.2f", amount)
    }
}

@Composable
private fun ExpenseCard(
    expense: ExpenseEntity,
    onDelete: () -> Unit
) {
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
            // Category icon in colored circle
            val catIcon = categoryIcon(expense.category)
            Box(
                modifier = Modifier
                    .size(ComponentSize.avatarMedium)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            GradientAmber.map { it.copy(alpha = 0.15f) }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    catIcon,
                    contentDescription = expense.category,
                    tint = AmberWarning,
                    modifier = Modifier.size(IconSize.small)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.medium))

            // Title + category + date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xxsmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category badge
                    Box(
                        modifier = Modifier
                            .clip(BadgeShape)
                            .background(IndigoContainer)
                            .padding(horizontal = Spacing.small, vertical = Spacing.xxsmall)
                    ) {
                        Text(
                            text = expense.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = IndigoOnContainer,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Date
                    Text(
                        text = formatTimestamp(expense.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryLight
                    )
                }
                if (!expense.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xxsmall))
                    Text(
                        text = expense.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiaryLight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.small))

            // Amount + delete
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format("₹%.2f", expense.amount),
                    style = AmountMediumStyle,
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed,
                    maxLines = 1
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(IconSize.medium)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete expense",
                        tint = ErrorRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(IconSize.small)
                    )
                }
            }
        }
    }
}
