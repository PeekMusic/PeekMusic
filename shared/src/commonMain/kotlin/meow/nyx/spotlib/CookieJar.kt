package meow.nyx.spotlib

import com.metrolist.shared.network.TextResponse
import com.metrolist.shared.network.bodyAsTextLimited
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.statement.HttpStatement
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * The cookies Spotify sets during sign-in, kept per domain. PeekMusic: plain Kotlin instead of java.net's CookieManager,
 * so the library also runs on iOS (Spotify Canvas).
 */
class SpotifyCookieJar {
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val hostOnly: Boolean,
        val path: String,
    )

    // Replaced whole on every change, so a request reading it never sees a list being modified.
    @Volatile
    private var cookies: List<StoredCookie> = emptyList()
    private val cookieExemptHosts = setOf("api-partner.spotify.com", "clienttoken.spotify.com")

    fun requestCookies(url: Url): String? {
        if (url.host in cookieExemptHosts) return null
        return matching(url)
            .takeIf(List<StoredCookie>::isNotEmpty)
            ?.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun cookieValue(
        url: Url,
        name: String,
    ): String? = matching(url).firstOrNull { it.name == name }?.value

    fun capture(
        url: Url,
        setCookieHeaders: List<String>,
    ) {
        for (raw in setCookieHeaders) {
            val cookie = runCatching { parseServerSetCookieHeader(raw) }.getOrNull() ?: continue
            if (cookie.name.isBlank()) continue
            val domain =
                cookie.domain
                    ?.trim()
                    ?.removePrefix(".")
                    ?.lowercase()
                    ?.takeIf(String::isNotBlank)
            // A domain attribute must cover the host that set it, as browsers and java.net require.
            if (domain != null && !domainMatches(url.host.lowercase(), domain)) continue
            val stored =
                StoredCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = domain ?: url.host.lowercase(),
                    hostOnly = domain == null,
                    path = cookie.path?.takeIf(String::isNotEmpty) ?: "/",
                )
            val others = cookies.filterNot { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            val expired =
                cookie.maxAge?.let { it <= 0 } == true ||
                    cookie.expires?.let { it.timestamp <= Clock.System.now().toEpochMilliseconds() } == true
            cookies = if (expired) others else others + stored
        }
    }

    fun store(
        url: Url,
        name: String,
        value: String,
    ) {
        val host = url.host.lowercase()
        cookies =
            cookies.filterNot { it.name == name && it.domain == host && it.path == "/" } +
            StoredCookie(name, value, host, hostOnly = false, path = "/")
    }

    fun clear() {
        cookies = emptyList()
    }

    private fun matching(url: Url): List<StoredCookie> {
        val host = url.host.lowercase()
        return cookies.filter { if (it.hostOnly) host == it.domain else domainMatches(host, it.domain) }
    }

    private fun domainMatches(
        host: String,
        domain: String,
    ): Boolean = host == domain || host.endsWith(".$domain")
}

internal class ApiHttp(
    private val client: HttpClient,
    private val jar: SpotifyCookieJar,
) {
    suspend fun get(
        url: String,
        maxBytes: Int = 256 * 1024,
        block: HttpRequestBuilder.() -> Unit = {},
    ): TextResponse = send(client.prepareGet(url) { applyCommon(url, block) }, maxBytes)

    suspend fun post(
        url: String,
        maxBytes: Int = 256 * 1024,
        block: HttpRequestBuilder.() -> Unit = {},
    ): TextResponse = send(client.preparePost(url) { applyCommon(url, block) }, maxBytes)

    private fun HttpRequestBuilder.applyCommon(
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ) {
        jar.requestCookies(Url(url))?.let { header(HttpHeaders.Cookie, it) }
        block()
    }

    private suspend fun send(
        statement: HttpStatement,
        maxBytes: Int,
    ): TextResponse =
        statement.execute { response ->
            response.headers.getAll(HttpHeaders.SetCookie)?.let { jar.capture(response.call.request.url, it) }
            val request = response.call.request
            Log.info("${request.method.value} ${request.url.host}${request.url.encodedPath} -> ${response.status.value}")
            TextResponse(response.status, response.headers, response.bodyAsTextLimited(maxBytes))
        }
}
