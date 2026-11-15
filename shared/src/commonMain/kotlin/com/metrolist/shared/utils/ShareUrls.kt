package com.metrolist.shared.utils

import com.metrolist.shared.peek.PeekLinks

object ShareUrls {
    private const val MUSIC_DOMAIN = "https://music.youtube.com"

    fun song(
        videoId: String,
        startSeconds: Long? = null,
    ): String = "$MUSIC_DOMAIN/watch?v=$videoId" + (startSeconds?.let { "&t=${it.coerceAtLeast(0)}s" } ?: "")

    fun party(
        roomCode: String,
        usePartyParam: Boolean = true,
    ): String = PeekLinks.partyInvitation(roomCode, usePartyParam) // PeekMusic: invitations to PeekMusic's own server

    fun playlist(playlistId: String): String = "$MUSIC_DOMAIN/playlist?list=${playlistId.removePrefix("VL")}"

    fun album(albumId: String): String = "$MUSIC_DOMAIN/browse/$albumId"

    fun artist(artistId: String): String = "$MUSIC_DOMAIN/channel/$artistId"

    fun podcast(podcastId: String): String = "$MUSIC_DOMAIN/browse/$podcastId"
}
