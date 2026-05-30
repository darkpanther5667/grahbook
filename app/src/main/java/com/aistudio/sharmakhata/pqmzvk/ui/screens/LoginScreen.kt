package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoggedIn: () -> Unit,
    onRegisterStore: () -> Unit = {}
) {
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpStage by remember { mutableStateOf(false) }
    var storeNotFound by remember { mutableStateOf(false) }
    var loginMode by remember { mutableStateOf("password") } // "password" | "otp"
    var passwordVisible by remember { mutableStateOf(false) }

    // Load session once on first composition
    LaunchedEffect(Unit) {
        SessionManager.load(context)
        viewModel.resetOperationState()

        // Pre-fill phone from stored registration if available
        if (phoneNumber.isEmpty()) {
            val storedPhone = SessionManager.phone
            if (!storedPhone.isNullOrBlank()) {
                val digits = storedPhone.filter { it.isDigit() }
                if (digits.length >= 10) {
                    phoneNumber = digits.takeLast(10)
                }
            }
        }
    }

    // React to operation state changes
    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is OperationState.Success -> {
                when (state.message) {
                    "Logged in" -> onLoggedIn()
                    "Code sent on WhatsApp" -> {
                        isOtpStage = true
                        storeNotFound = false
                        viewModel.resetOperationState()
                    }
                    else -> {
                        // Generic success — navigate to OTP stage automatically
                        if (!isOtpStage && phoneNumber.length == 10 && loginMode == "otp") {
                            isOtpStage = true
                        }
                        viewModel.resetOperationState()
                    }
                }
            }
            is OperationState.Error -> {
                val message = state.message
                storeNotFound = message.contains("No store found", ignoreCase = true) ||
                                message.contains("not authorized", ignoreCase = true) ||
                                message.contains("No account found", ignoreCase = true)

                val actionLabel = when {
                    message.contains("internet", ignoreCase = true) ||
                    message.contains("timeout", ignoreCase = true) ||
                    message.contains("connect", ignoreCase = true) -> "Retry"
                    storeNotFound -> "Register"
                    else -> null
                }

                val result = snackbarHostState.showSnackbar(
                    message = state.message,
                    actionLabel = actionLabel,
                    withDismissAction = true
                )
                when {
                    result == SnackbarResult.ActionPerformed && actionLabel == "Retry" && !isOtpStage -> {
                        viewModel.requestLoginCode("+91$phoneNumber")
                    }
                    result == SnackbarResult.ActionPerformed && actionLabel == "Register" -> {
                        onRegisterStore()
                    }
                }
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StitchTeal,
                            StitchTealDark,
                            Color(0xFF134E4A)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Logo
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "G",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = StitchTeal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Grahbook",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "Smart Billing for Your Business",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Login Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!isOtpStage) {
                            // ── Phone Input Stage ──
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = StitchTeal,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "Welcome!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                            Text(
                                "Enter your phone number to continue",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryLight,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { newVal ->
                                    if (newVal.length <= 10 && newVal.all { it.isDigit() }) {
                                        phoneNumber = newVal
                                        storeNotFound = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Phone Number") },
                                placeholder = { Text("9876543210") },
                                supportingText = {
                                    when {
                                        storeNotFound -> Text(
                                            "No account found. Tap 'Register' below",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        phoneNumber.isNotEmpty() && phoneNumber.length < 10 -> Text(
                                            "Enter 10-digit mobile number",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        else -> Text("Enter your 10-digit Indian mobile number")
                                    }
                                },
                                leadingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("+91", fontWeight = FontWeight.Bold, color = StitchTeal)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(CardBorder))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = StitchTeal, modifier = Modifier.size(18.dp))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedIndicatorColor = if (phoneNumber.length == 10) StitchTeal else MaterialTheme.colorScheme.error,
                                    unfocusedIndicatorColor = CardBorder,
                                    focusedContainerColor = BackgroundLight,
                                    unfocusedContainerColor = BackgroundLight,
                                    focusedLabelColor = if (phoneNumber.length == 10) StitchTeal else MaterialTheme.colorScheme.error,
                                    unfocusedLabelColor = TextSecondaryLight,
                                    cursorColor = StitchTeal
                                ),
                                isError = (phoneNumber.isNotEmpty() && phoneNumber.length < 10) || storeNotFound
                            )

                            // ── Login Mode Toggle ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundLight, RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FilterChip(
                                    selected = loginMode == "password",
                                    onClick = { loginMode = "password" },
                                    label = { Text("Password", fontWeight = if (loginMode == "password") FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        if (loginMode == "password") {
                                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = StitchTeal)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StitchTeal.copy(alpha = 0.15f),
                                        selectedLabelColor = StitchTeal
                                    )
                                )
                                FilterChip(
                                    selected = loginMode == "otp",
                                    onClick = { loginMode = "otp" },
                                    label = { Text("OTP", fontWeight = if (loginMode == "otp") FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        if (loginMode == "otp") {
                                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp), tint = StitchTeal)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StitchTeal.copy(alpha = 0.15f),
                                        selectedLabelColor = StitchTeal
                                    )
                                )
                            }

                            if (loginMode == "password") {
                                // ── Password Login ──
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Password") },
                                    placeholder = { Text("Enter your password") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = StitchTeal) },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                                tint = TextSecondaryLight
                                            )
                                        }
                                    },
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = TextPrimaryLight,
                                        unfocusedTextColor = TextPrimaryLight,
                                        focusedIndicatorColor = StitchTeal,
                                        unfocusedIndicatorColor = CardBorder,
                                        focusedContainerColor = BackgroundLight,
                                        unfocusedContainerColor = BackgroundLight,
                                        focusedLabelColor = StitchTeal,
                                        unfocusedLabelColor = TextSecondaryLight,
                                        cursorColor = StitchTeal
                                    )
                                )

                                Button(
                                    onClick = {
                                        storeNotFound = false
                                        viewModel.loginWithPassword("+91$phoneNumber", password, context)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    enabled = phoneNumber.length == 10 && password.length >= 4 && operationState !is OperationState.Loading,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = StitchTeal)
                                ) {
                                    if (operationState is OperationState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Login", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                // ── OTP Login ──
                                Button(
                                    onClick = {
                                        storeNotFound = false
                                        viewModel.requestLoginCode("+91$phoneNumber")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    enabled = phoneNumber.length == 10 && operationState !is OperationState.Loading,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (phoneNumber.length == 10) StitchTeal else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    if (operationState is OperationState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("Send OTP", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Register link (always visible)
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("New here?", color = TextSecondaryLight, fontSize = 13.sp)
                                TextButton(onClick = onRegisterStore) {
                                    Text("Register your store", color = StitchTeal, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            // ── OTP Stage ──
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = StitchTeal,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "Verify OTP",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryLight
                            )
                            Text(
                                "Enter the 6-digit code sent to\n+91 $phoneNumber",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondaryLight,
                                textAlign = TextAlign.Center
                            )

                            OutlinedTextField(
                                value = otp,
                                onValueChange = { newVal ->
                                    if (newVal.length <= 6 && newVal.all { it.isDigit() }) {
                                        otp = newVal
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("OTP") },
                                placeholder = { Text("_ _ _ _ _ _") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = StitchTeal)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = TextPrimaryLight,
                                    unfocusedTextColor = TextPrimaryLight,
                                    focusedIndicatorColor = StitchTeal,
                                    unfocusedIndicatorColor = CardBorder,
                                    focusedContainerColor = BackgroundLight,
                                    unfocusedContainerColor = BackgroundLight,
                                    focusedLabelColor = StitchTeal,
                                    unfocusedLabelColor = TextSecondaryLight,
                                    cursorColor = StitchTeal
                                ),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 8.sp
                                )
                            )

                            Button(
                                onClick = {
                                    viewModel.verifyLoginCode("+91$phoneNumber", otp, context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = otp.length == 6 && operationState !is OperationState.Loading,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StitchTeal)
                            ) {
                                if (operationState is OperationState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Verify & Login", fontWeight = FontWeight.Bold)
                                }
                            }

                            TextButton(onClick = {
                                isOtpStage = false
                                otp = ""
                                viewModel.requestLoginCode("+91$phoneNumber")
                            }) {
                                Text("Resend OTP", color = StitchTeal, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
