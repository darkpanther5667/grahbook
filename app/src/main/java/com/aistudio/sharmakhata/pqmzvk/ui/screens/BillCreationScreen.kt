package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

data class BillItemEntry(
    val name: String = "",
    val price: String = "",
    val qty: String = "1"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillCreationScreen(
    viewModel: MainViewModel,
    customerId: String,
    customerName: String,
    onBack: () -> Unit
) {
    val operationState by viewModel.operationState.collectAsState()
    val lastBillId by viewModel.lastCreatedBillId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var items by remember { mutableStateOf(listOf(BillItemEntry())) }
    var showSuccessDialog by remember { mutableStateOf(false) }

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
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false; viewModel.resetOperationState(); onBack() },
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
                                viewModel.sendInvoiceOnWhatsApp(context, lastBillId!!)
                                showSuccessDialog = false
                                viewModel.resetOperationState()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp")
                        }
                    }
                    TextButton(onClick = { showSuccessDialog = false; viewModel.resetOperationState(); onBack() }) {
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

    val isValid = items.any { it.name.isNotBlank() && it.price.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Invoice", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                // Customer info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = IndigoPrimary.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Billing for",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryLight
                            )
                            Text(
                                text = customerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
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

                // Items Table Header
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

                            Divider(color = CardBorder)

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
                            Divider(color = CardBorder)
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

                // Generate Invoice Button
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
                            viewModel.createBill(context, customerId, total, billItems)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = isValid && operationState !is OperationState.Loading,
                    shape = RoundedCornerShape(14.dp),
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
                        Text("Generate Invoice", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
