package com.aistudio.sharmakhata.pqmzvk.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.MainViewModel
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.UiState

@Composable
fun LoadingScreen(
    viewModel: MainViewModel,
    onReady: () -> Unit,
    onBackToLogin: () -> Unit = {},
) {
    val dbState by viewModel.dbState.collectAsState()
    val reportState by viewModel.reportState.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    var hasTimedOut by remember { mutableStateOf(false) }
    var showSkeleton by remember { mutableStateOf(true) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.fetchData(context)
        // Show skeleton for 2 seconds, then show actual loading
        kotlinx.coroutines.delay(2000)
        showSkeleton = false
        // Add timeout: if no success after 120 seconds, show error (account for Render cold start)
        kotlinx.coroutines.delay(118000)
        if (dbState !is UiState.Success || reportState !is UiState.Success) {
            hasTimedOut = true
        }
    }

    LaunchedEffect(dbState, reportState) {
        if (dbState is UiState.Success && reportState is UiState.Success) {
            kotlinx.coroutines.delay(500) // Small delay for smooth transition
            onReady()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            StitchTeal.copy(alpha = 0.05f),
                            Color.White,
                            StitchTeal.copy(alpha = 0.02f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Logo Animation
                AnimatedLogo()
                
                Spacer(modifier = Modifier.height(24.dp))

                when {
                    hasTimedOut || dbState is UiState.Error || reportState is UiState.Error || syncError != null -> {
                        ErrorState(
                            error = listOfNotNull(
                                (dbState as? UiState.Error)?.message,
                                (reportState as? UiState.Error)?.message,
                                syncError,
                                if (hasTimedOut) "Sync timed out. The server may be waking up (cold start). Please try again." else null
                            ).joinToString("\n"),
                            onRetry = {
                                hasTimedOut = false
                                showSkeleton = true
                                viewModel.fetchData(context)
                            },
                            onBackToLogin = {
                                viewModel.resetOperationState()
                                onBackToLogin()
                            }
                        )
                    }
                    showSkeleton -> {
                        LoadingSkeleton()
                    }
                    else -> {
                        SyncingAnimation()
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(StitchTeal, StitchTealDark)
                )
            )
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Storefront,
            contentDescription = "Grahbook Logo",
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )
    }
}

@Composable
fun LoadingSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title Skeleton
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Slate200)
        )
        
        // Subtitle Skeleton
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Slate200)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Progress indicator
        CircularProgressIndicator(
            color = StitchTeal,
            strokeWidth = 3.dp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Loading your workspace...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryLight
        )
    }
}

@Composable
fun SyncingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "sync")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    CircularProgressIndicator(
        color = StitchTeal,
        strokeWidth = 4.dp,
        modifier = Modifier.size(48.dp)
    )
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Text(
        text = "Syncing your data…",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondaryLight,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Animated dots
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(StitchTeal.copy(alpha = alpha))
            )
        }
    }
}

@Composable
fun ErrorState(
    error: String,
    onRetry: () -> Unit,
    onBackToLogin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = "Sync Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Sync Failed",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StitchTeal
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry")
                }
                
                OutlinedButton(
                    onClick = onBackToLogin,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StitchTeal)
                ) {
                    Text("Back to Login")
                }
            }
        }
    }
}
