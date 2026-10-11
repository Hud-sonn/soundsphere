/**
 * Soundsphere Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.soundsphere.music.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.soundsphere.music.LocalPlayerAwareWindowInsets
import com.soundsphere.music.R
import com.soundsphere.music.viewmodels.PaystackPlan
import com.soundsphere.music.viewmodels.PaymentUiState
import com.soundsphere.music.viewmodels.PaymentViewModel

private const val CALLBACK_HOST_PATH = "soundsphere.name.ng/paystack-callback"

/**
 * Paystack hosted checkout inside a WebView.
 *
 * Card entry + 3D Secure/OTP all happen on Paystack's page (JavaScript and
 * redirects left enabled for exactly that reason). When the page lands on
 * the callback URL the WebView closes and the reference is verified
 * server-side — the redirect alone never marks anything paid. Back press
 * mid-payment cancels without side effects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentWebViewScreen(
    navController: NavController,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.cancel()
        navController.navigateUp()
    }

    TopAppBar(
        title = { Text(stringResource(R.string.payment_title)) },
    )
    when (val state = uiState) {
        is PaymentUiState.Idle,
        is PaymentUiState.Starting,
        is PaymentUiState.Cancelled,
        -> {
            PlanPicker(
                isLoading = state is PaymentUiState.Starting,
                onPick = viewModel::startCheckout,
            )
        }

        is PaymentUiState.CheckoutReady -> {
            CheckoutWebView(
                authorizationUrl = state.authorizationUrl,
                onCallbackUrl = { viewModel.verify(state.reference) },
            )
        }

        is PaymentUiState.Verifying -> {
            LoadingView(text = stringResource(R.string.payment_verifying))
        }

        is PaymentUiState.Success -> {
            ResultView(
                title = stringResource(R.string.payment_success_title),
                body = stringResource(R.string.payment_success_body),
                buttonText = stringResource(R.string.payment_done),
                onDone = { navController.navigateUp() },
            )
        }

        is PaymentUiState.Failed -> {
            ResultView(
                title = stringResource(R.string.payment_failed_title),
                body = state.message,
                buttonText = stringResource(R.string.payment_retry),
                onDone = viewModel::reset,
            )
        }
    }
}

@Composable
private fun PlanPicker(
    isLoading: Boolean,
    onPick: (PaystackPlan) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.payment_plan_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.payment_plan_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        onPick(PaystackPlan(code = "monthly-pro", label = "Monthly Pro"))
                    },
                ) {
                    Text(stringResource(R.string.payment_pay_monthly))
                }
            }
        }
    }
}

@Composable
private fun CheckoutWebView(
    authorizationUrl: String,
    onCallbackUrl: () -> Unit,
) {
    var callbackFired = remember { false }
    AndroidView(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportMultipleWindows(false)
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val url = request.url.toString()
                            if (url.contains(CALLBACK_HOST_PATH) && !callbackFired) {
                                callbackFired = true
                                onCallbackUrl()
                                return true
                            }
                            return false
                        }
                    }
                loadUrl(authorizationUrl)
            }
        },
    )
}

@Composable
private fun LoadingView(text: String) {
    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text)
        }
    }
}

@Composable
private fun ResultView(
    title: String,
    body: String,
    buttonText: String,
    onDone: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onDone) {
                Text(buttonText)
            }
        }
    }
}
