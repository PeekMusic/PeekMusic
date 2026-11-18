/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.spotifyimport

import com.metrolist.shared.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import meow.nyx.spotlib.SpotifyClient
import meow.nyx.spotlib.SpotifyConfig

private val canvasSessionJson = Json { ignoreUnknownKeys = true }

/**
 * PeekMusic: signs in with the `sp_dc` cookie from a Spotify login page and stores the session that Spotify Canvas
 * uses. iOS calls this from its login screen; Android and desktop go through their import managers.
 */
suspend fun signInSpotifyCanvas(
    spDc: String,
    engine: HttpClientEngine,
) {
    require(spDc.isNotBlank()) { "Spotify login did not return a session cookie" }
    SpotifyClient.create(SpotifyConfig(engine = engine)).use { client ->
        client.loginWithSession(spDc)
        Settings.shared.spotifySession = canvasSessionJson.encodeToString(client.session)
    }
}
