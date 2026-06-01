package com.aistudio.sharmakhata.pqmzvk.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.util.DownloadState
import com.aistudio.sharmakhata.pqmzvk.util.UpdateInfo

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.ui.platform.LocalUriHandler

@Composable
fun UpdateAvailableDialog(
    updateInfo: UpdateInfo,
    downloadState: DownloadState,
    onUpdate: () -> Unit,
    onDismiss: (() -> Unit)?   // null = mandatory, cannot be dismissed
) {
    val isDownloading = downloadState is DownloadState.Downloading
    val isDone = downloadState is DownloadState.Done
    val isError = downloadState is DownloadState.Error
    val progress = (downloadState as? DownloadState.Downloading)?.progress ?: 0
    val errorMsg = (downloadState as? DownloadState.Error)?.message

    // Pulse animation on the update icon
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconPulse"
    )

    val uriHandler = LocalUriHandler.current

    Dialog(
        onDismissRequest = { if (!updateInfo.isMandatory && !isDownloading) onDismiss?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.isMandatory && !isDownloading,
            dismissOnClickOutside = !updateInfo.isMandatory && !isDownloading
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // ── Icon with gradient badge ───────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(StitchTeal, Brand500))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // ── Title ─────────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "v${updateInfo.latestVersionName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = StitchTeal,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // ── Release notes ─────────────────────────────────────────────
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start
                        )
                    }
                }

                // ── Download progress ─────────────────────────────────────────
                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = StitchTeal,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Downloading update…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$progress%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StitchTeal
                            )
                        }
                    }
                }

                // ── Error message ─────────────────────────────────────────────
                if (isError && errorMsg != null) {
                    Text(
                        text = "⚠️ $errorMsg",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        textAlign = TextAlign.Center
                    )
                }

                // ── Done banner ───────────────────────────────────────────────
                if (isDone) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StitchTeal.copy(alpha = 0.12f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "✅ Download complete! Follow the installer prompt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StitchTeal,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // ── Action buttons ────────────────────────────────────────────
                if (!isDownloading && !isDone) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StitchTeal)
                        ) {
                            Text(
                                text = if (isError) "Retry Download" else "Update Now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                        }

                        if (onDismiss != null && !updateInfo.isMandatory) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Remind me later",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Text(
                                "This update is required to continue using the app.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ErrorRed,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Browser fallback for users whose in-app download fails
                            TextButton(
                                onClick = { uriHandler.openUri(updateInfo.apkUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Download via Browser instead",
                                    fontSize = 12.sp,
                                    color = StitchTeal
                                )
                            }
                        }
                    }
                }

                if (isDone) {
                    TextButton(onClick = { onDismiss?.invoke() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
