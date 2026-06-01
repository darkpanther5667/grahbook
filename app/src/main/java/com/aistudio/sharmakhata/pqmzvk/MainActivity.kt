package com.aistudio.sharmakhata.pqmzvk

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import com.aistudio.sharmakhata.pqmzvk.ui.screens.*
import com.aistudio.sharmakhata.pqmzvk.ui.onboarding.OnboardingScreen
import com.aistudio.sharmakhata.pqmzvk.ui.onboarding.isOnboardingComplete
import com.aistudio.sharmakhata.pqmzvk.ui.onboarding.setOnboardingComplete
import com.aistudio.sharmakhata.pqmzvk.navigation.AppNavGraph
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerLoading
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.*
import com.aistudio.sharmakhata.pqmzvk.ui.theme.GrahbookTheme
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.BuildConfig
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import com.aistudio.sharmakhata.pqmzvk.util.BiometricAuthHelper
import com.aistudio.sharmakhata.pqmzvk.ui.components.UpdateAvailableDialog
import com.aistudio.sharmakhata.pqmzvk.util.AppUpdateManager
import com.aistudio.sharmakhata.pqmzvk.util.UpdateInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  companion object {
    private const val PREFS_NAME = "grahbook_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Request notification permission on Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      val permission = android.Manifest.permission.POST_NOTIFICATIONS
      if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(permission), 1001)
      }
    }

    // Load saved theme preference
    val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val savedDarkMode = sharedPrefs.getBoolean(KEY_DARK_MODE, true)

    setContent {
      val context = LocalContext.current
      var isDarkTheme by remember { mutableStateOf(savedDarkMode) }
      var onboardingComplete by remember { mutableStateOf(isOnboardingComplete(context)) }
      val biometricUnlocked by BiometricAuthHelper.authState.collectAsState()
      val isUnlocked = !BiometricAuthHelper.isAvailable(context) || biometricUnlocked

      // OTA update state
      var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
      var showUpdateDialog by remember { mutableStateOf(false) }
      val downloadState by AppUpdateManager.downloadState.collectAsState()
      val updateScope = rememberCoroutineScope()

      // Check for update once on launch (silently in background)
      LaunchedEffect(Unit) {
        updateScope.launch {
          val update = AppUpdateManager.checkForUpdate()
          if (update != null) {
            pendingUpdate = update
            showUpdateDialog = true
          }
        }
      }

      // Trigger biometric prompt (safe cast — ComponentActivity is not FragmentActivity,
      // so this silently skips on devices/activities where biometric dialog isn't supported)
      LaunchedEffect(Unit) {
        try {
          val fa = context as? androidx.fragment.app.FragmentActivity
          if (fa != null && BiometricAuthHelper.isAvailable(fa) &&
              !BiometricAuthHelper.isAuthenticated(fa)) {
            BiometricAuthHelper.showPrompt(fa)
          }
        } catch (e: Exception) {
          android.util.Log.w("MainActivity", "Biometric prompt failed (non-fatal)", e)
        }
      }

      if (BiometricAuthHelper.isAvailable(context) && !isUnlocked) {
        // Lock screen while waiting for biometric
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
          contentAlignment = Alignment.Center
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            Icon(
              imageVector = Icons.Default.DarkMode,
              contentDescription = null,
              modifier = Modifier.size(64.dp),
              tint = StitchTeal
            )
            Text(
              text = "Grahbook Pro",
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "Verify your identity to continue",
              style = MaterialTheme.typography.bodyLarge,
              color = TextSecondaryLight
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
              onClick = {
                val fa = context as? androidx.fragment.app.FragmentActivity
                if (fa != null) BiometricAuthHelper.showPrompt(fa)
              }
            ) {
              Text("Tap to retry")
            }
          }
        }
      } else {
        val toggleTheme: (Boolean) -> Unit = {
          isDarkTheme = it
          sharedPrefs.edit().putBoolean(KEY_DARK_MODE, it).apply()
        }

        if (!onboardingComplete) {
          val shopNamePref = context.getSharedPreferences("grahbook_session", Context.MODE_PRIVATE)
              .getString("store_name", "") ?: ""
          OnboardingScreen(
            shopName = shopNamePref,
            onComplete = { state ->
              setOnboardingComplete(context)
              onboardingComplete = true
            },
            onSkip = {
              setOnboardingComplete(context)
              onboardingComplete = true
            }
          )
        } else {
          GrahbookTheme(darkTheme = isDarkTheme) {
            AppNavGraph(
              isDarkTheme = isDarkTheme,
              onToggleTheme = toggleTheme
            )
          }
        }
      }

      // ── OTA Update Dialog ───────────────────────────────────────────────────
      if (showUpdateDialog && pendingUpdate != null) {
        UpdateAvailableDialog(
          updateInfo = pendingUpdate!!,
          downloadState = downloadState,
          onUpdate = {
            updateScope.launch {
              AppUpdateManager.downloadAndInstall(context, pendingUpdate!!)
            }
          },
          onDismiss = {
            showUpdateDialog = false
            AppUpdateManager.resetDownloadState()
          }
        )
      }
    }
  }
}

