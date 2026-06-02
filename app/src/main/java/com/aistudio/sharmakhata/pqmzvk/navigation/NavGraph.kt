package com.aistudio.sharmakhata.pqmzvk.navigation

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aistudio.sharmakhata.pqmzvk.ui.components.AppSidebarDrawer
import com.aistudio.sharmakhata.pqmzvk.ui.components.FloatingBottomNav
import com.aistudio.sharmakhata.pqmzvk.ui.screens.*
import com.aistudio.sharmakhata.pqmzvk.ui.viewmodel.*
import com.aistudio.sharmakhata.pqmzvk.util.SessionManager
import com.aistudio.sharmakhata.pqmzvk.ProfileScreen
import kotlinx.coroutines.launch

private val MAIN_ROUTES = setOf(
    NavRoutes.HOME, NavRoutes.CUSTOMERS, NavRoutes.BILLS,
    NavRoutes.INVENTORY, NavRoutes.REPORTS
)

/** Extract the base tab route from a full route string (handles parameterized routes). */
private fun String?.baseRoute(): String? {
    if (this == null) return null
    return when {
        startsWith("home") -> NavRoutes.HOME
        startsWith("customer") -> NavRoutes.CUSTOMERS
        startsWith("bill") -> NavRoutes.BILLS
        startsWith("inventory") -> NavRoutes.INVENTORY
        startsWith("report") -> NavRoutes.REPORTS
        else -> this
    }
}

