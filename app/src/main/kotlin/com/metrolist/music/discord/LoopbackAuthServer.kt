package com.metrolist.music.discord

import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.net.ServerSocket

data class AuthCodeResult(val code: String, val state: String)

class LoopbackAuthServer(private val expectedState: String) {

    private companion object {
        const val TAG = "DiscordSvc"
        const val DEFAULT_HOST = "127.0.0.1"
    }

    private val deferred = CompletableDeferred<AuthCodeResult>()

    private var server: EmbeddedServer<*, *>? = null

    private fun findAvailablePort(): Int {
        val socket = try {
            ServerSocket(0)
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "loopback: failed to open ServerSocket(0)")
            throw e
        }
        val port = socket.localPort
        socket.close()
        return port
    }

    suspend fun start(): Int {
        val port = findAvailablePort()
        Timber.tag(TAG).i("loopback: starting on %s:%d", DEFAULT_HOST, port)
        server = embeddedServer(CIO, port = port, host = DEFAULT_HOST) {
            routing {
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val state = call.request.queryParameters["state"]
                    val error = call.request.queryParameters["error"]

                    if (error != null && state == expectedState) {
                        Timber.tag(TAG).w("loopback: callback received with error=%s", error)
                        deferred.completeExceptionally(CancellationException("Authorization denied: $error"))
                        call.respondText(
                            "<html><body><h1>Authorization Denied</h1><p>Discord returned: $error</p></body></html>",
                            ContentType.Text.Html,
                        )
                        return@get
                    }

                    if (code == null) {
                        Timber.tag(TAG).w("loopback: callback received with missing code")
                        deferred.completeExceptionally(DiscordAuthException.InvalidGrant("Missing authorization code"))
                        call.respondText(
                            "<html><body><h1>Authorization Failed</h1><p>Missing authorization code.</p></body></html>",
                            ContentType.Text.Html,
                        )
                        return@get
                    }

                    if (state != expectedState) {
                        Timber.tag(TAG).w(
                            "loopback: state mismatch (expected=%s, got=%s)",
                            expectedState.take(8),
                            state?.take(8),
                        )
                        deferred.completeExceptionally(DiscordAuthException.StateMismatch())
                        call.respondText(
                            "<html><body><h1>Authorization Failed</h1><p>State validation failed.</p></body></html>",
                            ContentType.Text.Html,
                        )
                        return@get
                    }

                    Timber.tag(TAG).i("loopback: callback received with valid code (length=%d)", code.length)
                    deferred.complete(AuthCodeResult(code = code, state = state))
                    call.respondText(
                        "<html><body><h1>Authorization Complete</h1><p>You can close this tab and return to Metrolist.</p></body></html>",
                        ContentType.Text.Html,
                    )
                }
            }
        }.start(wait = false)
        Timber.tag(TAG).i("loopback: started on port %d", port)
        return port
    }

    suspend fun awaitCode(timeoutMs: Long = 120_000L): AuthCodeResult {
        return withTimeout(timeoutMs) { deferred.await() }
    }

    fun cancel() {
        if (!deferred.isCompleted) {
            Timber.tag(TAG).i("loopback: cancelling authorization")
            deferred.completeExceptionally(CancellationException("Authorization cancelled by user"))
        }
    }

    fun stop() {
        Timber.tag(TAG).i("loopback: stopping")
        server?.stop(1000L, 2000L)
        server = null
    }
}
