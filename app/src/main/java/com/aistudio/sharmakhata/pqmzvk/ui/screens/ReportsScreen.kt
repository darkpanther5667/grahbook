package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import com.aistudio.sharmakhata.pqmzvk.data.model.DailyReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val reportState by viewModel.reportState.collectAsState()
    val dbState by viewModel.dbState.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    var selectedPeriod by remember { mutableStateOf("Today") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports", fontWeight = FontWeight.Bold) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            // Period filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Today", "This Week", "This Month", "All").forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StitchTeal,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = TextSecondaryLight
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            when (reportState) {
                is UiState.Success -> {
                    val report = (reportState as UiState.Success).data
                    val totalExpenses = expenses.sumOf { it.amount }
                    val netProfit = report.billsTotal - totalExpenses

                    // Summary cards row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReportStatCard(
                            label = "Total Sales",
                            value = FormatUtils.formatCurrency(report.billsTotal),
                            icon = Icons.Outlined.TrendingUp,
                            gradient = GradientTeal,
                            valueColor = StitchTeal,
                            modifier = Modifier.weight(1f)
                        )
                        ReportStatCard(
                            label = "Expenses",
                            value = FormatUtils.formatCurrency(totalExpenses),
                            icon = Icons.Outlined.MoneyOff,
                            gradient = GradientOrange,
                            valueColor = OrangeDanger,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReportStatCard(
                            label = "Net Profit",
                            value = FormatUtils.formatCurrency(netProfit.coerceAtLeast(0.0)),
                            icon = Icons.Outlined.AccountBalance,
                            gradient = if (netProfit >= 0) GradientEmerald else GradientOrange,
                            valueColor = if (netProfit >= 0) SuccessGreen else ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        ReportStatCard(
                            label = "Collection",
                            value = FormatUtils.formatCurrency(report.paymentTotal),
                            icon = Icons.Outlined.AccountBalanceWallet,
                            gradient = GradientSky,
                            valueColor = StitchSky,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Revenue Breakdown section
                    Text(
                        text = "REVENUE BREAKDOWN",
                        style = SectionOverlineStyle,
                        color = TextTertiaryLight,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BreakdownRow(
                                label = "Total Bills",
                                value = FormatUtils.formatCurrency(report.billsTotal),
                                count = "${report.billsCount} bills",
                                valueColor = StitchTeal
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = DividerColor)
                            BreakdownRow(
                                label = "Total Expenses",
                                value = FormatUtils.formatCurrency(totalExpenses),
                                count = "${expenses.size} entries",
                                valueColor = ErrorRed
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = DividerColor)
                            BreakdownRow(
                                label = "Net Revenue",
                                value = FormatUtils.formatCurrency(netProfit.coerceAtLeast(0.0)),
                                count = if (netProfit >= 0) "Profit" else "Loss",
                                valueColor = if (netProfit >= 0) SuccessGreen else ErrorRed
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = DividerColor)
                            BreakdownRow(
                                label = "Outstanding",
                                value = FormatUtils.formatCurrency(report.outstanding.sumOf { it.balance }),
                                count = "${report.outstanding.size} customers",
                                valueColor = OrangeDanger
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Outstanding section
                    if (report.outstanding.isNotEmpty()) {
                        Text(
                            text = "OUTSTANDING",
                            style = SectionOverlineStyle,
                            color = TextTertiaryLight,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                report.outstanding.take(5).forEachIndexed { index, entry ->
                                    OutstandingRow(
                                        name = entry.name,
                                        balance = entry.balance,
                                        isLast = index == report.outstanding.lastIndex || index == 4
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = StitchTeal)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Failed to load reports", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    gradient: List<Color>,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryLight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    count: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
                text = count,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryLight
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun OutstandingRow(
    name: String,
    balance: Double,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initial = name.firstOrNull()?.uppercase()?.toString() ?: "?"
        val gradient = AvatarColors[name.hashCode().mod(AvatarColors.size).let { kotlin.math.abs(it) }]
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = FormatUtils.formatCurrency(balance),
            style = AmountSmallStyle,
            fontWeight = FontWeight.Bold,
            color = if (balance > 0) AmountDue else AmountCredit
        )
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = DividerColor
        )
    }
}
