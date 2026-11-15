/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek.friends

import com.metrolist.shared.listentogether.network.ListenTogetherEnvelope
import com.metrolist.shared.listentogether.network.ListenTogetherMessageCodec
import com.metrolist.shared.listentogether.network.ProtoFields
import com.metrolist.shared.listentogether.network.decompressListenTogetherPayload
import com.metrolist.shared.listentogether.network.message
import com.metrolist.shared.listentogether.network.orEmpty

/**
 * PeekParty friends on PeekMusic's server. Field numbers follow `server/peekproto/peek_friends.proto`; the envelope is
 * upstream's, so friend messages travel next to Listen Together's without touching its codecs.
 */
object PeekFriendMessages {
    const val AUTHENTICATE = "authenticate"
    const val AUTH_SUCCESS = "auth_success"
    const val ADD_FRIEND = "add_friend"
    const val REMOVE_FRIEND = "remove_friend"
    const val FRIEND_REMOVED = "friend_removed"
    const val FRIENDS_STATUS = "friends_status"
    const val FRIEND_PRESENCE_UPDATE = "friend_presence_update"
    const val FRIEND_ADDED = "friend_added"
    const val ACCEPT_FRIEND_REQUEST = "accept_friend_request"
    const val DECLINE_FRIEND_REQUEST = "decline_friend_request"
    const val FRIEND_REQUEST = "friend_request"
    const val INVITE_FRIEND = "invite_friend"
    const val FRIEND_INVITE = "friend_invite"
    const val DECLINE_INVITE = "decline_invite"
    const val FRIEND_INVITE_DECLINED = "friend_invite_declined"
    const val DELETE_FRIEND_ACCOUNT = "delete_friend_account"
    const val FRIEND_ACCOUNT_DELETED = "friend_account_deleted"

    /** Server feature: friend requests, invites and account deletion. */
    const val FEATURE = "peek_friends_v2"

    val all =
        setOf(
            AUTHENTICATE,
            AUTH_SUCCESS,
            ADD_FRIEND,
            REMOVE_FRIEND,
            FRIEND_REMOVED,
            FRIENDS_STATUS,
            FRIEND_PRESENCE_UPDATE,
            FRIEND_ADDED,
            ACCEPT_FRIEND_REQUEST,
            DECLINE_FRIEND_REQUEST,
            FRIEND_REQUEST,
            INVITE_FRIEND,
            FRIEND_INVITE,
            DECLINE_INVITE,
            FRIEND_INVITE_DECLINED,
            DELETE_FRIEND_ACCOUNT,
            FRIEND_ACCOUNT_DELETED,
        )

    private val codeMessages =
        setOf(
            ADD_FRIEND,
            REMOVE_FRIEND,
            FRIEND_REMOVED,
            ACCEPT_FRIEND_REQUEST,
            DECLINE_FRIEND_REQUEST,
            INVITE_FRIEND,
            DECLINE_INVITE,
            FRIEND_INVITE_DECLINED,
        )
    private val payloadless = setOf(DELETE_FRIEND_ACCOUNT, FRIEND_ACCOUNT_DELETED)

    internal fun encodePayload(
        type: String,
        payload: Any?,
    ): ByteArray =
        when {
            type == AUTHENTICATE && payload is PeekAuthenticate -> {
                message {
                    string(1, payload.authToken)
                    string(2, payload.username)
                    bool(3, payload.presenceOnly)
                }
            }

            type in codeMessages && payload is PeekFriendCode -> {
                message { string(1, payload.friendCode) }
            }

            type in payloadless && payload == null -> {
                byteArrayOf()
            }

            else -> {
                throw IllegalArgumentException("Invalid PeekParty friends payload: $type")
            }
        }

