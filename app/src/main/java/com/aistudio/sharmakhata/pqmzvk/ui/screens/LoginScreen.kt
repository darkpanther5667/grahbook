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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.OperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onLoggedIn: () -> Unit,
    onRegisterStore: () -> Unit = {}
) {
    val operationState by viewModel.operationState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var phoneNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var isOtpStage by remember { mutableStateOf(false) }

    LaunchedEffect(operationState) {
        when (val state = operationState) {
            is OperationState.Success -> {
                val msg = state.message
                if (msg == "Logged in") {
                    onLoggedIn()
                } else if (msg == "Code sent on WhatsApp") {
                    isOtpStage = true
                }
            }
            is OperationState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    withDismissAction = true
                )
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
                            IndigoPrimary,
                            IndigoDark,
                            Color(0xFF1E1B4B)
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
                                color = IndigoPrimary
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
                            // Phone Input Stage
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = IndigoPrimary,
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
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Phone Number") },
                                placeholder = { Text("9876543210") },
                                leadingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("+91", fontWeight = FontWeight.Bold, color = IndigoPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(modifier = Modifier.width(1.dp).height(20.dp).background(CardBorder))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = IndigoPrimary,
                                    unfocusedIndicatorColor = CardBorder,
                                    focusedContainerColor = BackgroundLight,
                                    unfocusedContainerColor = BackgroundLight
                                )
                            )

                            Button(
                                onClick = { viewModel.requestLoginCode("", phoneNumber) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = phoneNumber.length == 10 && operationState !is OperationState.Loading,
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
                                    Text("Send OTP", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Register link
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("New here?", color = TextSecondaryLight, fontSize = 13.sp)
                                TextButton(onClick = onRegisterStore) {
                                    Text("Register your store", color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            // OTP Stage
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = IndigoPrimary,
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
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = IndigoPrimary)
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = IndigoPrimary,
                                    unfocusedIndicatorColor = CardBorder,
                                    focusedContainerColor = BackgroundLight,
                                    unfocusedContainerColor = BackgroundLight
                                ),
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 8.sp
                                )
                            )

                            Button(
                                onClick = { viewModel.verifyLoginCode("", phoneNumber, otp, context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                enabled = otp.length == 6 && operationState !is OperationState.Loading,
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
                                    Text("Verify & Login", fontWeight = FontWeight.Bold)
                                }
                            }

                            TextButton(onClick = {
                                isOtpStage = false
                                otp = ""
                                viewModel.requestLoginCode("", phoneNumber)
                            }) {
                                Text("Resend OTP", color = IndigoPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
