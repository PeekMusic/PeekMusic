/**
 * PeekMusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.music.constants.EnableSimpMusicKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * SimpMusic Lyrics community database, matched exactly by YouTube videoId.
 * Returns enhanced LRC (word-level timestamps) when available, plain LRC otherwise.
 *
 * ponytail: rate-limited to ~30 req/min/IP (429 + Retry-After). The sequential
 * provider chain only hits us once per song and lyrics are DB-cached afterwards,
 * so normal use stays well under the limit. Ceiling: aggressive bulk re-fetch of
 * uncached songs could hit it; upgrade path: honor Retry-After and back off.
 */
object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    private const val TAG = "SimpMusicProvider"
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1"

    @Serializable
    private data class LyricsData(
        val plainLyric: String = "",
        val syncedLyrics: String = "",
        val richSyncLyrics: String = "",
    )

    @Serializable
    private data class Response(
        val type: String = "",
        val success: Boolean = false,
        val data: List<LyricsData> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableSimpMusicKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        return try {
            val http = client.get("$BASE_URL/$id")
            if (!http.status.isSuccess()) {
                return Result.failure(IllegalStateException("SimpMusic: HTTP ${http.status.value}"))
            }
            val body = json.decodeFromString<Response>(http.bodyAsText())
            val data = body.data.firstOrNull()
                ?: return Result.failure(IllegalStateException("SimpMusic: empty data"))
            val lyrics = data.richSyncLyrics.ifBlank { data.syncedLyrics.ifBlank { data.plainLyric } }
            if (lyrics.isBlank()) {
                Result.failure(IllegalStateException("SimpMusic: no lyrics content"))
            } else {
                Result.success(lyrics)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to get lyrics")
            Result.failure(e)
        }
    }
}
