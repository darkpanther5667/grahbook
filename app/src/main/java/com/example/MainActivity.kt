package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.BillCreationScreen
import com.example.ui.screens.CustomerDetailScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.PaymentRecordingScreen
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AppNavigation()
    }
  }
}

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
  val navController = rememberNavController()
  var currentScreen by remember { mutableStateOf("dashboard") }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
  ) { padding ->
    NavHost(
      navController = navController,
      startDestination = "dashboard",
      modifier = Modifier.padding(padding)
    ) {
      composable("dashboard") {
        DashboardScreen(
          viewModel = viewModel,
          onNavigateToCustomers = {
            currentScreen = "customers"
            navController.navigate("customers")
          }
        )
      }
      composable("customers") {
        CustomersScreen(
          viewModel = viewModel,
          onBack = {
            currentScreen = "dashboard"
            navController.popBackStack()
          },
          onCustomerClick = { customerId ->
            currentScreen = "customer_detail"
            navController.navigate("customer_detail/$customerId")
          }
        )
      }
      composable("customer_detail/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val dbState by viewModel.dbState.collectAsState()
        val customerName = when (dbState) {
          is com.example.ui.viewmodel.UiState.Success -> {
            val db = (dbState as com.example.ui.viewmodel.UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }
        
        CustomerDetailScreen(
          viewModel = viewModel,
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
          }
        )
      }
      composable("payment/{customerId}",
        arguments = listOf(navArgument("customerId") { type = NavType.StringType })
      ) { backStackEntry ->
        val customerId = backStackEntry.arguments?.getString("customerId") ?: ""
        val dbState by viewModel.dbState.collectAsState()
        val customerName = when (dbState) {
          is com.example.ui.viewmodel.UiState.Success -> {
            val db = (dbState as com.example.ui.viewmodel.UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }
        
        PaymentRecordingScreen(
          viewModel = viewModel,
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
        val dbState by viewModel.dbState.collectAsState()
        val customerName = when (dbState) {
          is com.example.ui.viewmodel.UiState.Success -> {
            val db = (dbState as com.example.ui.viewmodel.UiState.Success).data
            val customer = db.customers.find { it.id == customerId }
            customer?.name ?: "Customer"
          }
          else -> "Customer"
        }
        
        BillCreationScreen(
          viewModel = viewModel,
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
        LedgerScreen(
          viewModel = viewModel,
          customerId = customerId,
          onBack = {
            currentScreen = "customer_detail"
            navController.popBackStack()
          }
        )
      }
    }
  }
}
