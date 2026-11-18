/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.error
import com.metrolist.shared.generated.resources.retry
import com.metrolist.shared.generated.resources.spotify_import_error_sign_in_failed
import com.metrolist.shared.generated.resources.spotify_login_page_failed
import com.metrolist.shared.generated.resources.spotify_sign_in
import com.metrolist.shared.spotifyimport.signInSpotifyCanvas
import com.metrolist.shared.spotifyimport.spotifySessionCookie
import com.metrolist.shared.state.GlobalYouTube
import com.metrolist.shared.ui.components.ContainedLoadingIndicator
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.utils.BlockScreenshots
import com.metrolist.shared.utils.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.stringResource
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLRequest
import platform.UIKit.UIDevice
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * PeekMusic: Spotify sign-in for Spotify Canvas on iOS, like Android's WebView login. It waits for the `sp_dc`
 * cookie, turns it into a stored Spotify session and leaves the page; the web view keeps no cookies afterwards.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun IosSpotifyLoginScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    BlockScreenshots()

    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WKWebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val signInFailed = stringResource(Res.string.spotify_import_error_sign_in_failed)
    val pageError = stringResource(Res.string.spotify_login_page_failed, stringResource(Res.string.error))

    fun checkForSession(view: WKWebView) {
        if (isAuthenticating) return
        scope.launch {
            val cookie = spotifyCookieFrom(view) ?: return@launch
            if (isAuthenticating) return@launch
            isAuthenticating = true
            try {
                signInSpotifyCanvas(cookie, GlobalYouTube.client.engine)
                clearSpotifyCookies(view)
                onComplete()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w("IosSpotifyLogin", "Sign-in failed type=${error::class.simpleName}")
                clearSpotifyCookies(view)
                errorMessage = signInFailed
                isAuthenticating = false
            }
        }
    }

    // Spotify sometimes sets the cookie from script without a new page load, so look again every second.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000)
            webView?.let(::checkForSession)
        }
    }

    val delegate = remember { IosSpotifyLoginDelegate() }
    delegate.onStart = { isLoading = true }
    delegate.onFinish = { view ->
        isLoading = false
        checkForSession(view)
    }
    delegate.onError = { error ->
        isLoading = false
        Logger.w("IosSpotifyLogin", "Page load failed code=${error.code}")
        errorMessage = pageError
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.spotify_sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            UIKitView(
                factory = {
                    val configuration =
                        WKWebViewConfiguration().apply {
                            // A fresh, in-memory profile: no old Spotify account leaks in and nothing stays on disk.
                            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                            applicationNameForUserAgent = "Version/${UIDevice.currentDevice.systemVersion} Safari/604.1"
                        }
                    WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration).apply {
                        navigationDelegate = delegate
                        UIDelegate = delegate
                        allowsBackForwardNavigationGestures = true
                        webView = this
                        loadRequest(NSURLRequest.requestWithURL(requireNotNull(NSURL.URLWithString(SPOTIFY_LOGIN_URL))))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = {
                    it.navigationDelegate = delegate
                    it.UIDelegate = delegate
                },
                onRelease = {
                    it.stopLoading()
                    it.navigationDelegate = null
                    it.UIDelegate = null
                    if (webView === it) webView = null
                },
            )

            if (isLoading || isAuthenticating) {
                ContainedLoadingIndicator(Modifier.align(Alignment.Center))
            }

            errorMessage?.let { message ->
                Surface(Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        Button(
                            onClick = {
                                errorMessage = null
                                webView?.loadRequest(
                                    NSURLRequest.requestWithURL(requireNotNull(NSURL.URLWithString(SPOTIFY_LOGIN_URL))),
                                )
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.retry)) }
                    }
                }
            }
        }
    }
}

private suspend fun spotifyCookies(webView: WKWebView): List<NSHTTPCookie> =
    suspendCancellableCoroutine { continuation ->
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { values ->
            if (continuation.isActive) {
                continuation.resume(
                    values.orEmpty().filterIsInstance<NSHTTPCookie>().filter {
                        it.domain.removePrefix(".").let { domain -> domain == "spotify.com" || domain.endsWith(".spotify.com") }
                    },
                )
            }
        }
    }

private suspend fun spotifyCookieFrom(webView: WKWebView): String? =
    spotifySessionCookie(spotifyCookies(webView).joinToString("; ") { "${it.name}=${it.value}" })

private suspend fun clearSpotifyCookies(webView: WKWebView) {
    val store = webView.configuration.websiteDataStore.httpCookieStore
    spotifyCookies(webView).forEach { store.deleteCookie(it) {} }
}

@OptIn(ExperimentalForeignApi::class)
private class IosSpotifyLoginDelegate :
    NSObject(),
    WKNavigationDelegateProtocol,
    WKUIDelegateProtocol {
    var onStart: () -> Unit = {}
    var onFinish: (WKWebView) -> Unit = {}
    var onError: (NSError) -> Unit = {}

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didStartProvisionalNavigation: WKNavigation?,
    ) = onStart()

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFinishNavigation: WKNavigation?,
    ) = onFinish(webView)

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError,
    ) {
        if (withError.code != NSURLErrorCancelled) onError(withError)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        if (withError.code != NSURLErrorCancelled) onError(withError)
    }

    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val scheme = decidePolicyForNavigationAction.request.URL?.scheme
        decisionHandler(
            if (scheme == "http" || scheme == "https") {
                WKNavigationActionPolicy.WKNavigationActionPolicyAllow
            } else {
                WKNavigationActionPolicy.WKNavigationActionPolicyCancel
            },
        )
    }

    // Google/Apple/Facebook sign-in opens popups; load them in the same view instead.
    override fun webView(
        webView: WKWebView,
        createWebViewWithConfiguration: WKWebViewConfiguration,
        forNavigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures,
    ): WKWebView? {
        forNavigationAction.request.takeIf { it.URL?.scheme in setOf("http", "https") }?.let(webView::loadRequest)
        return null
    }
}

private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
