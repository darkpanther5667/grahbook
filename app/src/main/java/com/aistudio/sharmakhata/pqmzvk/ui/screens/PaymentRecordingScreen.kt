package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentRecordingScreen(
    viewModel: MainViewModel,
    customerId: String,
    customerName: String,
    onBack: () -> Unit
) {
    val operationState by viewModel.operationState.collectAsState()
    val dbState by viewModel.dbState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var amount by remember { mutableStateOf("") }
    var isCredit by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Calculate pending amount for this customer
    val pendingAmount = remember(dbState, customerId) {
        when (dbState) {
            is UiState.Success -> {
                val db = (dbState as UiState.Success).data
                val customerTransactions = db.transactions.filter { it.customerId == customerId }
                val customerBills = db.bills.filter { it.customerId == customerId }
                val payments = customerTransactions.filter { it.type == "payment" }.sumOf { it.amount }
                val credits = customerTransactions.filter { it.type == "credit" }.sumOf { it.amount }
                val billTotal = customerBills.sumOf { it.total }
                credits + billTotal - payments
            }
            else -> 0.0
        }
    }

    val isValid = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0

    LaunchedEffect(operationState) {
        when (operationState) {
            is OperationState.Success -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Success).message)
                viewModel.resetOperationState()
                onBack()
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar((operationState as OperationState.Error).message)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = if (isCredit) ErrorRed else SuccessGreen
                    )
                    Text(
                        "Confirm ${if (isCredit) "Credit" else "Payment"}",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text("Recording ${if (isCredit) "credit" else "payment"} of:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = FormatUtils.formatCurrency(amount.toDoubleOrNull() ?: 0.0),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = IndigoPrimary
                    )
                    if (note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Note: $note", color = TextSecondaryLight, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.addPayment(
                            context = context,
                            customerId = customerId,
                            amount = if (isCredit) -(amount.toDoubleOrNull() ?: 0.0) else (amount.toDoubleOrNull() ?: 0.0),
                            note = note.ifBlank { null }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCredit) ErrorRed else SuccessGreen
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isCredit) "Record Credit" else "Record Payment", fontWeight = FontWeight.SemiBold) },
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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Customer info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(IndigoPrimary, IndigoDark)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customerName.firstOrNull()?.uppercase()?.toString() ?: "C",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (pendingAmount > 0) {
                                Text(
                                    text = "Pending: ${FormatUtils.formatCurrency(pendingAmount)}",
                                    fontSize = 13.sp,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "No pending amount",
                                    fontSize = 13.sp,
                                    color = SuccessGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Payment/Credit type toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (!isCredit) Modifier else Modifier
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isCredit) SuccessGreen.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { isCredit = false },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (!isCredit) 2.dp else 0.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = if (!isCredit) SuccessGreen else TextSecondaryLight,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Payment",
                                fontWeight = if (!isCredit) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isCredit) SuccessGreen else TextSecondaryLight
                            )
                            Text(
                                "Received",
                                fontSize = 11.sp,
                                color = if (!isCredit) SuccessGreen.copy(alpha = 0.7f) else TextSecondaryLight
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isCredit) Modifier else Modifier
                            ),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCredit) ErrorRed.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        onClick = { isCredit = true },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isCredit) 2.dp else 0.dp
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = if (isCredit) ErrorRed else TextSecondaryLight,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Credit",
                                fontWeight = if (isCredit) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCredit) ErrorRed else TextSecondaryLight
                            )
                            Text(
                                "Given",
                                fontSize = 11.sp,
                                color = if (isCredit) ErrorRed.copy(alpha = 0.7f) else TextSecondaryLight
                            )
                        }
                    }
                }

                // Amount input
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Enter Amount",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondaryLight,
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { newVal ->
                                if (newVal.isEmpty() || newVal.all { it.isDigit() || it == '.' }) {
                                    amount = newVal
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("₹ 0.00", textAlign = TextAlign.Center) },
                            textStyle = MaterialTheme.typography.displaySmall.copy(
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = if (isCredit) ErrorRed else SuccessGreen,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Quick amount buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("500", "1000", "2000", "5000").forEach { quickAmount ->
                                OutlinedButton(
                                    onClick = { amount = quickAmount },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isCredit) ErrorRed else SuccessGreen
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("₹$quickAmount", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Note field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a note (optional)...") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Notes, contentDescription = null, tint = TextSecondaryLight)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = if (isCredit) ErrorRed else SuccessGreen,
                        unfocusedIndicatorColor = CardBorder,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.weight(1f))

                // Submit button
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    enabled = isValid && operationState !is OperationState.Loading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCredit) ErrorRed else SuccessGreen
                    )
                ) {
                    if (operationState is OperationState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (isCredit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Record ${if (isCredit) "Credit" else "Payment"}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
