/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundsphere.music.api.SyncService
import com.soundsphere.music.data.AuthRepository
import com.soundsphere.music.data.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Plan shown at checkout. Code + amount must match PAYSTACK_PLANS_JSON server-side. */
data class PaystackPlan(
    val code: String,
    val label: String,
)

sealed interface PaymentUiState {
    data object Idle : PaymentUiState

    data object Starting : PaymentUiState

    data class CheckoutReady(
        val authorizationUrl: String,
        val reference: String,
    ) : PaymentUiState

    data object Verifying : PaymentUiState

    data object Success : PaymentUiState

    data class Failed(
        val message: String,
    ) : PaymentUiState

    data object Cancelled : PaymentUiState
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Idle)
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun startCheckout(plan: PaystackPlan) {
        val token = authRepository.getToken()
        if (token == null) {
            _uiState.value = PaymentUiState.Failed("Not signed in")
            return
        }
        _uiState.value = PaymentUiState.Starting
        viewModelScope.launch {
            SyncService.initializePaystack(token, plan.code)
                .onSuccess { init ->
                    _uiState.value =
                        PaymentUiState.CheckoutReady(
                            authorizationUrl = init.authorizationUrl,
                            reference = init.reference,
                        )
                }.onFailure { error ->
                    _uiState.value =
                        PaymentUiState.Failed(error.message ?: "Could not start checkout")
                }
        }
    }

    fun verify(reference: String) {
        val token = authRepository.getToken()
        if (token == null) {
            _uiState.value = PaymentUiState.Failed("Not signed in")
            return
        }
        _uiState.value = PaymentUiState.Verifying
        viewModelScope.launch {
            SyncService.verifyPaystack(token, reference)
                .onSuccess { premium ->
                    if (premium) {
                        refreshSubscription()
                        // Flip the app-wide flag so locks clear immediately.
                        syncRepository.refreshPremiumStatus()
                        _uiState.value = PaymentUiState.Success
                    } else {
                        _uiState.value = PaymentUiState.Failed("Payment not confirmed")
                    }
                }.onFailure { error ->
                    _uiState.value =
                        PaymentUiState.Failed(error.message ?: "Verification failed")
                }
        }
    }

    fun cancel() {
        _uiState.value = PaymentUiState.Cancelled
    }

    fun reset() {
        _uiState.value = PaymentUiState.Idle
    }

    fun refreshSubscription() {
        val token = authRepository.getToken() ?: return
        viewModelScope.launch {
            SyncService.getSubscriptionStatus(token)
                .onSuccess { status ->
                    _isPremium.value = status.isPro
                }
        }
    }
}
