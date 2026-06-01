package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.R
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.ui.screens.BillItemEntry
import com.aistudio.sharmakhata.pqmzvk.data.remote.StoredItem
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import androidx.compose.animation.AnimatedVisibility
import com.aistudio.sharmakhata.pqmzvk.util.GstCalculator
import com.aistudio.sharmakhata.pqmzvk.util.GstType
import com.aistudio.sharmakhata.pqmzvk.util.GstBreakdown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBillScreen(
    onMenuClick: () -> Unit = {},
    shopInitial: String = "S",
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

    // GST state
    var gstTypeState by remember { mutableStateOf(GstType.CGST_SGST) }
    var gstRateState by remember { mutableStateOf(18) }
    var enableGst by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

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
                    Text(stringResource(R.string.bill_created_title), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(stringResource(R.string.bill_created_message))
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
                            Text(stringResource(R.string.whatsapp_label))
                        }
                    }
                    TextButton(onClick = { showSuccessDialog = false; onResetOperation(); onBack() }) {
                        Text(stringResource(R.string.done))
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

    fun calculateGst(): GstBreakdown {
        val subtotal = calculateTotal()
        return if (enableGst) GstCalculator.calculate(subtotal, gstRateState, gstTypeState)
        else GstBreakdown(taxableAmount = subtotal, grandTotal = subtotal)
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
                title = { Text(stringResource(R.string.quick_bill_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                                    text = stringResource(R.string.walk_in_customer),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.optional_details),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryLight
                                )
                            }
                        }

                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.customer_name_optional)) },
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
                            placeholder = { Text(stringResource(R.string.phone_number_optional)) },
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
                                        text = stringResource(R.string.quick_add_items),
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
                                        contentDescription = stringResource(R.string.hide),
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

                // GST Configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = StitchSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
                ) {
                    Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Receipt, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.gst_configuration), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            }
                            Switch(
                                checked = enableGst,
                                onCheckedChange = { enableGst = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = IndigoPrimary)
                            )
                        }
                        AnimatedVisibility(visible = enableGst) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                                Text(stringResource(R.string.gst_type), style = MaterialTheme.typography.labelMedium, color = TextSecondaryLight)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = gstTypeState == GstType.CGST_SGST,
                                        onClick = { gstTypeState = GstType.CGST_SGST },
                                        label = { Text(stringResource(R.string.cgst_sgst_label)) },
                                        leadingIcon = { if (gstTypeState == GstType.CGST_SGST) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IndigoPrimary.copy(alpha = 0.15f), selectedLabelColor = IndigoPrimary)
                                    )
                                    FilterChip(
                                        selected = gstTypeState == GstType.IGST,
                                        onClick = { gstTypeState = GstType.IGST },
                                        label = { Text(stringResource(R.string.igst_label)) },
                                        leadingIcon = { if (gstTypeState == GstType.IGST) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IndigoPrimary.copy(alpha = 0.15f), selectedLabelColor = IndigoPrimary)
                                    )
                                }
                                Text(stringResource(R.string.gst_rate), style = MaterialTheme.typography.labelMedium, color = TextSecondaryLight)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GstCalculator.gstRates.forEach { rate ->
                                        FilterChip(
                                            selected = gstRateState == rate,
                                            onClick = { gstRateState = rate },
                                            label = { Text("$rate%") },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = IndigoPrimary.copy(alpha = 0.15f), selectedLabelColor = IndigoPrimary)
                                        )
                                    }
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
                        text = stringResource(R.string.invoice_items),
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
                        Text(stringResource(R.string.add_item_button), color = IndigoPrimary)
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
                                    stringResource(R.string.item_column),
                                    modifier = Modifier.weight(1.5f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight
                                )
                                Text(
                                    stringResource(R.string.qty_column),
                                    modifier = Modifier.weight(0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    stringResource(R.string.price_column),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondaryLight,
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    stringResource(R.string.amount_column),
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

                                // Autocomplete suggestions for this item slot
                                val suggestions = remember(item.name, storedItems) {
                                    if (item.name.length >= 1)
                                        storedItems.filter {
                                            it.name.contains(item.name, ignoreCase = true)
                                        }.take(5)
                                    else emptyList()
                                }
                                var showSuggestions by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item name with autocomplete dropdown
                                    Box(modifier = Modifier.weight(1.5f)) {
                                        OutlinedTextField(
                                            value = item.name,
                                            onValueChange = { newName ->
                                                items = items.toMutableList().also { it[index] = item.copy(name = newName) }
                                                showSuggestions = newName.isNotEmpty() && storedItems.any {
                                                    it.name.contains(newName, ignoreCase = true)
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
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
                                        // Dropdown of suggestions
                                        if (showSuggestions && suggestions.isNotEmpty()) {
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 48.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                            ) {
                                                Column {
                                                    suggestions.forEach { suggestion ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    items = items.toMutableList().also {
                                                                        it[index] = item.copy(
                                                                            name = suggestion.name,
                                                                            price = suggestion.lastPrice.toString()
                                                                        )
                                                                    }
                                                                    showSuggestions = false
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = suggestion.name,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier.weight(1f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = "₹${suggestion.lastPrice.toInt()}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = IndigoPrimary,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        if (suggestion != suggestions.last()) {
                                                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

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
                                        placeholder = { Text(stringResource(R.string.qty_placeholder), fontSize = 12.sp, textAlign = TextAlign.Center) },
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
                                        placeholder = { Text(stringResource(R.string.price_placeholder_zero), fontSize = 12.sp, textAlign = TextAlign.End) },
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
                                                contentDescription = stringResource(R.string.remove),
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

                    // Totals Section with GST
                    val subtotal = calculateTotal()
                    val gstBreakdown = calculateGst()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        colors = CardDefaults.cardColors(containerColor = IndigoPrimary.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.subtotal), style = MaterialTheme.typography.bodyMedium, color = TextSecondaryLight)
                                Text(FormatUtils.formatCurrency(subtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            if (enableGst) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.taxable_amount), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                    Text(FormatUtils.formatCurrency(gstBreakdown.taxableAmount), style = MaterialTheme.typography.bodySmall)
                                }
                                if (gstTypeState == GstType.CGST_SGST) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.cgst_at_rate, gstRateState / 2), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                        Text(FormatUtils.formatCurrency(gstBreakdown.cgst), style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.sgst_at_rate, gstRateState / 2), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                        Text(FormatUtils.formatCurrency(gstBreakdown.sgst), style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.igst_at_rate, gstRateState), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                        Text(FormatUtils.formatCurrency(gstBreakdown.igst), style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(stringResource(R.string.gst_optional), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                    Text("---", style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = CardBorder)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.grand_total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Text(FormatUtils.formatCurrency(gstBreakdown.grandTotal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview Bill Button
                Button(
                    onClick = { showPreview = true },
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
                        Text(stringResource(R.string.preview_bill), fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Invoice Preview Dialog
    if (showPreview) {
        val previewSubtotal = calculateTotal()
        val previewGst = calculateGst()
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = IndigoPrimary)
                    Text(stringResource(R.string.invoice_preview), fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(if (customerName.isNotBlank()) customerName else stringResource(R.string.walk_in_customer_fallback), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.invoice_type_label, if (enableGst) (if (gstTypeState == GstType.CGST_SGST) stringResource(R.string.cgst_sgst_label) else stringResource(R.string.igst_label)) else stringResource(R.string.non_gst)), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                        }
                    }
                    items.filter { it.name.isNotBlank() }.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${item.qty} x ${FormatUtils.formatCurrency(item.price.toDoubleOrNull() ?: 0.0)}", style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
                            }
                            Text(FormatUtils.formatCurrency((item.price.toDoubleOrNull() ?: 0.0) * (item.qty.toIntOrNull() ?: 1)), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = CardBorder)
                    if (enableGst) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.subtotal), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                            Text(FormatUtils.formatCurrency(previewSubtotal), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.gst_rate_label, gstRateState), style = MaterialTheme.typography.bodySmall, color = TextSecondaryLight)
                            Text(FormatUtils.formatCurrency(previewGst.totalGst), style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.grand_total), fontWeight = FontWeight.Bold)
                        Text(FormatUtils.formatCurrency(previewGst.grandTotal), fontWeight = FontWeight.Bold, color = IndigoPrimary)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPreview = false }) { Text(stringResource(R.string.edit)) }
                    Button(onClick = {
                        showPreview = false
                        val billItems = items.filter { it.name.isNotBlank() && it.price.isNotBlank() }
                            .map { BillItemRequest(it.name, it.price.toDoubleOrNull() ?: 0.0, it.qty.toIntOrNull() ?: 1) }
                        if (billItems.isNotEmpty()) {
                            onCreateBill(customerName.ifBlank { null }, customerPhone.ifBlank { null }, previewGst.grandTotal, billItems)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)) {
                        Text(stringResource(R.string.create_invoice_button), fontWeight = FontWeight.Bold)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