    internal fun decodePayload(
        type: String,
        bytes: ByteArray,
    ): Any? {
        val f = ProtoFields(bytes)
        return when (type) {
            AUTHENTICATE -> {
                PeekAuthenticate(f.string(1), f.string(2), f.bool(3))
            }

            AUTH_SUCCESS -> {
                PeekAuthSuccess(f.string(1))
            }

            FRIENDS_STATUS -> {
                PeekFriendsStatus(
                    f.repeated(1).map { it.friend() },
                    f.repeated(2).map { it.friend() },
                    f.repeated(3).map { it.friend() },
                )
            }

            FRIEND_PRESENCE_UPDATE -> {
                PeekFriendPresence(f.nested(1).orEmpty().friend())
            }

            FRIEND_ADDED -> {
                PeekFriendAdded(f.string(1), f.bool(2), f.optionalString(3), f.bool(4), f.optionalString(5))
            }

            FRIEND_REQUEST -> {
                PeekFriendRequest(f.nested(1).orEmpty().friend())
            }

            FRIEND_INVITE -> {
                PeekFriendInvite(f.nested(1).orEmpty().friend(), f.string(2))
            }

            in payloadless -> {
                null
            }

            in codeMessages -> {
                PeekFriendCode(f.string(1))
            }

            else -> {
                throw IllegalArgumentException("Unknown PeekParty friends message: $type")
            }
        }
    }

    private fun ProtoFields.friend() = PeekFriendInfo(string(1), string(2), bool(3), optionalString(4))
}

data class PeekAuthenticate(
    val authToken: String,
    val username: String,
    val presenceOnly: Boolean = false,
)

data class PeekFriendCode(
    val friendCode: String,
)

data class PeekAuthSuccess(
    val friendCode: String,
)

data class PeekFriendInfo(
    val friendCode: String,
    val username: String,
    val isOnline: Boolean,
    val activeRoomCode: String?,
)

data class PeekFriendsStatus(
    val friends: List<PeekFriendInfo>,
    val incomingRequests: List<PeekFriendInfo>,
    val outgoingRequests: List<PeekFriendInfo>,
)

data class PeekFriendPresence(
    val friend: PeekFriendInfo,
)

data class PeekFriendAdded(
    val friendCode: String,
    val success: Boolean,
    val errorMessage: String?,
    val pending: Boolean,
    val username: String?,
)

data class PeekFriendRequest(
    val from: PeekFriendInfo,
)

data class PeekFriendInvite(
    val from: PeekFriendInfo,
    val roomCode: String,
)

/**
 * Wraps a Listen Together codec so it also carries friend messages. Friend envelopes are built and read here;
 * everything else goes to [inner] unchanged.
 */
internal class PeekFriendsCodec(
    private val inner: ListenTogetherMessageCodec,
) : ListenTogetherMessageCodec {
    private companion object {
        const val MAX_PAYLOAD_BYTES = 256 * 1024
    }

    override var compressionEnabled: Boolean
        get() = inner.compressionEnabled
        set(value) {
            inner.compressionEnabled = value
        }

    override fun encode(
        msgType: String,
        payload: Any?,
        requestId: Long,
    ): ByteArray {
        if (msgType !in PeekFriendMessages.all) return inner.encode(msgType, payload, requestId)
        val bytes = PeekFriendMessages.encodePayload(msgType, payload)
        return message {
            string(1, msgType)
            bytes(2, bytes)
            long(4, requestId)
        }
    }

    override fun decodeEnvelope(data: ByteArray): ListenTogetherEnvelope {
        val type = runCatching { ProtoFields(data).string(1) }.getOrNull()
        if (type == null || type !in PeekFriendMessages.all) return inner.decodeEnvelope(data)
        val envelope = ProtoFields(data)
        val raw = envelope.bytes(2)
        val payload = if (envelope.bool(3)) decompressListenTogetherPayload(raw, MAX_PAYLOAD_BYTES) else raw
        require(payload.size <= MAX_PAYLOAD_BYTES) { "PeekParty friends payload exceeds the size limit" }
        return ListenTogetherEnvelope(type, payload, envelope.long(4))
    }

    override fun decodePayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any? =
        if (msgType in PeekFriendMessages.all) {
            PeekFriendMessages.decodePayload(msgType, payloadBytes)
        } else {
            inner.decodePayload(msgType, payloadBytes)
        }
}
