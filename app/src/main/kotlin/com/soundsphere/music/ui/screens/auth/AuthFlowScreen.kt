/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soundsphere.music.viewmodels.AuthViewModel

/** Internal routing states of the unauthenticated auth flow. */
private sealed interface AuthRoute {
    data object Splash : AuthRoute
    data object Login : AuthRoute
    data object Register : AuthRoute
    data class Otp(val email: String) : AuthRoute
    data object Forgot : AuthRoute
    data class Reset(val email: String) : AuthRoute
}

/**
 * Root composable of the auth flow. Shown by the main gate until the user is
 * authenticated (the gate releases as soon as [AuthViewModel.isLoggedIn] flips).
 */
@Composable
fun AuthFlowScreen(authViewModel: AuthViewModel) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var route by remember { mutableStateOf<AuthRoute>(AuthRoute.Splash) }

    // Register success -> OTP verification for that email
    LaunchedEffect(uiState.emailPendingVerification) {
        uiState.emailPendingVerification?.let { email -> route = AuthRoute.Otp(email) }
    }

    // Reset success -> back to login
    LaunchedEffect(uiState.successMessage) {
        if (route is AuthRoute.Reset && uiState.successMessage != null) {
            route = AuthRoute.Login
        }
    }

    when (val current = route) {
        is AuthRoute.Splash ->
            AuthSplashScreen(
                onFinished = { route = AuthRoute.Login },
            )

        is AuthRoute.Login ->
            AuthLoginScreen(
                isLoading = uiState.isLoading,
                error = uiState.error,
                onLogin = { email, password -> authViewModel.login(email, password) },
                onCreateAccount = { route = AuthRoute.Register },
                onForgotPassword = { route = AuthRoute.Forgot },
            )

        is AuthRoute.Register ->
            AuthRegisterScreen(
                isLoading = uiState.isLoading,
                error = uiState.error,
                onRegister = { username, email, password ->
                    authViewModel.register(username, email, password)
                },
                onLogin = { route = AuthRoute.Login },
            )

        is AuthRoute.Otp ->
            AuthOtpScreen(
                email = current.email,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onVerify = { otp -> authViewModel.verify(current.email, otp) },
                onResend = { authViewModel.resendOtp(current.email) },
                onBack = { route = AuthRoute.Login },
            )

        is AuthRoute.Forgot ->
            AuthForgotPasswordScreen(
                isLoading = uiState.isLoading,
                error = uiState.error,
                onSend = { email ->
                    authViewModel.forgotPassword(email)
                    route = AuthRoute.Reset(email)
                },
                onBack = { route = AuthRoute.Login },
            )

        is AuthRoute.Reset ->
            AuthResetPasswordScreen(
                email = current.email,
                isLoading = uiState.isLoading,
                error = uiState.error,
                onReset = { otp, newPassword ->
                    authViewModel.resetPassword(current.email, otp, newPassword)
                },
                onBack = { route = AuthRoute.Forgot },
            )
    }
}
