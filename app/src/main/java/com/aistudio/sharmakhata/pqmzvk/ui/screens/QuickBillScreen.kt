package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.ui.screens.BillItemEntry
import com.aistudio.sharmakhata.pqmzvk.data.remote.StoredItem
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBillScreen(
    onBack: () -> Unit,
    onCreateBill: (customerName: String?, customerPhone: String?, total: Double, items: List<BillItemRequest>) -> Unit,
    operationState: OperationState,
    lastBillId: String?,
    storedItems: List<StoredItem>,
    onResetOperation: () -> Unit,
    onSendWhatsApp: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    var items by remember { mutableStateOf(listOf(BillItemEntry())) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showStoredItems by remember { mutableStateOf(true) }

    // Watch operation state for snackbar and success dialog
    LaunchedEffect(operationState) {
        when (operationState) {
            is OperationState.Success -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Success).message)
                if (!lastBillId.isNullOrBlank()) {
                    showSuccessDialog = true
                }
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Error).message)
                onResetOperation()
            }
            else -> {}
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false; onResetOperation(); onBack() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    Text("Bill Created!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Invoice has been created successfully. You can send it via WhatsApp.")
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!lastBillId.isNullOrBlank()) {
                        Button(
                            onClick = {
                                lastBillId?.let { billId ->
                                    onSendWhatsApp(billId)
                                }
                                showSuccessDialog = false
                                onResetOperation()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp")
                        }
                    }
                    TextButton(onClick = { showSuccessDialog = false; onResetOperation(); onBack() }) {
                        Text("Done")
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    fun calculateTotal(): Double {
        return items.sumOf { item ->
            val price = item.price.toDoubleOrNull() ?: 0.0
            val qty = item.qty.toIntOrNull() ?: 1
            price * qty
        }
    }

    // Add a stored item to the bill — fills first empty row or appends a new one
    fun addStoredItem(stored: StoredItem) {
        val firstEmptyIdx = items.indexOfFirst { it.name.isBlank() }
        if (firstEmptyIdx >= 0) {
            items = items.toMutableList().also {
                it[firstEmptyIdx] = BillItemEntry(
                    name = stored.name,
                    price = stored.lastPrice.toString(),
                    qty = "1"
                )
            }
        } else {
            if (items.size < 20) {
                items = items + BillItemEntry(
                    name = stored.name,
                    price = stored.lastPrice.toString(),
                    qty = "1"
                )
            }
        }
    }

    val isValid = items.any { it.name.isNotBlank() && it.price.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Bill", fontWeight = FontWeight.SemiBold) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Walk-in customer fields card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.cardPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(ComponentSize.iconContainerMedium)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(GradientIndigo.map { it.copy(alpha = 0.15f) })),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(IconSize.small)
                                )
                            }
                            Column {
                                Text(
                                    text = "Walk-in Customer",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Optional details for the invoice",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryLight
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Customer Name (optional)") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PersonOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.small),
                                    tint = TextSecondaryLight
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = IndigoPrimary,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        OutlinedTextField(
                            value = customerPhone,
                            onValueChange = { newValue ->
                                // Allow only digits, +, and -
                                if (newValue.all { it.isDigit() || it == '+' || it == '-' }) {
                                    customerPhone = newValue
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Phone Number (optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.small),
                                    tint = TextSecondaryLight
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = IndigoPrimary,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )
                    }
                }

                // Quick Add Items section (from stored catalog)
                if (storedItems.isNotEmpty() && showStoredItems) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ShoppingCart,
                                        contentDescription = null,
                                        tint = IndigoPrimary,
                                        modifier = Modifier.size(IconSize.small)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.small))
                                    Text(
                                        text = "Quick Add Items",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = { showStoredItems = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Hide",
                                        tint = Slate400,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.small))
                            // Horizontally scrollable chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                            ) {
                                storedItems.take(20).forEach { stored ->
                                    SuggestionChip(
                                        onClick = { addStoredItem(stored) },
                                        label = {
                                            Text(
                                                text = "${stored.name} ₹${stored.lastPrice.toInt()}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Invoice Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Invoice Items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = {
                            if (items.size < 20) {
                                items = items + BillItemEntry()
                            }
                        }
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", color = IndigoPrimary)
                    }
                }

                // Items Table
                if (items.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Table header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Item",
                                    modifier = Modifier.weight(1.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight
                                )
                                Text(
                                    "Qty",
                                    modifier = Modifier.weight(0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Price",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    "Amount",
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.width(32.dp)) // for delete button
                            }

                            HorizontalDivider(color = CardBorder)

                            Spacer(modifier = Modifier.height(8.dp))

                            // Item rows
                            items.forEachIndexed { index, item ->
                                val itemTotal = (item.price.toDoubleOrNull() ?: 0.0) * (item.qty.toIntOrNull() ?: 1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item name
                                    OutlinedTextField(
                                        value = item.name,
                                        onValueChange = { newName ->
                                            items = items.toMutableList().also { it[index] = item.copy(name = newName) }
                                        },
                                        modifier = Modifier
                                            .weight(1.5f)
                                            .height(48.dp),
                                        placeholder = { Text("Item name", fontSize = 12.sp) },
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = IndigoPrimary,
                                            unfocusedIndicatorColor = CardBorder,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = BackgroundLight
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Quantity
                                    OutlinedTextField(
                                        value = item.qty,
                                        onValueChange = { newQty ->
                                            if (newQty.isEmpty() || newQty.all { it.isDigit() }) {
                                                items = items.toMutableList().also { it[index] = item.copy(qty = newQty) }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(0.6f)
                                            .height(48.dp),
                                        placeholder = { Text("1", fontSize = 12.sp, textAlign = TextAlign.Center) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.Center),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = IndigoPrimary,
                                            unfocusedIndicatorColor = CardBorder,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = BackgroundLight
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Price
                                    OutlinedTextField(
                                        value = item.price,
                                        onValueChange = { newPrice ->
                                            if (newPrice.isEmpty() || newPrice.all { it.isDigit() || it == '.' }) {
                                                items = items.toMutableList().also { it[index] = item.copy(price = newPrice) }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        placeholder = { Text("₹0", fontSize = 12.sp, textAlign = TextAlign.End) },
                                        textStyle = MaterialTheme.typography.bodySmall.copy(textAlign = TextAlign.End),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = IndigoPrimary,
                                            unfocusedIndicatorColor = CardBorder,
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = BackgroundLight
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Amount (calculated)
                                    Text(
                                        text = FormatUtils.formatCurrency(itemTotal),
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.End,
                                        maxLines = 1
                                    )

                                    // Delete button
                                    if (items.size > 1) {
                                        IconButton(
                                            onClick = {
                                                items = items.toMutableList().also { it.removeAt(index) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveCircleOutline,
                                                contentDescription = "Remove",
                                                tint = ErrorRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(28.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Totals Section
                    val totalAmount = calculateTotal()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = IndigoPrimary.copy(alpha = 0.05f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = TextSecondaryLight)
                                Text(FormatUtils.formatCurrency(totalAmount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GST (Optional)", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                Text("—", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = CardBorder)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Grand Total",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    FormatUtils.formatCurrency(totalAmount),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = IndigoPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Generate Bill Button
                Button(
                    onClick = {
                        val billItems = items
                            .filter { it.name.isNotBlank() && it.price.isNotBlank() }
                            .map { item ->
                                val price = item.price.toDoubleOrNull() ?: 0.0
                                val qty = item.qty.toIntOrNull() ?: 1
                                BillItemRequest(item.name, price, qty)
                            }
                        if (billItems.isNotEmpty()) {
                            val total = calculateTotal()
                            onCreateBill(customerName.ifBlank { null }, customerPhone.ifBlank { null }, total, billItems)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.buttonHeight),
                    enabled = isValid && operationState !is OperationState.Loading,
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    if (operationState is OperationState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Bill", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
