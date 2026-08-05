/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soundsphere.music.api.AuthService
import com.soundsphere.music.api.AuthToken
import com.soundsphere.music.api.UnauthorizedException
import com.soundsphere.music.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val emailPendingVerification: String? = null,
    val emailPendingReset: String? = null,
    val passwordResetComplete: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(repository.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    /**
     * Whether the initial auth check (reading the stored token) has completed.
     * The native splash stays visible until this flips true.
     */
    private val _authChecked = MutableStateFlow(false)
    val authChecked: StateFlow<Boolean> = _authChecked.asStateFlow()

    init {
        // Keep the login state in sync with the repository (e.g. token cleared on 401)
        _isLoggedIn.value = repository.isLoggedIn()
        _authChecked.value = true
        if (_isLoggedIn.value) {
            validateStoredSession()
        }
    }

    /**
     * Confirms the stored token is still accepted by the backend. If it was
     * revoked or expired (401), the session is cleared and the account gate
     * reappears — the MainActivity navigation effect reacts to isLoggedIn.
     */
    private fun validateStoredSession() {
        val token = repository.getToken() ?: return
        viewModelScope.launch {
            AuthService.me(token).onFailure { error ->
                if (error is UnauthorizedException) {
                    repository.clearToken()
                    _isLoggedIn.value = false
                    _uiState.value = AuthUiState()
                }
            }
        }
    }

    fun register(
        username: String,
        email: String,
        password: String,
    ) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
        viewModelScope.launch {
            val result = AuthService.register(email, password, username)
            result.onSuccess {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Verification code sent to your email",
                        emailPendingVerification = email,
                    )
            }.onFailure {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Registration failed",
                    )
            }
        }
    }

    fun verify(email: String, otp: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = AuthService.verify(email, otp)
            handleTokenResult(result)
        }
    }

    fun resendOtp(email: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = AuthService.resendOtp(email)
            result.onSuccess {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        successMessage = "A new code has been sent",
                    )
            }.onFailure {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Failed to resend code",
                    )
            }
        }
    }

    fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = AuthService.login(email, password)
            handleTokenResult(result)
        }
    }

    fun forgotPassword(email: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
        viewModelScope.launch {
            val result = AuthService.forgotPassword(email)
            result.onSuccess {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        successMessage = "If that email is registered, a reset code was sent",
                        emailPendingReset = email,
                    )
            }.onFailure {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Failed to send reset code",
                    )
            }
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String) {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, successMessage = null)
        viewModelScope.launch {
            val result = AuthService.resetPassword(email, otp, newPassword)
            result.onSuccess {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Password reset successfully. You can log in now.",
                        passwordResetComplete = true,
                    )
            }.onFailure {
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = it.message ?: "Failed to reset password",
                    )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun logout() {
        repository.clearToken()
        _isLoggedIn.value = false
        _uiState.value = AuthUiState()
    }

    private fun handleTokenResult(result: Result<AuthToken>) {
        result.onSuccess { token ->
            repository.saveToken(token.token)
            _isLoggedIn.value = true
            _uiState.value =
                AuthUiState(
                    isAuthenticated = true,
                )
        }.onFailure {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = false,
                    error = it.message ?: "Authentication failed",
                )
        }
    }
}
