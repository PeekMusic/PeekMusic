/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.listentogether.parseListenTogetherInvitation
import com.metrolist.shared.utils.ShareUrls
import com.metrolist.shared.utils.extractListenTogetherRoomCode
import com.metrolist.shared.utils.normalizeMetrolistShareUrl
import kotlin.test.Test
import kotlin.test.assertEquals

class PeekLinksTest {
    @Test
    fun peekMusicInvitationsOpenTheRoom() {
        assertEquals("ROOM1234", parseListenTogetherInvitation("peekmusic://listen-together/room1234"))
        assertEquals("ROOM1234", parseListenTogetherInvitation("https://listen.peekmusic.org/listen?code=ROOM1234"))
        assertEquals("ROOM1234", extractListenTogetherRoomCode("https://listen.peekmusic.org/party?r=room1234"))
        assertEquals("ROOM1234", extractListenTogetherRoomCode(ShareUrls.party("ROOM1234")))
    }

    @Test
    fun metrolistLinksStillWork() {
        assertEquals("ROOM1234", parseListenTogetherInvitation("metrolist://listen-together/ROOM1234"))
        assertEquals("ROOM1234", extractListenTogetherRoomCode("https://metrolist.cc/listen?code=ROOM1234"))
    }

    @Test
    fun peekMusicSchemeOpensYouTubeMusicPages() {
        assertEquals("https://music.youtube.com/channel/UC123", normalizeMetrolistShareUrl("peekmusic://artist/UC123"))
    }
}