@Composable
fun AppNavGraph(
    mainViewModel: MainViewModel = hiltViewModel(),
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    currentLanguage: String = "en",
    onLanguageChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.baseRoute()
    val startDestination = if (SessionManager.token.isNullOrBlank()) NavRoutes.LOGIN else NavRoutes.LOADING
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    // Observe 401 unauthorized events
    val logoutEvent by mainViewModel.logoutEvent.collectAsState()
    LaunchedEffect(logoutEvent) {
        if (logoutEvent) {
            SessionManager.clear(context)
            navController.navigate(NavRoutes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
            mainViewModel.consumeLogoutEvent()
        }
    }

    // Drawer navigation handler
    val drawerNavigate: (String) -> Unit = { screen ->
        scope.launch { drawerState.close() }
        when (screen) {
            "logout" -> {
                SessionManager.clear(context)
                navController.navigate(NavRoutes.LOGIN) {
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
                navController.navigate(screen) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Extract shop info from db state
    val dbState by mainViewModel.dbState.collectAsState()
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

    val isMainApp = currentRoute in MAIN_ROUTES

    val navContent: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            bottomBar = {
                if (isMainApp) {
                    FloatingBottomNav(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
                enterTransition = {
                    fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(220)
                    )
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(220)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(220)
                    )
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(220)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(220)
                    )
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(220)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(220)
                    )
                }
            ) {
                // ===== AUTH ROUTES =====
                composable(NavRoutes.LOGIN) {
                    LoginScreen(
                        viewModel = mainViewModel,
                        onLoggedIn = {
                            navController.navigate(NavRoutes.LOADING) {
                                popUpTo(NavRoutes.LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onRegisterStore = {
                            navController.navigate(NavRoutes.REGISTER_STORE)
                        }
                    )
                }

                composable(NavRoutes.REGISTER_STORE) {
                    RegisterStoreScreen(
                        viewModel = mainViewModel,
                        onBack = { navController.popBackStack() },
                        onSuccess = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.LOADING) {
                    LoadingScreen(
                        viewModel = mainViewModel,
                        onReady = {
                            navController.navigate(NavRoutes.HOME) {
                                popUpTo(NavRoutes.LOADING) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onBackToLogin = {
                            SessionManager.clear(context)
                            navController.navigate(NavRoutes.LOGIN) {
                                popUpTo(NavRoutes.LOADING) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // ===== HOME =====
                composable(NavRoutes.HOME) {
                    val dashVm: DashboardViewModel = hiltViewModel()
                    DashboardScreen(
                        viewModel = dashVm,
                        onNavigateToCustomers = { navController.navigate(NavRoutes.CUSTOMERS) },
                        onNavigateToWebView = { navController.navigate(NavRoutes.WEBVIEW) },
                        onCreateInvoice = { navController.navigate(NavRoutes.CUSTOMERS) },
                        onAddCustomer = { navController.navigate(NavRoutes.ADD_CUSTOMER) },
                        onRecordPayment = { navController.navigate(NavRoutes.CUSTOMERS) },
                        onViewReports = { navController.navigate(NavRoutes.REPORTS) },
                        onSendReminder = { navController.navigate(NavRoutes.CUSTOMERS) },
                        onWhatsApp = { navController.navigate(NavRoutes.WEBVIEW) },
                        onMenuClick = openDrawer
                    )
                }

                // ===== REPORTS =====
                composable(NavRoutes.REPORTS) {
                    val reportsVm: ReportsViewModel = hiltViewModel()
                    val expenseVm: ExpenseViewModel = hiltViewModel()
                    val expList by expenseVm.expenses.collectAsState()
                    LaunchedEffect(Unit) { expenseVm.loadExpenses() }
                    ReportsScreen(
                        viewModel = reportsVm,
                        expenses = expList,
                        onBack = { navController.popBackStack() },
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

                // ===== CUSTOMERS =====
                composable(NavRoutes.CUSTOMERS) {
                    val custVm: CustomerViewModel = hiltViewModel()
                    CustomersScreen(
                        viewModel = custVm,
                        onBack = { navController.popBackStack() },
                        onAddCustomer = { navController.navigate(NavRoutes.ADD_CUSTOMER) },
                        onCustomerClick = { customerId ->
                            navController.navigate(NavRoutes.customerDetail(customerId))
                        },
                        onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH) },
                        onMenuClick = openDrawer
                    )
                }

                composable(NavRoutes.ADD_CUSTOMER) {
                    val custVm: CustomerViewModel = hiltViewModel()
                    AddCustomerScreen(
                        viewModel = custVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NavRoutes.CUSTOMER_DETAIL,
                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
                    val custVm: CustomerViewModel = hiltViewModel()
                    val billingVm: BillingViewModel = hiltViewModel()
                    val custVmDb by custVm.dbState.collectAsState()
                    val customerName = when (custVmDb) {
                        is UiState.Success -> {
                            val db = (custVmDb as UiState.Success).data
                            db.customers.find { it.id == customerId }?.name ?: "Customer"
                        }
                        else -> "Customer"
                    }

                    CustomerDetailScreen(
                        customerVm = custVm,
                        billingVm = billingVm,
                        customerId = customerId,
                        onBack = { navController.popBackStack() },
                        onAddPayment = { navController.navigate("payment/$customerId") },
                        onCreateBill = { navController.navigate("bill/$customerId") },
                        onViewBills = { navController.navigate("bills/$customerId") },
                        onViewLedger = { navController.navigate("ledger/$customerId") }
                    )
                }

                composable(
                    route = "payment/{customerId}",
                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
                    val payVm: PaymentViewModel = hiltViewModel()
                    val payVmDb by payVm.dbState.collectAsState()
                    val customerName = when (payVmDb) {
                        is UiState.Success -> {
                            val db = (payVmDb as UiState.Success).data
                            db.customers.find { it.id == customerId }?.name ?: "Customer"
                        }
                        else -> "Customer"
                    }
                    PaymentRecordingScreen(
                        viewModel = payVm,
                        customerId = customerId,
                        customerName = customerName,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "bill/{customerId}",
                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
                    val billingVm: BillingViewModel = hiltViewModel()
                    val billingVmDb by billingVm.dbState.collectAsState()
                    val customerName = when (billingVmDb) {
                        is UiState.Success -> {
                            val db = (billingVmDb as UiState.Success).data
                            db.customers.find { it.id == customerId }?.name ?: "Customer"
                        }
                        else -> "Customer"
                    }
                    BillCreationScreen(
                        viewModel = billingVm,
                        customerId = customerId,
                        customerName = customerName,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "ledger/{customerId}",
                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
                    val custVm: CustomerViewModel = hiltViewModel()
                    LedgerScreen(
                        viewModel = custVm,
                        customerId = customerId,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(NavRoutes.SEARCH) {
                    val custVm: CustomerViewModel = hiltViewModel()
                    SearchScreen(
                        viewModel = custVm,
                        onBack = { navController.popBackStack() },
                        onCustomerClick = { customerId ->
                            navController.navigate(NavRoutes.customerDetail(customerId))
                        },
                        onBillClick = { customerId, _ ->
                            navController.navigate("bills/$customerId")
                        }
                    )
                }

                // ===== BILLS =====
                composable(NavRoutes.BILLS) {
                    BillsOverviewScreen(
                        viewModel = mainViewModel,
                        onCustomerClick = { customerId ->
                            navController.navigate(NavRoutes.customerDetail(customerId))
                        },
                        onNavigateToSearch = { navController.navigate(NavRoutes.SEARCH) }
                    )
                }

                composable(
                    route = "bills/{customerId}",
                    arguments = listOf(navArgument("customerId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
                    val billingVm: BillingViewModel = hiltViewModel()
                    BillsScreen(
                        viewModel = billingVm,
                        customerId = customerId,
                        onBack = { navController.popBackStack() },
                        onOpenPdf = { billId ->
                            navController.navigate(NavRoutes.webviewPdf(billId))
                        }
                    )
                }

                composable(NavRoutes.WEBVIEW) {
                    WebViewScreen(
                        url = "https://wpapp-xz9l.onrender.com",
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = NavRoutes.WEBVIEW_PDF,
                    arguments = listOf(navArgument("billId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val billId = backStackEntry.arguments?.getString("billId") ?: ""
                    WebViewScreen(
                        url = "https://wpapp-xz9l.onrender.com/api/bill/$billId/pdf",
                        onBack = { navController.popBackStack() }
                    )
                }

                // ===== INVENTORY =====
                composable(NavRoutes.INVENTORY) {
                    val invVm: InventoryViewModel = hiltViewModel()
                    val items by invVm.items.collectAsState()
                    LaunchedEffect(Unit) { invVm.loadItems() }
                    ItemsScreen(
                        items = items,
                        onBack = { navController.popBackStack() },
                        onAddItem = { navController.navigate(NavRoutes.ADD_EDIT_ITEM) },
                        onEditItem = { id -> navController.navigate(NavRoutes.editItem(id)) },
                        onDeleteItem = { id -> invVm.deleteItem(id) },
                        onRefresh = { invVm.refreshItems() },
                        isLoading = false,
                        onMenuClick = openDrawer
                    )
                }

                composable(NavRoutes.ADD_EDIT_ITEM) {
                    val invVm: InventoryViewModel = hiltViewModel()
                    AddEditItemScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { name, price, stock, alert ->
                            invVm.saveItem(name, price, stock, alert)
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = NavRoutes.EDIT_ITEM,
                    arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                ) { entry ->
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

                // ===== QUICK BILL (legacy) =====
                composable(NavRoutes.QUICK_BILL) {
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

                // ===== EXPENSES =====
                composable(NavRoutes.ADD_EXPENSE) {
                    val expVm: ExpenseViewModel = hiltViewModel()
                    AddExpenseScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { title, amount, category, note ->
                            expVm.saveExpense(title, amount, category, note)
                            navController.popBackStack()
                        }
                    )
                }

                // ===== EXPENSES LIST =====
                composable("expenses") {
                    val expVm: ExpenseViewModel = hiltViewModel()
                    val expenses by expVm.expenses.collectAsState()
                    val todayTotal by expVm.todayTotal.collectAsState()
                    LaunchedEffect(Unit) { expVm.loadExpenses(); expVm.loadTodayTotal() }
                    ExpensesScreen(
                        expenses = expenses,
                        todayTotal = todayTotal,
                        onBack = { navController.popBackStack() },
                        onAddExpense = { navController.navigate(NavRoutes.ADD_EXPENSE) },
                        onDeleteExpense = { id -> expVm.deleteExpense(id) },
                        onMenuClick = openDrawer
                    )
                }

                // ===== PURCHASES =====
                composable(NavRoutes.PURCHASES) {
                    val purchVm: PurchaseViewModel = hiltViewModel()
                    val purchases by purchVm.purchases.collectAsState()
                    LaunchedEffect(Unit) { purchVm.loadPurchases() }
                    PurchasesScreen(
                        purchases = purchases,
                        onMenuClick = openDrawer,
                        onBack = { navController.popBackStack() },
                        onAddPurchase = { navController.navigate(NavRoutes.ADD_PURCHASE) },
                        onPurchaseClick = { id -> navController.navigate(NavRoutes.purchaseDetail(id)) },
                        onDeletePurchase = { id -> purchVm.deletePurchase(id) },
                        onRefresh = { purchVm.loadPurchases() },
                        isLoading = false
                    )
                }

                composable(NavRoutes.ADD_PURCHASE) {
                    val purchVm: PurchaseViewModel = hiltViewModel()
                    AddPurchaseScreen(
                        onBack = { navController.popBackStack() },
                        onSave = { name, phone, items, total, paid, notes ->
                            purchVm.savePurchase(name, phone, items, total, paid, notes)
                            navController.popBackStack()
                        }
                    )
                }

                composable(
                    route = NavRoutes.PURCHASE_DETAIL,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
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

                // ===== SETTINGS / PROFILE =====
                composable("profile") {
                    ProfileScreen(
                        viewModel = mainViewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        currentLanguage = currentLanguage,
                        onLanguageChange = onLanguageChange,
                        onLogout = {
                            SessionManager.clear(context)
                            navController.navigate(NavRoutes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                // ===== GST RETURNS (standalone, to be merged into Reports) =====
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

                // ===== INVOICE TEMPLATES =====
                composable("invoice_templates") {
                    val dbState by mainViewModel.dbState.collectAsState()
                    val currentTemplateStr = when (val s = dbState) {
                        is UiState.Success -> s.data.shop?.invoiceTemplate ?: "modern"
                        else -> "modern"
                    }
                    val currentEnum = try {
                        InvoiceTemplate.valueOf(currentTemplateStr.uppercase())
                    } catch (e: Exception) {
                        InvoiceTemplate.MODERN
                    }
                    InvoiceTemplateScreen(
                        currentTemplate = currentEnum,
                        onBack = { navController.popBackStack() },
                        onTemplateSelected = { selected ->
                            mainViewModel.updateInvoiceTemplate(selected.name.lowercase(), context)
                        }
                    )
                }
            }
        }
    }

    if (isMainApp) {
        AppSidebarDrawer(
            drawerState = drawerState,
            currentScreen = currentRoute ?: NavRoutes.HOME,
            shopName = shopName,
            shopOwner = shopOwner,
            customerCount = customerCount,
            billCount = billCount,
            onNavigate = drawerNavigate,
            onLogout = {
                SessionManager.clear(context)
                navController.navigate(NavRoutes.LOGIN) {
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
