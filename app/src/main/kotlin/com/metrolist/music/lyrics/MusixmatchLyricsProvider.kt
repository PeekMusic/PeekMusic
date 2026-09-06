/**
 * DripMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.constants.EnableMusixmatchKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Locale

/**
 * Musixmatch via the undocumented desktop-app flow: a free, keyless user token
 * (`token.get`), then `track.search` -> `track.richsync.get` (real per-word timings
 * via the richsync `l` array) with `track.subtitle.get` (plain LRC) as fallback.
 * Flow mirrors the proven syncedlyrics implementation.
 *
 * ponytail: all-zero token means Musixmatch flagged the client's IP (datacenter,
 * VPN, some carrier ranges); every lyrics endpoint then 404s, so we fail fast.
 * There is no workaround from a flagged IP.
 */
object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    private const val TAG = "MusixmatchProvider"
    private const val BASE_URL = "https://apic-desktop.musixmatch.com/ws/1.1"
    private const val APP_ID = "web-desktop-app-v1.0"
    private const val TOKEN_TTL_MS = 8 * 60 * 1000L // token lives ~10 min, refresh early

    @Serializable
    private data class Header(
        @SerialName("status_code") val statusCode: Int = 0,
    )

    @Serializable
    private data class Message<T>(
        val header: Header,
        val body: T? = null,
    )

    @Serializable
    private data class Envelope<T>(
        val message: Message<T>,
    )

    @Serializable
    private data class TokenBody(
        @SerialName("user_token") val userToken: String = "",
    )

    @Serializable
    private data class Track(
        @SerialName("track_id") val trackId: Long = 0,
        @SerialName("track_length") val trackLength: Int = 0,
    )

    @Serializable
    private data class TrackListItem(
        val track: Track,
    )

    @Serializable
    private data class SearchBody(
        @SerialName("track_list") val trackList: List<TrackListItem> = emptyList(),
    )

    @Serializable
    private data class Richsync(
        @SerialName("richsync_body") val richsyncBody: String = "",
    )

    @Serializable
    private data class RichsyncBody(
        val richsync: Richsync? = null,
    )

    @Serializable
    private data class Subtitle(
        @SerialName("subtitle_body") val subtitleBody: String = "",
    )

    @Serializable
    private data class SubtitleBody(
        val subtitle: Subtitle? = null,
    )

    // richsync lines carry real word timings in `l`: each word has an offset `o`
    // (seconds into the line) and the text `c` (with trailing space).
    @Serializable
    private data class RichsyncWord(
        val o: Double = 0.0,
        val c: String = "",
    )

    @Serializable
    private data class RichsyncLine(
        val ts: Double = 0.0,
        val te: Double = 0.0,
        val x: String = "",
        val l: List<RichsyncWord> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    @Volatile
    private var token: String = ""
    private var tokenFetchedAtMs: Long = 0

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        return try {
            val usertoken = getToken()
            val track = searchTrack(title, artist, duration, usertoken)
                ?: return Result.failure(IllegalStateException("Musixmatch: no matching track"))

            val lyrics = getRichsync(track.trackId, track.trackLength, usertoken)
                ?: getSubtitle(track.trackId, track.trackLength, usertoken)
                ?: return Result.failure(IllegalStateException("Musixmatch: no lyrics for track"))
            Result.success(lyrics)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get lyrics")
            Result.failure(e)
        }
    }

    private suspend fun getToken(): String {
        val now = System.currentTimeMillis()
        if (token.isNotBlank() && now - tokenFetchedAtMs < TOKEN_TTL_MS) return token

        val responseText = client.get("$BASE_URL/token.get") {
            parameter("app_id", APP_ID)
            parameter("user_language", "en")
            parameter("t", now.toString())
            header(HttpHeaders.UserAgent, DESKTOP_UA)
            header(HttpHeaders.Cookie, COOKIE)
        }.bodyAsText()
        val response = json.decodeFromString<Envelope<TokenBody>>(responseText).message
        // Musixmatch rate-limits token issuance with 401; wait briefly and retry once,
        // mirroring the syncedlyrics flow.
        if (response.header.statusCode == 401) {
            kotlinx.coroutines.delay(5_000)
            return getToken()
        }
        if (response.header.statusCode != 200 || response.body == null ||
            response.body.userToken.isBlank() || response.body.userToken == "UpgradeOnly"
        ) {
            throw IllegalStateException("Musixmatch: token request failed (${response.header.statusCode})")
        }
        // All-zero token = Musixmatch flagged this IP (datacenter/VPN) and withholds
        // a real token; every lyrics endpoint then 404s. Fail fast instead.
        if (response.body.userToken.all { it == '0' }) {
            throw IllegalStateException("Musixmatch: no real token for this IP")
        }
        token = response.body.userToken
        tokenFetchedAtMs = now
        return token
    }

    private suspend fun searchTrack(
        title: String,
        artist: String,
        duration: Int,
        usertoken: String,
    ): Track? {
        val responseText = client.get("$BASE_URL/track.search") {
            parameter("app_id", APP_ID)
            parameter("q_track", title)
            parameter("q_artist", artist)
            parameter("f_has_lyrics", 1)
            if (duration > 0) parameter("q_duration", duration / 1000)
            parameter("page_size", 1)
            parameter("page", 1)
            parameter("s_track_rating", "desc")
            parameter("usertoken", usertoken)
            parameter("t", System.currentTimeMillis().toString())
            header(HttpHeaders.UserAgent, DESKTOP_UA)
            header(HttpHeaders.Cookie, COOKIE)
        }.bodyAsText()
        val response = json.decodeFromString<Envelope<SearchBody>>(responseText).message
        if (response.header.statusCode != 200) return null
        return response.body?.trackList?.firstOrNull()?.track
    }

    private suspend fun getRichsync(
        trackId: Long,
        trackLength: Int,
        usertoken: String,
    ): String? {
        val http = client.get("$BASE_URL/track.richsync.get") {
            parameter("app_id", APP_ID)
            parameter("track_id", trackId)
            parameter("f_subtitle_length", trackLength)
            parameter("f_subtitle_length_max_deviation", 10)
            parameter("usertoken", usertoken)
            parameter("t", System.currentTimeMillis().toString())
            header(HttpHeaders.UserAgent, DESKTOP_UA)
            header(HttpHeaders.Cookie, COOKIE)
        }
        if (!http.status.isSuccess()) return null
        val body = json.decodeFromString<Envelope<RichsyncBody>>(http.bodyAsText()).message
        val richsyncBody = body.body?.richsync?.richsyncBody ?: return null
        if (richsyncBody.isBlank()) return null

        return runCatching {
            val lines = json.decodeFromString<List<RichsyncLine>>(richsyncBody)
            if (lines.isEmpty()) return null
            lines.joinToString("\n") { line -> formatLine(line) }
        }.getOrElse {
            Timber.tag(TAG).w(it, "Failed to parse richsync body")
            null
        }
    }

    private suspend fun getSubtitle(
        trackId: Long,
        trackLength: Int,
        usertoken: String,
    ): String? {
        val http = client.get("$BASE_URL/track.subtitle.get") {
            parameter("app_id", APP_ID)
            parameter("track_id", trackId)
            parameter("subtitle_format", "lrc")
            parameter("f_subtitle_length", trackLength)
            parameter("f_subtitle_length_max_deviation", 10)
            parameter("usertoken", usertoken)
            parameter("t", System.currentTimeMillis().toString())
            header(HttpHeaders.UserAgent, DESKTOP_UA)
            header(HttpHeaders.Cookie, COOKIE)
        }
        if (!http.status.isSuccess()) return null
        val body = json.decodeFromString<Envelope<SubtitleBody>>(http.bodyAsText()).message
        val subtitle = body.body?.subtitle?.subtitleBody ?: return null
        return subtitle.ifBlank { null }
    }

    /**
     * One richsync line -> "[mm:ss.SSS]text" plus a "<word:ts:te|...>" timing
     * continuation line (times in seconds, matching LyricsUtils.parseWordTimestamps).
     * Word timings come from the richsync `l` array (offset `o` per word); a word's
     * end is the next word's start, the last word ends at the line end `te`.
     */
    private fun formatLine(line: RichsyncLine): String {
        val text = line.x.trim()
        val lrc = "[%s]%s".format(Locale.US, formatLrcTimestamp(line.ts), text)

        val words = line.l.filter { it.c.isNotBlank() }
        if (words.isEmpty()) return lrc

        val wordTimings = words.mapIndexed { index, word ->
            val start = line.ts + word.o
            val end = words.getOrNull(index + 1)?.let { line.ts + it.o } ?: line.te
            "%s:%.3f:%.3f".format(Locale.US, word.c.trim(), start, end)
        }.joinToString("|")

        return "$lrc\n<$wordTimings>"
    }

    private fun formatLrcTimestamp(seconds: Double): String {
        val totalMs = (seconds * 1000).toLong()
        val min = totalMs / 60_000
        val sec = (totalMs % 60_000) / 1000
        val ms = totalMs % 1000
        return "%02d:%02d.%03d".format(Locale.US, min, sec, ms)
    }

    // Dropping the AWS ELB sticky-session cookie (as Meld does) keeps every request
    // a fresh anonymous session, which Musixmatch's token issuance tolerates better.
    private const val COOKIE = "AWSELB=0; AWSELBCORS=0"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
