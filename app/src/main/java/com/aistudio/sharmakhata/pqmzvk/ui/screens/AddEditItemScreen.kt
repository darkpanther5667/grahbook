package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.data.local.ItemEntity
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    itemId: Long? = null,
    existingItem: ItemEntity? = null,
    onBack: () -> Unit,
    onSave: (name: String, price: Double, stock: Int, lowStockAlert: Int) -> Unit,
    onDelete: (Long) -> Unit = {}
) {
    val isEditing = itemId != null || existingItem != null

    var name by remember { mutableStateOf(existingItem?.name ?: "") }
    var price by remember { mutableStateOf(existingItem?.price?.let { if (it == 0.0) "" else it.toString() } ?: "") }
    var stock by remember { mutableStateOf(existingItem?.stock?.toString() ?: "0") }
    var lowStockAlert by remember { mutableStateOf(existingItem?.lowStockAlert?.toString() ?: "5") }

    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank() && price.isNotBlank()

    fun validateAndSave() {
        nameError = name.isBlank()
        priceError = price.isBlank()
        if (name.isNotBlank() && price.isNotBlank()) {
            onSave(name.trim(), price.toDoubleOrNull() ?: 0.0, stock.toIntOrNull() ?: 0, lowStockAlert.toIntOrNull() ?: 5)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Edit Item" else "Add Item",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                .padding(Spacing.large),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            .background(
                                IndigoPrimary.copy(alpha = 0.1f),
                                shape = CardShape
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
                    Column {
                        Text(
                            text = if (isEditing) "Edit Item Details" else "New Item",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEditing) "Update the item information below"
                            else "Add a new item to your inventory",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryLight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Form fields container
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    // Item Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (it.isNotBlank()) nameError = false
                        },
                        label = { Text("Item Name *") },
                        placeholder = { Text("e.g. Notebook, Pen, Bag") },
                        leadingIcon = {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = IndigoPrimary)
                        },
                        isError = nameError,
                        supportingText = if (nameError) {
                            { Text("Item name is required", color = ErrorRed) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = TextFieldShape,
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = IndigoPrimary,
                            unfocusedIndicatorColor = CardBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = BackgroundLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight,
                            cursorColor = IndigoPrimary
                        )
                    )

                    // Price
                    OutlinedTextField(
                        value = price,
                        onValueChange = {
                            price = it
                            if (it.isNotBlank()) priceError = false
                        },
                        label = { Text("Price *") },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Icon(Icons.Default.CurrencyRupee, contentDescription = null, tint = IndigoPrimary)
                        },
                        isError = priceError,
                        supportingText = if (priceError) {
                            { Text("Price is required", color = ErrorRed) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = TextFieldShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = IndigoPrimary,
                            unfocusedIndicatorColor = CardBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = BackgroundLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight,
                            cursorColor = IndigoPrimary
                        )
                    )

                    // Stock Quantity
                    OutlinedTextField(
                        value = stock,
                        onValueChange = {
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) {
                                stock = it
                            }
                        },
                        label = { Text("Stock Quantity") },
                        placeholder = { Text("0") },
                        leadingIcon = {
                            Icon(Icons.Default.Inventory, contentDescription = null, tint = IndigoPrimary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = TextFieldShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = IndigoPrimary,
                            unfocusedIndicatorColor = CardBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = BackgroundLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight,
                            cursorColor = IndigoPrimary
                        )
                    )

                    // Low Stock Alert
                    OutlinedTextField(
                        value = lowStockAlert,
                        onValueChange = {
                            if (it.isEmpty() || it.all { c -> c.isDigit() }) {
                                lowStockAlert = it
                            }
                        },
                        label = { Text("Low Stock Alert Threshold") },
                        placeholder = { Text("5") },
                        leadingIcon = {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AmberWarning)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = TextFieldShape,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = IndigoPrimary,
                            unfocusedIndicatorColor = CardBorder,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = BackgroundLight,
                            focusedTextColor = TextPrimaryLight,
                            unfocusedTextColor = TextPrimaryLight,
                            cursorColor = IndigoPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            // Save Button
            Button(
                onClick = { validateAndSave() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.buttonHeight),
                enabled = isValid,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = IndigoPrimary.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = Elevation.low,
                    pressedElevation = Elevation.medium
                )
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.small)
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = if (isEditing) "Update Item" else "Save Item",
                    fontWeight = FontWeight.Bold
                )
            }

            // Delete Button (only when editing)
            if (isEditing && existingItem != null) {
                OutlinedButton(
                    onClick = { onDelete(existingItem.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentSize.buttonHeight),
                    shape = ButtonShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ErrorRed)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.small),
                        tint = ErrorRed
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text(
                        text = "Delete Item",
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.large))
        }
    }
}
