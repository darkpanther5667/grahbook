package com.aistudio.sharmakhata.pqmzvk

import android.content.Context
import android.content.Intent
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aistudio.sharmakhata.pqmzvk.ui.screens.BillCreationScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.BillsScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.CustomerDetailScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.CustomersScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.DashboardScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.LedgerScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.LoadingScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.PaymentRecordingScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.WebViewScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.AddCustomerScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.LoginScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.RegisterStoreScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.SearchScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.ItemsScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.AddEditItemScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.QuickBillScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.ExpensesScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.AddExpenseScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.ReportsScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.FinancialReportsScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.PurchasesScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.AddPurchaseScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.PurchaseDetailScreen
import com.aistudio.sharmakhata.pqmzvk.data.local.PurchaseItemEntry
import com.aistudio.sharmakhata.pqmzvk.ui.screens.GstReturnsScreen
import com.aistudio.sharmakhata.pqmzvk.ui.screens.InvoiceTemplateScreen
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppSidebarDrawer
import com.aistudio.sharmakhata.pqmzvk.ui.components.ShimmerLoading
import com.aistudio.sharmakhata.pqmzvk.ui.components.EmptyState
import com.aistudio.sharmakhata.pqmzvk.util.FormatUtils
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.*
import com.aistudio.sharmakhata.pqmzvk.ui.theme.GrahbookTheme
import com.aistudio.sharmakhata.pqmzvk.ui.theme.*
import com.aistudio.sharmakhata.pqmzvk.BuildConfig
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import com.aistudio.sharmakhata.pqmzvk.util.BiometricAuthHelper
import androidx.fragment.app.FragmentActivity
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
      val biometricUnlocked by BiometricAuthHelper.authState.collectAsState()
      val isUnlocked = !BiometricAuthHelper.isAvailable(context) || biometricUnlocked

      // Trigger biometric prompt when the composable tree is ready
      LaunchedEffect(Unit) {
        try {
          if (BiometricAuthHelper.isAvailable(context) &&
              !BiometricAuthHelper.isAuthenticated(context)) {
            BiometricAuthHelper.showPrompt(context as FragmentActivity)
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
                BiometricAuthHelper.showPrompt(context as FragmentActivity)
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
        GrahbookTheme(darkTheme = isDarkTheme) {
          AppNavigation(isDarkTheme = isDarkTheme, onToggleTheme = toggleTheme)
        }
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

@Composable
fun AppNavigation(viewModel: MainViewModel = hiltViewModel(), isDarkTheme: Boolean, onToggleTheme: (Boolean) -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()
    var currentScreen by remember { mutableStateOf("dashboard") }
    val startDestination = if (SessionManager.token.isNullOrBlank()) "login" else "loading"

  // Observe 401 unauthorized events from the server
  val logoutEvent by viewModel.logoutEvent.collectAsState()
  LaunchedEffect(logoutEvent) {
    if (logoutEvent) {
      SessionManager.clear(context)
      currentScreen = "login"
      navController.navigate("login") {
        popUpTo(0) { inclusive = true }
      }
      viewModel.consumeLogoutEvent()
    }
  }

  val drawerState = rememberDrawerState(DrawerValue.Closed)
  val scope = rememberCoroutineScope()
  val dbState by viewModel.dbState.collectAsState()
  val shopName = when (val s = dbState) {
    is UiState.Success -> s.data.shop?.name ?: "My Store"
    else -> "My Store"
  }
  val shopOwner = when (val s = dbState) {
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

  val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

  val drawerNavigate: (String) -> Unit = { screen ->
    scope.launch { drawerState.close() }
    when (screen) {
      "logout" -> {
        SessionManager.clear(context)
        currentScreen = "login"
        navController.navigate("login") {
          popUpTo(0) { inclusive = true }
        }
      }
      "share" -> {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_TEXT, "Download Grahbook - Billing App\nhttps://wpapp-xz9l.onrender.com")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
      }
      else -> {
        currentScreen = screen
        navController.navigate(screen) {
          popUpTo(navController.graph.startDestinationId) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }
    }
  }

  val isMainApp = currentScreen != "login" && currentScreen != "loading" && currentScreen != "register_store"

  val navContent: @Composable () -> Unit = {
    Scaffold(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding(),
      bottomBar = {
        if (isMainApp) {
          BottomNavigationBar(
            currentScreen = currentScreen,
            onNavigate = { screen ->
              when (screen) {
                "dashboard" -> navController.navigate("dashboard") {
                  popUpTo("dashboard") { inclusive = true }
                  launchSingleTop = true
                }
                "customers" -> navController.navigate("customers") {
                  popUpTo("customers") { inclusive = true }
                  launchSingleTop = true
                }
                "bills" -> navController.navigate("bills") {
                  popUpTo("bills") { inclusive = true }
                  launchSingleTop = true
                }
                "profile" -> navController.navigate("profile") {
                  popUpTo("profile") { inclusive = true }
                  launchSingleTop = true
                }
              }
              currentScreen = screen
            }
          )
        }
      }
    ) { padding ->
      NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = Modifier.padding(padding)
      ) {
      composable("login") {
        LoginScreen(
          viewModel = viewModel,
          onLoggedIn = {
            currentScreen = "loading"
            navController.navigate("loading") {
              popUpTo("login") { inclusive = true }
              launchSingleTop = true
            }
          },
          onRegisterStore = {
            currentScreen = "register_store"
            navController.navigate("register_store")
          }
        )
      }
      composable("register_store") {
        RegisterStoreScreen(
          viewModel = viewModel,
          onBack = {
            currentScreen = "login"
            navController.popBackStack()
          },
          onSuccess = {
            // After registration, take user back to login with storeId shown in snackbar.
            currentScreen = "login"
            navController.popBackStack()
          }
        )
      }
      composable("loading") {
        LoadingScreen(
          viewModel = viewModel,
          onReady = {
            currentScreen = "dashboard"
            navController.navigate("dashboard") {
              popUpTo("loading") { inclusive = true }
              launchSingleTop = true
            }
          },
          onBackToLogin = {
            // Clear session and navigate back to login
            SessionManager.clear(context)
            currentScreen = "login"
            navController.navigate("login") {
              popUpTo("loading") { inclusive = true }
              launchSingleTop = true
            }
          }
        )
      }
      composable("dashboard") {
        val dashVm: DashboardViewModel = hiltViewModel()
        DashboardScreen(
          viewModel = dashVm,
          onNavigateToCustomers = {
            currentScreen = "customers"
            navController.navigate("customers")
          },
          onNavigateToWebView = {
            currentScreen = "webview"
            navController.navigate("webview")
          },
          onCreateInvoice = {
            currentScreen = "customers"
            navController.navigate("customers")
          },
          onAddCustomer = {
            currentScreen = "add_customer"
            navController.navigate("add_customer")
          },
          onRecordPayment = {
            currentScreen = "customers"
            navController.navigate("customers")
          },
          onViewReports = {
            currentScreen = "reports"
            navController.navigate("reports")
          },
          onSendReminder = {
            currentScreen = "customers"
            navController.navigate("customers")
          },
          onWhatsApp = {
            currentScreen = "webview"
            navController.navigate("webview")
          },
          onMenuClick = openDrawer
        )
      }
      composable("reports") {
        val reportsVm: ReportsViewModel = hiltViewModel()
        val expenseVm: ExpenseViewModel = hiltViewModel()
        val expList by expenseVm.expenses.collectAsState()
        LaunchedEffect(Unit) { expenseVm.loadExpenses() }
        ReportsScreen(
          viewModel = reportsVm,
          expenses = expList,
          onBack = {
            currentScreen = "dashboard"
            navController.popBackStack()
          },
          onMenuClick = openDrawer
        )
      }
      composable("financial_reports") {
        val expenseVm: ExpenseViewModel = hiltViewModel()
        val reportsVm: ReportsViewModel = hiltViewModel()
        val dbState by reportsVm.dbState.collectAsState()
        val expList by expenseVm.expenses.collectAsState()
        LaunchedEffect(Unit) { expenseVm.loadExpenses() }
        val db = when (dbState) {
          is UiState.Success -> (dbState as UiState.Success).data
          else -> null
        }
        FinancialReportsScreen(
          bills = db?.bills ?: emptyList(),
          transactions = db?.transactions ?: emptyList(),
          expenses = expList,
          customers = db?.customers ?: emptyList(),
          onBack = { navController.popBackStack() }
        )
      }
      composable("customers") {
        val custVm: CustomerViewModel = hiltViewModel()
        CustomersScreen(
          viewModel = custVm,
          onBack = {
            currentScreen = "dashboard"
            navController.popBackStack()
          },
          onAddCustomer = {
            currentScreen = "add_customer"
            navController.navigate("add_customer")
          },
          onCustomerClick = { customerId ->
            currentScreen = "customer_detail"
            navController.navigate("customer_detail/$customerId")
          },
          onNavigateToSearch = {
            navController.navigate("search")
          },
          onMenuClick = openDrawer
        )
      }
      composable("add_customer") {
        val custVm: CustomerViewModel = hiltViewModel()
        AddCustomerScreen(
          viewModel = custVm,
          onBack = {
            currentScreen = "customers"
            navController.popBackStack()
          }
        )
      }
      composable("customer_detail/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val custVm: CustomerViewModel = hiltViewModel()
        val billingVm: BillingViewModel = hiltViewModel()
        val custVmDb by custVm.dbState.collectAsState()
        val customerName = when (custVmDb) {
          is UiState.Success -> {
            val db = (custVmDb as UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }

        CustomerDetailScreen(
          customerVm = custVm,
          billingVm = billingVm,
          customerId = customerId,
          onBack = {
            currentScreen = "customers"
            navController.popBackStack()
          },
          onAddPayment = {
            currentScreen = "payment"
            navController.navigate("payment/$customerId")
          },
          onCreateBill = {
            currentScreen = "bill"
            navController.navigate("bill/$customerId")
          },
          onViewBills = {
            currentScreen = "bills"
            navController.navigate("bills/$customerId")
          },
          onViewLedger = {
            currentScreen = "ledger"
            navController.navigate("ledger/$customerId")
          }
        )
      }
      composable("payment/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val payVm: PaymentViewModel = hiltViewModel()
        val payVmDb by payVm.dbState.collectAsState()
        val customerName = when (payVmDb) {
          is UiState.Success -> {
            val db = (payVmDb as UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }

        PaymentRecordingScreen(
          viewModel = payVm,
          customerId = customerId,
          customerName = customerName,
          onBack = {
            currentScreen = "customer_detail"
            navController.popBackStack()
          }
        )
      }
      composable("bill/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val billingVm: BillingViewModel = hiltViewModel()
        val billingVmDb by billingVm.dbState.collectAsState()
        val customerName = when (billingVmDb) {
          is UiState.Success -> {
            val db = (billingVmDb as UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }

        BillCreationScreen(
          viewModel = billingVm,
          customerId = customerId,
          customerName = customerName,
          onBack = {
            currentScreen = "customer_detail"
            navController.popBackStack()
          }
        )
      }
      composable("ledger/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val custVm: CustomerViewModel = hiltViewModel()
        LedgerScreen(
          viewModel = custVm,
          customerId = customerId,
          onBack = {
            currentScreen = "customer_detail"
            navController.popBackStack()
          }
        )
      }
      composable("bills/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val billingVm: BillingViewModel = hiltViewModel()
        BillsScreen(
          viewModel = billingVm,
          customerId = customerId,
          onBack = {
            currentScreen = "customer_detail"
            navController.popBackStack()
          },
          onOpenPdf = { billId ->
            currentScreen = "webview"
            navController.navigate("webview_pdf/$billId")
          }
        )
      }
      composable("webview_pdf/{billId}",
        arguments = listOf(navArgument("billId") { type = NavType.StringType })
      ) { backStackEntry ->
        val billId = backStackEntry.arguments?.getString("billId") ?: ""
        WebViewScreen(
          url = "https://wpapp-xz9l.onrender.com/api/bill/$billId/pdf",
          onBack = {
            currentScreen = "bills"
            navController.popBackStack()
          }
        )
      }
      composable("webview") {
        WebViewScreen(
          url = "https://wpapp-xz9l.onrender.com",
          onBack = {
            currentScreen = "dashboard"
            navController.popBackStack()
          }
        )
      }
      composable("bills") {
        BillsOverviewScreen(
          viewModel = viewModel,
          onCustomerClick = { customerId ->
            currentScreen = "customer_detail"
            navController.navigate("customer_detail/$customerId")
          },
          onNavigateToSearch = {
            navController.navigate("search")
          }
        )
      }
      composable("profile") {
        ProfileScreen(
          viewModel = viewModel,
          isDarkTheme = isDarkTheme,
          onToggleTheme = onToggleTheme,
          onLogout = {
            currentScreen = "login"
            SessionManager.clear(context)
            navController.navigate("login") {
              popUpTo("dashboard") { inclusive = true }
              launchSingleTop = true
            }
          }
        )
      }
      composable("search") {
        val custVm: CustomerViewModel = hiltViewModel()
        SearchScreen(
          viewModel = custVm,
          onBack = {
            navController.popBackStack()
          },
          onCustomerClick = { customerId ->
            currentScreen = "customer_detail"
            navController.navigate("customer_detail/$customerId")
          },
          onBillClick = { customerId, billId ->
            currentScreen = "bills"
            navController.navigate("bills/$customerId")
          }
        )
      }
      composable("items") {
        val invVm: InventoryViewModel = hiltViewModel()
        val items by invVm.items.collectAsState()
        LaunchedEffect(Unit) { invVm.loadItems() }
        ItemsScreen(
          items = items,
          onBack = { navController.popBackStack() },
          onAddItem = { navController.navigate("add_item") },
          onEditItem = { id -> navController.navigate("edit_item/$id") },
          onDeleteItem = { id -> invVm.deleteItem(id) },
          onRefresh = { invVm.refreshItems() },
          isLoading = false,
          onMenuClick = openDrawer
        )
      }
      composable("add_item") {
        val invVm: InventoryViewModel = hiltViewModel()
        AddEditItemScreen(
          onBack = { navController.popBackStack() },
          onSave = { name, price, stock, alert ->
            invVm.saveItem(name, price, stock, alert)
            navController.popBackStack()
          }
        )
      }
      composable("edit_item/{itemId}", arguments = listOf(navArgument("itemId") { type = NavType.LongType })) { entry ->
        val itemId = entry.arguments?.getLong("itemId") ?: return@composable
        val invVm: InventoryViewModel = hiltViewModel()
        val items by invVm.items.collectAsState()
        val item = items.find { it.id == itemId }
        AddEditItemScreen(
          itemId = itemId,
          existingItem = item,
          onBack = { navController.popBackStack() },
          onSave = { name, price, stock, alert ->
            invVm.saveItem(name, price, stock, alert, itemId)
            navController.popBackStack()
          },
          onDelete = { id -> invVm.deleteItem(id); navController.popBackStack() }
        )
      }
      composable("quick_bill") {
        val billingVm: BillingViewModel = hiltViewModel()
        val storedItems by billingVm.storedItems.collectAsState()
        val operationState by billingVm.operationState.collectAsState()
        val lastBillId by billingVm.lastCreatedBillId.collectAsState()
        LaunchedEffect(Unit) { billingVm.loadStoredItems() }
        QuickBillScreen(
          onBack = { navController.popBackStack() },
          onCreateBill = { name, phone, total, items ->
            billingVm.quickBill(context, name, phone, total, items)
          },
          operationState = operationState,
          lastBillId = lastBillId,
          storedItems = storedItems,
          onResetOperation = { billingVm.resetOperationState() },
          onSendWhatsApp = { billId -> billingVm.sendInvoiceOnWhatsApp(context, billId) }
        )
      }
      composable("expenses") {
        val expVm: ExpenseViewModel = hiltViewModel()
        val expenses by expVm.expenses.collectAsState()
        val todayTotal by expVm.todayTotal.collectAsState()
        LaunchedEffect(Unit) { expVm.loadExpenses(); expVm.loadTodayTotal() }
        ExpensesScreen(
          expenses = expenses,
          todayTotal = todayTotal,
          onBack = { navController.popBackStack() },
          onAddExpense = { navController.navigate("add_expense") },
          onDeleteExpense = { id -> expVm.deleteExpense(id) },
          onMenuClick = openDrawer
        )
      }
      composable("add_expense") {
        val expVm: ExpenseViewModel = hiltViewModel()
        AddExpenseScreen(
          onBack = { navController.popBackStack() },
          onSave = { title, amount, category, note ->
            expVm.saveExpense(title, amount, category, note)
            navController.popBackStack()
          }
        )
      }
      composable("purchases") {
        val purchVm: PurchaseViewModel = hiltViewModel()
        val purchases by purchVm.purchases.collectAsState()
        LaunchedEffect(Unit) { purchVm.loadPurchases() }
        PurchasesScreen(
          purchases = purchases,
          onMenuClick = openDrawer,
          onBack = { navController.popBackStack() },
          onAddPurchase = { navController.navigate("add_purchase") },
          onPurchaseClick = { id -> navController.navigate("purchase_detail/$id") },
          onDeletePurchase = { id -> purchVm.deletePurchase(id) },
          onRefresh = { purchVm.loadPurchases() },
          isLoading = false
        )
      }
      composable("add_purchase") {
        val purchVm: PurchaseViewModel = hiltViewModel()
        AddPurchaseScreen(
          onBack = { navController.popBackStack() },
          onSave = { name, phone, items, total, paid, notes ->
            purchVm.savePurchase(name, phone, items, total, paid, notes)
            navController.popBackStack()
          }
        )
      }
      composable("purchase_detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
        val id = entry.arguments?.getLong("id") ?: return@composable
        val purchVm: PurchaseViewModel = hiltViewModel()
        val purchases by purchVm.purchases.collectAsState()
        val purchase = purchases.find { it.id == id }
        PurchaseDetailScreen(
          purchase = purchase,
          onBack = { navController.popBackStack() },
          onDelete = { purchVm.deletePurchase(it); navController.popBackStack() }
        )
      }
      composable("gst_returns") {
        val reportsVm: ReportsViewModel = hiltViewModel()
        val dbState by reportsVm.dbState.collectAsState()
        val bills = when (val s = dbState) {
          is UiState.Success -> s.data.bills
          else -> emptyList()
        }
        GstReturnsScreen(
          bills = bills,
          onBack = { navController.popBackStack() }
        )
      }
      composable("invoice_templates") {
        val purchVm: PurchaseViewModel = hiltViewModel()
        InvoiceTemplateScreen(
          onBack = { navController.popBackStack() },
          onTemplateSelected = { purchVm.setInvoiceTemplate(it) }
        )
      }
    }
  }
  }

  if (isMainApp) {
    AppSidebarDrawer(
      drawerState = drawerState,
      currentScreen = currentScreen,
      shopName = shopName,
      shopOwner = shopOwner,
      customerCount = customerCount,
      billCount = billCount,
      onNavigate = drawerNavigate,
      onLogout = {
        SessionManager.clear(context)
        currentScreen = "login"
        navController.navigate("login") {
          popUpTo(0) { inclusive = true }
        }
      }
    ) {
      navContent()
    }
  } else {
    navContent()
  }
}
@Composable
fun BottomNavigationBar(
  currentScreen: String,
  onNavigate: (String) -> Unit
) {
  val items = listOf(
    BottomNavItem("dashboard", "Home", Icons.Default.Home),
    BottomNavItem("customers", "Customers", Icons.Default.Group),
    BottomNavItem("bills", "Bills", Icons.Default.Receipt),
    BottomNavItem("profile", "Profile", Icons.Default.Person)
  )

  NavigationBar(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp
  ) {
    items.forEach { item ->
      val selected = currentScreen == item.screen
      NavigationBarItem(
        selected = selected,
        onClick = { onNavigate(item.screen) },
        icon = {
          Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) IndigoPrimary else Slate400
          )
        },
        label = {
          Text(
            text = item.label,
            color = if (selected) IndigoPrimary else Slate500,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = IndigoPrimary,
          selectedTextColor = IndigoPrimary,
          unselectedIconColor = Slate400,
          unselectedTextColor = Slate500,
          indicatorColor = IndigoContainer
        )
      )
    }
  }
}

data class BottomNavItem(
  val screen: String,
  val label: String,
  val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsOverviewScreen(
  viewModel: MainViewModel,
  onCustomerClick: (String) -> Unit,
  onNavigateToSearch: () -> Unit = {}
) {
  val dbState by viewModel.dbState.collectAsState()
  val context = LocalContext.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Bills Overview") },
        actions = {
          IconButton(onClick = onNavigateToSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    }
  ) { padding ->
    val pullToRefreshState = rememberPullToRefreshState()
    
    PullToRefreshBox(
      state = pullToRefreshState,
      isRefreshing = dbState is UiState.Loading,
      onRefresh = { viewModel.fetchData(context) },
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      when (dbState) {
        is UiState.Loading -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .align(Alignment.Center),
            contentAlignment = Alignment.Center
          ) {
            ShimmerLoading()
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .align(Alignment.Center)
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            EmptyState(
              message = "Error: ${(dbState as UiState.Error).message}",
              description = "There was an error loading bills data.",
              icon = Icons.Default.Error
            )
          }
        }
        is UiState.Success -> {
          val db = (dbState as UiState.Success).data
          if (db.bills.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
              contentAlignment = Alignment.Center
            ) {
              EmptyState(
                message = "No bills yet",
                description = "Bills will appear here once created.",
                icon = Icons.Default.Receipt
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              items(db.bills.take(20)) { bill ->
                BillOverviewCard(bill, onCustomerClick)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun BillOverviewCard(
  bill: com.aistudio.sharmakhata.pqmzvk.data.model.Bill,
  onCustomerClick: (String) -> Unit
) {
  val isPaid = bill.status == "paid"
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onCustomerClick(bill.customerId) },
    shape = CardShape,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = Elevation.low)
  ) {
    Row(
      modifier = Modifier.padding(Spacing.large),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(ComponentSize.iconContainerLarge)
          .clip(ActionIconShape)
          .background(if (isPaid) SuccessGreen.copy(alpha = 0.1f) else IndigoContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.Default.Receipt,
          contentDescription = null,
          tint = if (isPaid) SuccessGreen else IndigoPrimary,
          modifier = Modifier.size(IconSize.small)
        )
      }
      
      Spacer(modifier = Modifier.width(Spacing.large))
      
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Bill #${bill.id.take(8).uppercase()}",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = FormatUtils.formatCurrency(bill.total),
          style = AmountSmallStyle,
          color = if (isPaid) SuccessGreen else MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = FormatUtils.formatDate(bill.createdAt),
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondaryLight
        )
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

