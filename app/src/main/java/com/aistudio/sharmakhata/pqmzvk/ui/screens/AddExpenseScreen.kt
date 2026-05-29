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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*

private val categoryOptions = listOf(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onBack: () -> Unit,
    onSave: (title: String, amount: Double, category: String, note: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Other") }
    var note by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val isFormValid = title.isNotBlank() && amountText.toDoubleOrNull() != null && (amountText.toDoubleOrNull() ?: 0.0) > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense", fontWeight = FontWeight.SemiBold) },
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
            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (it.isNotBlank()) titleError = false
                },
                label = { Text("Title *") },
                placeholder = { Text("e.g. Office rent, Staff salary...") },
                leadingIcon = {
                    Icon(Icons.Default.Description, contentDescription = null, tint = IndigoPrimary)
                },
                isError = titleError,
                supportingText = if (titleError) {
                    { Text("Title is required", color = ErrorRed) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = TextFieldShape,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = IndigoPrimary,
                    unfocusedIndicatorColor = CardBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = BackgroundLight,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // Amount field
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (it.isEmpty() || it.all { c -> c.isDigit() || c == '.' }) {
                        amountText = it
                        if (it.toDoubleOrNull() != null) amountError = false
                    }
                },
                label = { Text("Amount *") },
                placeholder = { Text("0.00") },
                leadingIcon = {
                    Text(
                        "₹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary,
                        modifier = Modifier.padding(start = Spacing.small)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError,
                supportingText = if (amountError) {
                    { Text("Valid amount is required", color = ErrorRed) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = TextFieldShape,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = IndigoPrimary,
                    unfocusedIndicatorColor = CardBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = BackgroundLight,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            // Category label
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.small)
            )

            // Category chips in horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                categoryOptions.forEach { (category, icon) ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
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

            // Note field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("Add any additional details...") },
                leadingIcon = {
                    Icon(Icons.Default.Notes, contentDescription = null, tint = IndigoPrimary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                shape = TextFieldShape,
                maxLines = 4,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = IndigoPrimary,
                    unfocusedIndicatorColor = CardBorder,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = BackgroundLight,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Save button
            Button(
                onClick = {
                    titleError = title.isBlank()
                    amountError = amountText.toDoubleOrNull() == null || (amountText.toDoubleOrNull() ?: 0.0) <= 0
                    if (!titleError && !amountError) {
                        val amount = amountText.toDouble()
                        onSave(title.trim(), amount, selectedCategory, note.trim().ifBlank { null })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ComponentSize.buttonHeight),
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = IndigoPrimary.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                ),
                shape = ButtonShape,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = Elevation.low,
                    pressedElevation = Elevation.medium
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(IconSize.small))
                Spacer(modifier = Modifier.width(Spacing.small))
                Text("Save Expense", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(Spacing.xxlarge))
        }
    }
}
