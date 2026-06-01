package com.aistudio.sharmakhata.pqmzvk.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.aistudio.sharmakhata.pqmzvk.data.model.Bill
import com.aistudio.sharmakhata.pqmzvk.data.model.FullDatabase
import com.aistudio.sharmakhata.pqmzvk.data.remote.BillItemRequest
import com.aistudio.sharmakhata.pqmzvk.data.remote.StoredItem
import com.aistudio.sharmakhata.pqmzvk.data.repository.BillingRepository
import com.aistudio.sharmakhata.pqmzvk.ui.common.InMemoryPagingSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillingViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val dbState: StateFlow<UiState<FullDatabase>> = LiveSyncManager.fullDatabase
        .map { db ->
            if (db == null) UiState.Loading
            else UiState.Success(db)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()

    private val _lastCreatedBillId = MutableStateFlow<String?>(null)
    val lastCreatedBillId: StateFlow<String?> = _lastCreatedBillId.asStateFlow()

    private val _storedItems = MutableStateFlow<List<StoredItem>>(emptyList())
    val storedItems: StateFlow<List<StoredItem>> = _storedItems.asStateFlow()

    private val _billFilter = MutableStateFlow("All")
    val billFilter: StateFlow<String> = _billFilter.asStateFlow()
    fun setBillFilter(value: String) { _billFilter.value = value }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun billsPagingData(customerId: String): Flow<PagingData<Bill>> {
        return combine(
            dbState.map { state ->
                when (state) {
                    is UiState.Success -> state.data.bills
                    else -> emptyList<Bill>()
                }
            }.distinctUntilChanged(),
            _billFilter
        ) { allBills, filter ->
            val customerBills = allBills.filter { it.customerId == customerId }
            when (filter) {
                "Paid" -> customerBills.filter { it.status == "paid" }
                "Unpaid" -> customerBills.filter { it.status != "paid" }
                "Overdue" -> customerBills.filter { it.status == "overdue" }
                else -> customerBills
            }
        }.flatMapLatest { filteredBills ->
            Pager(PagingConfig(pageSize = 20)) {
                InMemoryPagingSource(filteredBills.sortedByDescending { it.createdAt })
            }.flow
        }.cachedIn(viewModelScope)
    }

    fun loadStoredItems() {
        viewModelScope.launch {
            _storedItems.value = billingRepository.loadStoredItems()
        }
    }

    fun createBill(
        context: Context,
        customerId: String,
        amount: Double,
        items: List<BillItemRequest>?,
        gstType: String = "cgst_sgst",
        gstRate: Int = 0,
        taxableAmount: Double = 0.0,
        totalCgst: Double = 0.0,
        totalSgst: Double = 0.0,
        totalIgst: Double = 0.0,
        grandTotal: Double = 0.0
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val (result, billId) = billingRepository.createBill(
                context, customerId, amount, items,
                gstType, gstRate, taxableAmount, totalCgst, totalSgst, totalIgst, grandTotal
            )
            if (billId != null) _lastCreatedBillId.value = billId
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun markBillPaid(context: Context, billId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.markBillPaid(context, billId)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun sendInvoiceOnWhatsApp(context: Context, billId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.sendInvoiceOnWhatsApp(context, billId)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun sendStatementOnWhatsApp(context: Context, customerId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.sendStatementOnWhatsApp(context, customerId)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun sendReminderOnWhatsApp(context: Context, customerId: String, message: String? = null) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.sendReminderOnWhatsApp(context, customerId, message)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun quickBill(
        context: Context,
        customerName: String?,
        customerPhone: String?,
        total: Double,
        items: List<BillItemRequest>?,
        gstType: String = "cgst_sgst",
        gstRate: Int = 0,
        taxableAmount: Double = 0.0,
        totalCgst: Double = 0.0,
        totalSgst: Double = 0.0,
        totalIgst: Double = 0.0,
        grandTotal: Double = 0.0
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val (result, billId) = billingRepository.quickBill(
                context, customerName, customerPhone, total, items,
                gstType, gstRate, taxableAmount, totalCgst, totalSgst, totalIgst, grandTotal
            )
            if (billId != null) _lastCreatedBillId.value = billId
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = OperationState.Idle
        _lastCreatedBillId.value = null
    }

    fun deleteBill(context: Context, billId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.deleteBill(context, billId)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }

    fun deleteTransaction(context: Context, transactionId: String) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading
            val result = billingRepository.deleteTransaction(context, transactionId)
            _operationState.value = when (result) {
                is RepoResult.Success -> OperationState.Success(result.message)
                is RepoResult.Error -> OperationState.Error(result.message)
            }
        }
    }
}
