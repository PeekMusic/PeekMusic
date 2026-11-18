/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.peek

import com.metrolist.desktop.state.app.AppState
import com.metrolist.shared.network.HttpFailureLog
import com.metrolist.shared.peek.friends.PeekFriendsManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.websocket.WebSockets

/**
 * PeekParty friends on desktop, the counterpart of Android's AndroidPeekFriends. The desktop app is online for its
 * friends as long as it runs, in the window or in the tray, so friends see you and can invite you. Requests and
 * invites show inside the app (PeekFriendsGlobalEvents), also when it comes back from the tray.
 */
object DesktopPeekFriends {
    private val httpClient =
        HttpClient(CIO) {
            install(HttpFailureLog)
            install(WebSockets) { maxFrameSize = 256L * 1024 }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000L
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 90_000L
            }
        }

    val manager: PeekFriendsManager by lazy { PeekFriendsManager(callerScope = AppState.scope, httpClient = httpClient) }

    private var started = false

    fun start() {
        if (started) return
        started = true
        manager.setActive(true)
    }

    fun close() {
        if (!started) return
        manager.setActive(false)
        httpClient.close()
    }
}
