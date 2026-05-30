package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
fun RegisterStoreScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var gstin by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isExistingStore by remember { mutableStateOf(false) }

    // Pre-fill phone from session if available
    LaunchedEffect(Unit) {
        SessionManager.load(context)
        val storedPhone = SessionManager.phone
        if (!storedPhone.isNullOrBlank()) {
            val digits = storedPhone.filter { it.isDigit() }
            if (digits.length >= 10) {
                phone = digits.takeLast(10)
            }
        }
    }

    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is OperationState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                // Navigate back to login screen — the storeId is now saved in SessionManager.
                // LoginScreen's LaunchedEffect will read it and pre-fill the phone.
                onSuccess()
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                isExistingStore = state.message.contains("already exists", ignoreCase = true) ||
                                  state.message.contains("already registered", ignoreCase = true)
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    val isValid = businessName.isNotBlank() && ownerName.isNotBlank() && phone.length >= 10 &&
                  password.length >= 4 && password == confirmPassword

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Store", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
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

                Text(
                    text = if (isExistingStore) "Store Already Registered" else "Setup Your Business",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = if (isExistingStore)
                        "Your store is already registered. You can update your details below."
                    else
                        "Enter your business details to get started with Grahbook",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Form Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Business Name
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Business Name *") },
                            placeholder = { Text("e.g. Sharma Electronics") },
                            leadingIcon = { Icon(Icons.Outlined.Store, contentDescription = null, tint = StitchTeal) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Owner Name
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Owner Name *") },
                            placeholder = { Text("e.g. Ram Sharma") },
                            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = StitchTeal) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Phone
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { newVal ->
                                if (newVal.length <= 10 && newVal.all { it.isDigit() }) {
                                    phone = newVal
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Phone Number *") },
                            placeholder = { Text("9876543210") },
                            leadingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("+91", fontWeight = FontWeight.Bold, color = StitchTeal, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    HorizontalDivider(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(20.dp),
                                        color = CardBorder
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Outlined.Phone, contentDescription = null, tint = StitchTeal, modifier = Modifier.size(18.dp))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Password
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Password *") },
                            placeholder = { Text("At least 4 characters") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = StitchTeal) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondaryLight
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Confirm Password
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm Password *") },
                            placeholder = { Text("Re-enter your password") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = StitchTeal) },
                            isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                            supportingText = {
                                if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                                    Text("Passwords do not match", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Email (optional)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Email (optional)") },
                            placeholder = { Text("your@email.com") },
                            leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = StitchTeal) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // Address
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Address (optional)") },
                            placeholder = { Text("Your business address") },
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = StitchTeal) },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        // GSTIN
                        OutlinedTextField(
                            value = gstin,
                            onValueChange = { newVal ->
                                if (newVal.length <= 15 && newVal.all { it.isLetterOrDigit() }) {
                                    gstin = newVal.uppercase()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("GSTIN (optional)") },
                            placeholder = { Text("22AAAAA0000A1Z5") },
                            leadingIcon = { Icon(Icons.Outlined.Badge, contentDescription = null, tint = StitchTeal) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = StitchTeal,
                                unfocusedIndicatorColor = CardBorder,
                                focusedContainerColor = BackgroundLight,
                                unfocusedContainerColor = BackgroundLight
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Register Button
                        Button(
                            onClick = {
                                isExistingStore = false
                                viewModel.registerStore(
                                    storeName = businessName,
                                    ownerName = ownerName,
                                    phone = "+91$phone",
                                    email = email.ifBlank { "" },
                                    address = address.ifBlank { null },
                                    gstin = gstin.ifBlank { null },
                                    password = password,
                                    context = context
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = isValid && operationState !is OperationState.Loading,
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
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Register & Continue", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
