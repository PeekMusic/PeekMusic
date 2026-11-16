package com.metrolist.shared.peek

import com.metrolist.innertubex.models.YouTubeClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Clients asked for a video's caption tracks, in order.
 *
 * Upstream only asks `WEB`, which YouTube answers with `UNPLAYABLE` and no captions unless the
 * user is logged in, so music-video lyrics were never re-timed for logged-out users. `IOS`
 * returns the caption tracks without login (checked 2026-11-16 on KAI, Rick Astley, Despacito,
 * PSY, BLACKPINK), and its caption URLs load with any user agent.
 */
internal val PeekCaptionClients: List<YouTubeClient> = listOf(YouTubeClient.WEB, YouTubeClient.IOS)

internal fun JsonObject.hasCaptionTracks(): Boolean {
    val renderer = (this["captions"] as? JsonObject)?.get("playerCaptionsTracklistRenderer") as? JsonObject
    return ((renderer?.get("captionTracks") as? JsonArray)?.isNotEmpty()) == true
}

/**
 * Returns the first player response that carries caption tracks, else the last response.
 * Rethrows the last failure only when every client failed.
 */
internal suspend fun <C> peekPlayerWithCaptions(
    clients: List<C>,
    fetch: suspend (C) -> JsonObject,
): JsonObject {
    var last: JsonObject? = null
    var failure: Exception? = null
    for (client in clients) {
        val response =
            try {
                fetch(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                continue
            }
        if (response.hasCaptionTracks()) return response
        last = response
    }
    return last ?: throw (failure ?: IllegalStateException("no caption clients"))
}