@Composable
fun BiometricLockScreen(
  onUnlocked: () -> Unit,
  onFallback: () -> Unit
) {
  var showFallback by remember { mutableStateOf(false) }
  val localContext = LocalContext.current

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Icon(
        imageVector = Icons.Default.DarkMode,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = StitchTeal
      )
      Text(
        text = "Grahbook Pro",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )
      Text(
        text = "Verify your identity to continue",
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondaryLight
      )
      Spacer(modifier = Modifier.height(24.dp))
      Button(
        onClick = {
          BiometricAuthHelper.setAuthenticated(localContext, true)
          onUnlocked()
        },
        shape = RoundedCornerShape(12.dp)
      ) {
        Text("Unlock with Fingerprint")
      }
      if (showFallback) {
        TextButton(onClick = onFallback) {
          Text("Use password instead")
        }
      }
    }
  }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  viewModel: MainViewModel,
  isDarkTheme: Boolean,
  onToggleTheme: (Boolean) -> Unit,
  onLogout: () -> Unit
) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val token = SessionManager.token
  val dbState by viewModel.dbState.collectAsState()
  val storeName = when (val s = dbState) {
    is UiState.Success -> s.data.shop?.name ?: "My Store"
    else -> "My Store"
  }
  val ownerName = when (val s = dbState) {
    is UiState.Success -> s.data.shop?.owner ?: ""
    else -> ""
  }
  val customerCount = when (val s = dbState) {
    is UiState.Success -> s.data.customers.size
    else -> 0
  }
  val billCount = when (val s = dbState) {
    is UiState.Success -> s.data.bills.size
    else -> 0
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Stitch-style gradient header
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
        .background(
          Brush.linearGradient(
            colors = listOf(StitchTeal, StitchTealDark),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
          )
        )
    )

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Settings", fontWeight = FontWeight.Bold) },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White
          )
        )
      },
      containerColor = Color.Transparent
    ) { padding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
      ) {
        // Profile info in the gradient area
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = storeName.firstOrNull()?.uppercase() ?: "S",
              color = Color.White,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = storeName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          if (ownerName.isNotBlank()) {
            Text(
              text = ownerName,
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.8f)
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .clip(BadgeShape)
              .background(Color.White.copy(alpha = 0.2f))
              .padding(horizontal = 16.dp, vertical = 4.dp)
          ) {
            Text(
              text = if (token != null) "Active Account" else "Not logged in",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = Color.White
            )
          }
        }

        // White rounded container for settings
        Column(
          modifier = Modifier
            .fillMaxSize()
            .background(
              MaterialTheme.colorScheme.background,
              shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(top = 8.dp)
        ) {
          val scrollState = rememberScrollState()
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Stats row
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(text = customerCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StitchTeal)
                  Text(text = "Customers", style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
                }
              }
              Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(text = billCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = StitchSky)
                  Text(text = "Bills", style = MaterialTheme.typography.labelSmall, color = TextSecondaryLight)
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === SETTINGS SECTIONS ===
            Text(
              text = "PREFERENCES",
              style = SectionOverlineStyle,
              color = TextTertiaryLight,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            // Dark Mode toggle
            SettingsItem(
              icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
              iconTint = StitchTeal,
              title = "Dark Mode",
              subtitle = if (isDarkTheme) "Currently dark" else "Currently light",
              trailing = {
                Switch(
                  checked = isDarkTheme,
                  onCheckedChange = { onToggleTheme(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = StitchTeal,
                    uncheckedThumbColor = Slate400,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                  )
                )
              }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = DividerColor)

            // ABOUT
            Text(
              text = "ABOUT",
              style = SectionOverlineStyle,
              color = TextTertiaryLight,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            SettingsItem(
              icon = Icons.Default.Info,
              iconTint = StitchSky,
              title = "Version",
              subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})"
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp, color = DividerColor)

            SettingsItem(
              icon = Icons.Default.Sync,
              iconTint = StitchTeal,
              title = "Sync Status",
              subtitle = if (token != null) "Active" else "Inactive"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Logout
            OutlinedButton(
              onClick = onLogout,
              modifier = Modifier
                .fillMaxWidth()
                .height(ComponentSize.buttonHeight),
              shape = ButtonShape,
              colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ErrorRed
              ),
              border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(ErrorRed.copy(alpha = 0.5f))
              )
            ) {
              Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(IconSize.small))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Logout", fontWeight = FontWeight.Bold, color = ErrorRed)
            }

            Spacer(modifier = Modifier.height(32.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsItem(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String = "",
  trailing: @Composable (() -> Unit)? = null
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(iconTint.copy(alpha = 0.1f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      if (subtitle.isNotBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondaryLight
        )
      }
    }
    if (trailing != null) {
      trailing()
    }
  }
}

@Composable
fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium
    )
  }
}

