/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek.friends

import com.metrolist.shared.listentogether.network.MessageTypes
import com.metrolist.shared.listentogether.network.PingPayload
import com.metrolist.shared.listentogether.network.PortableMessageCodec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeekFriendsProtocolTest {
    private val inner = PortableMessageCodec()
    private val codec = PeekFriendsCodec(inner)

    @Test
    fun authenticateMatchesTheServerWireFormat() {
        // envelope: type "authenticate", payload {1: "tok", 2: "N", 3: true}
        val expected = "0a0c61757468656e746963617465120a0a03746f6b12014e1801".hexToByteArray()
        assertContentEquals(expected, codec.encode(PeekFriendMessages.AUTHENTICATE, PeekAuthenticate("tok", "N", presenceOnly = true)))
    }

    @Test
    fun upstreamMessagesPassThroughUnchanged() {
        val ping = PingPayload(123, 7)
        assertContentEquals(inner.encode(MessageTypes.PING, ping), codec.encode(MessageTypes.PING, ping))
        val (type, payload) = codec.decode(inner.encode(MessageTypes.PING, ping))
        assertEquals(MessageTypes.PING, type)
        assertEquals(ping, codec.decodePayload(type, payload))
    }

    @Test
    fun friendMessagesRoundTrip() {
        val bytes = codec.encode(PeekFriendMessages.ADD_FRIEND, PeekFriendCode("A1B-2C3-D4E"), requestId = 9)
        val envelope = codec.decodeEnvelope(bytes)
        assertEquals(PeekFriendMessages.ADD_FRIEND, envelope.type)
        assertEquals(9L, envelope.requestId)
        assertEquals(PeekFriendCode("A1B-2C3-D4E"), codec.decodePayload(envelope.type, envelope.payload))

        val deleted = codec.decodeEnvelope(codec.encode(PeekFriendMessages.DELETE_FRIEND_ACCOUNT, null))
        assertNull(codec.decodePayload(deleted.type, deleted.payload))
    }

    @Test
    fun decodesServerPayloads() {
        // friends_status {1: {1:"AAA-BBB-CCC", 2:"Ana", 3:true, 4:"ROOM1234"}, 2: {1:"DDD-EEE-FFF", 2:"Bo"}}
        val status =
            PeekFriendMessages.decodePayload(
                PeekFriendMessages.FRIENDS_STATUS,
                "0a1e0a0b4141412d4242422d4343431203416e6118012208524f4f4d3132333412110a0b4444442d4545452d46464612024230".hexToByteArray(),
            ) as PeekFriendsStatus
        assertEquals(listOf(PeekFriendInfo("AAA-BBB-CCC", "Ana", true, "ROOM1234")), status.friends)
        assertEquals(listOf(PeekFriendInfo("DDD-EEE-FFF", "B0", false, null)), status.incomingRequests)
        assertEquals(emptyList(), status.outgoingRequests)

        // friend_added {1:"X", 2:true, 4:true, 5:"Bo"}
        val added = PeekFriendMessages.decodePayload(PeekFriendMessages.FRIEND_ADDED, "0a0158100120012a02426f".hexToByteArray())
        assertEquals(PeekFriendAdded("X", true, null, true, "Bo"), added)
    }
}
