/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek.friends

import com.metrolist.shared.BuildConfig
import com.metrolist.shared.deviceconnect.DeviceCrypto
import com.metrolist.shared.listentogether.network.ClientCapabilities
import com.metrolist.shared.listentogether.network.ErrorPayload
import com.metrolist.shared.listentogether.network.MessageTypes
import com.metrolist.shared.listentogether.network.ServerCapabilities
import com.metrolist.shared.listentogether.network.createListenTogetherMessageCodec
import com.metrolist.shared.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** A friend or an open request, as the PeekParty screens show it. */
@Serializable
data class PeekFriend(
    val code: String,
    val name: String,
    val online: Boolean = false,
    val roomCode: String? = null,
)

sealed interface PeekFriendsEvent {
    data class RequestSent(
        val name: String,
    ) : PeekFriendsEvent

    data class FriendAdded(
        val name: String,
    ) : PeekFriendsEvent

    data class AddFailed(
        val message: String,
    ) : PeekFriendsEvent

    data class RequestReceived(
        val from: PeekFriend,
    ) : PeekFriendsEvent

    data class InviteReceived(
        val from: PeekFriend,
        val roomCode: String,
    ) : PeekFriendsEvent

    data class InviteDeclined(
        val friendCode: String,
    ) : PeekFriendsEvent

    data class Failed(
        val message: String,
    ) : PeekFriendsEvent

    data object AccountDeleted : PeekFriendsEvent
}

/**
 * PeekParty friends: its own small connection to PeekMusic's server, kept open while [setActive] says so (the
 * app is in front or music plays). Listen Together's party connection only reports which party you are in.
 */
class PeekFriendsManager(
    callerScope: CoroutineScope,
    private val httpClient: HttpClient,
    private val serverUrl: () -> String = { Settings.shared.listenTogetherServerUrl },
    private val reconnectDelayMs: (attempt: Int) -> Long = { attempt -> (1_000L shl minOf(attempt, 6)).coerceAtMost(60_000L) },
    private val idleDisconnectMs: Long = 60_000L,
) {
    private val scope = CoroutineScope(callerScope.coroutineContext + SupervisorJob(callerScope.coroutineContext[Job]))
    private val codec = PeekFriendsCodec(createListenTogetherMessageCodec())
    private val sendMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _friendCode = MutableStateFlow<String?>(null)
    val friendCode: StateFlow<String?> = _friendCode.asStateFlow()
    private val _friends = MutableStateFlow(loadCache())
    val friends: StateFlow<List<PeekFriend>> = _friends.asStateFlow()
    private val _incomingRequests = MutableStateFlow<List<PeekFriend>>(emptyList())
    val incomingRequests: StateFlow<List<PeekFriend>> = _incomingRequests.asStateFlow()
    private val _outgoingRequests = MutableStateFlow<List<PeekFriend>>(emptyList())
    val outgoingRequests: StateFlow<List<PeekFriend>> = _outgoingRequests.asStateFlow()
    private val _activeInvite = MutableStateFlow<PeekFriendsEvent.InviteReceived?>(null)

    /** The party invite waiting for an answer; the app shows it until it is joined, declined or cleared. */
    val activeInvite: StateFlow<PeekFriendsEvent.InviteReceived?> = _activeInvite.asStateFlow()
    private val _friendLink = MutableStateFlow<String?>(null)

    /** A friend code from an opened friend link, waiting for the user to confirm the request. */
    val friendLink: StateFlow<String?> = _friendLink.asStateFlow()
    private val _events = MutableSharedFlow<PeekFriendsEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PeekFriendsEvent> = _events.asSharedFlow()

    private var active = false
    private var connectionJob: Job? = null
    private var idleJob: Job? = null
    private var session: DefaultClientWebSocketSession? = null

    /** Cuts a reconnect wait short, e.g. when the app comes back to the front. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Online while true; after it turns false the connection stays a minute, so quick app switches don't flicker. */
    fun setActive(value: Boolean) {
        active = value
        if (value) {
            wake.trySend(Unit)
            idleJob?.cancel()
            idleJob = null
            if (connectionJob?.isActive != true) connectionJob = scope.launch { runConnection() }
        } else if (idleJob == null && connectionJob?.isActive == true) {
            idleJob =
                scope.launch {
                    delay(idleDisconnectMs.milliseconds)
                    idleJob = null
                    if (!active) disconnect()
                }
        }
    }

    /** Sends the current name again, e.g. after it changed in PeekParty. */
    fun refreshName() {
        scope.launch { send(PeekFriendMessages.AUTHENTICATE, PeekAuthenticate(authToken(), Settings.shared.listenTogetherUsername)) }
    }

    fun addFriend(code: String) = request(PeekFriendMessages.ADD_FRIEND, code)

    fun acceptRequest(code: String) = request(PeekFriendMessages.ACCEPT_FRIEND_REQUEST, code)

    fun declineRequest(code: String) {
        _incomingRequests.value = _incomingRequests.value.filterNot { it.code == code }
        request(PeekFriendMessages.DECLINE_FRIEND_REQUEST, code)
    }

    /** Ends a friendship or withdraws your own request. */
    fun removeFriend(code: String) {
        _friends.value = _friends.value.filterNot { it.code == code }
        _outgoingRequests.value = _outgoingRequests.value.filterNot { it.code == code }
        saveCache()
        request(PeekFriendMessages.REMOVE_FRIEND, code)
    }

    fun inviteToParty(code: String) = request(PeekFriendMessages.INVITE_FRIEND, code)

    fun openFriendLink(code: String) {
        _friendLink.value = code
    }

    fun clearFriendLink() {
        _friendLink.value = null
    }

    /** The invite was answered elsewhere (e.g. joined from the notification). */
    fun clearInvite() {
        _activeInvite.value = null
    }

    /** Turns the open invite down; the inviter can invite again right away. */
    fun declineInvite(inviterCode: String) {
        if (_activeInvite.value?.from?.code == inviterCode) _activeInvite.value = null
        scope.launch { sendWhenConnected(PeekFriendMessages.DECLINE_INVITE, PeekFriendCode(inviterCode)) }
    }

    /** Forgets your friend code, friends and requests on the server, and starts over with a fresh token. */
    fun deleteAccount() {
        scope.launch {
            if (!sendWhenConnected(PeekFriendMessages.DELETE_FRIEND_ACCOUNT, null)) {
                _events.tryEmit(PeekFriendsEvent.Failed(""))
            }
        }
    }

    private fun request(
        type: String,
        code: String,
    ) {
        scope.launch {
            if (!sendWhenConnected(type, PeekFriendCode(code))) _events.tryEmit(PeekFriendsEvent.Failed(""))
        }
    }

    private fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        val current = session
        session = null
        _connected.value = false
        markAllOffline()
        scope.launch { runCatching { current?.close() } }
    }

    private suspend fun runConnection() {
        var attempt = 0
        while (active || idleJob != null) {
            if (!Settings.shared.listenTogetherEnabled) {
                delay(5.seconds)
                continue
            }
            try {
                val ws = httpClient.webSocketSession { url(serverUrl()) }
                session = ws
                ws.send(
                    Frame.Binary(
                        true,
                        codec.encode(
                            MessageTypes.CLIENT_CAPABILITIES,
                            ClientCapabilities(
                                supportsProtobuf = true,
                                supportsCompression = false,
                                clientVersion = BuildConfig.APP_VERSION,
                                features = listOf(PeekFriendMessages.FEATURE),
                            ),
                        ),
                    ),
                )
                val capabilities =
                    withTimeoutOrNull(5.seconds) {
                        val frame = ws.incoming.receive() as? Frame.Binary ?: return@withTimeoutOrNull null
                        val envelope = codec.decodeEnvelope(frame.data)
                        codec.decodePayload(envelope.type, envelope.payload) as? ServerCapabilities
                    }
                if (capabilities == null || PeekFriendMessages.FEATURE !in capabilities.features) {
                    ws.close()
                    session = null
                    waitBeforeRetry(++attempt)
                    continue
                }
                send(PeekFriendMessages.AUTHENTICATE, PeekAuthenticate(authToken(), Settings.shared.listenTogetherUsername))
                for (frame in ws.incoming) {
                    if (frame !is Frame.Binary) continue
                    val handled =
                        runCatching {
                            val envelope = codec.decodeEnvelope(frame.data)
                            handle(envelope.type, codec.decodePayload(envelope.type, envelope.payload))
                        }
                    if (handled.isSuccess) attempt = 0
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Network trouble: retry below.
            } finally {
                session = null
                _connected.value = false
                markAllOffline()
            }
            if (!active && idleJob == null) break
            waitBeforeRetry(++attempt)
        }
    }

    private suspend fun waitBeforeRetry(attempt: Int) {
        withTimeoutOrNull(reconnectDelayMs(attempt).milliseconds) { wake.receive() }
    }

    /**
     * Sends once the friends connection is up. A tap right after opening the app (e.g. on a friend link) would
     * otherwise fail while it still connects; with the app in the back, the connection opens just for this.
     */
    private suspend fun sendWhenConnected(
        type: String,
        payload: Any?,
    ): Boolean {
        val wasActive = active
        if (!wasActive) setActive(true)
        try {
            if (!_connected.value) {
                wake.trySend(Unit)
                withTimeoutOrNull(CONNECT_WAIT) { connected.first { it } }
            }
            return send(type, payload)
        } finally {
            if (!wasActive) setActive(false)
        }
    }

    private suspend fun send(
        type: String,
        payload: Any?,
    ): Boolean {
        val ws = session ?: return false
        return sendMutex.withLock {
            runCatching { ws.send(Frame.Binary(true, codec.encode(type, payload))) }.isSuccess
        }
    }

    internal fun handle(
        type: String,
        payload: Any?,
    ) {
        when (payload) {
            is PeekAuthSuccess -> {
                _friendCode.value = payload.friendCode
                _connected.value = true
            }

            is PeekFriendsStatus -> {
                _friends.value = payload.friends.map { it.toFriend() }.sortedForList()
                _incomingRequests.value = payload.incomingRequests.map { it.toFriend() }
                _outgoingRequests.value = payload.outgoingRequests.map { it.toFriend() }
                saveCache()
            }

            is PeekFriendPresence -> {
                val friend = payload.friend.toFriend()
                val wasOutgoing = _outgoingRequests.value.any { it.code == friend.code }
                _outgoingRequests.value = _outgoingRequests.value.filterNot { it.code == friend.code }
                _incomingRequests.value = _incomingRequests.value.filterNot { it.code == friend.code }
                _friends.value = (_friends.value.filterNot { it.code == friend.code } + friend).sortedForList()
                if (wasOutgoing) _events.tryEmit(PeekFriendsEvent.FriendAdded(friend.displayName))
                saveCache()
            }

            is PeekFriendAdded -> {
                when {
                    !payload.success -> {
                        _events.tryEmit(PeekFriendsEvent.AddFailed(payload.errorMessage.orEmpty()))
                    }

                    payload.pending -> {
                        val friend = PeekFriend(payload.friendCode, payload.username.orEmpty())
                        _outgoingRequests.value = _outgoingRequests.value.filterNot { it.code == friend.code } + friend
                        _events.tryEmit(PeekFriendsEvent.RequestSent(friend.displayName))
                    }

                    else -> {
                        _events.tryEmit(PeekFriendsEvent.FriendAdded(payload.username?.ifBlank { null } ?: payload.friendCode))
                    }
                }
            }

            is PeekFriendRequest -> {
                val from = payload.from.toFriend()
                _incomingRequests.value = _incomingRequests.value.filterNot { it.code == from.code } + from
                _events.tryEmit(PeekFriendsEvent.RequestReceived(from))
            }

            is PeekFriendInvite -> {
                val invite = PeekFriendsEvent.InviteReceived(payload.from.toFriend(), payload.roomCode)
                _activeInvite.value = invite
                _events.tryEmit(invite)
            }

            is PeekFriendCode -> {
                if (type == PeekFriendMessages.FRIEND_INVITE_DECLINED) {
                    _events.tryEmit(PeekFriendsEvent.InviteDeclined(payload.friendCode))
                } else if (type == PeekFriendMessages.FRIEND_REMOVED) {
                    _friends.value = _friends.value.filterNot { it.code == payload.friendCode }
                    _incomingRequests.value = _incomingRequests.value.filterNot { it.code == payload.friendCode }
                    _outgoingRequests.value = _outgoingRequests.value.filterNot { it.code == payload.friendCode }
                    saveCache()
                }
            }

            is ErrorPayload -> {
                _events.tryEmit(PeekFriendsEvent.Failed(payload.message))
            }

            null -> {
                if (type == PeekFriendMessages.FRIEND_ACCOUNT_DELETED) {
                    Settings.shared.listenTogetherAuthToken = ""
                    _friendCode.value = null
                    _friends.value = emptyList()
                    _incomingRequests.value = emptyList()
                    _outgoingRequests.value = emptyList()
                    saveCache()
                    _events.tryEmit(PeekFriendsEvent.AccountDeleted)
                    refreshName()
                }
            }
        }
    }

    private fun markAllOffline() {
        _friends.value = _friends.value.map { it.copy(online = false, roomCode = null) }.sortedForList()
    }

    private fun loadCache(): List<PeekFriend> =
        runCatching {
            json.decodeFromString(ListSerializer(PeekFriend.serializer()), Settings.shared.peekFriendsCache)
        }.getOrDefault(emptyList()).map { it.copy(online = false, roomCode = null) }

    private fun saveCache() {
        Settings.shared.peekFriendsCache =
            json.encodeToString(ListSerializer(PeekFriend.serializer()), _friends.value.map { it.copy(online = false, roomCode = null) })
    }

    companion object {
        private val CONNECT_WAIT = 10.seconds

        /** The app's PeekParty account token; created on first use, PeekMusic 1.3.0's is kept. */
        fun authToken(): String =
            Settings.shared.listenTogetherAuthToken.ifBlank {
                DeviceCrypto
                    .secureRandomBytes(16)
                    .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
                    .also { Settings.shared.listenTogetherAuthToken = it }
            }

        /** What the party connection sends so friends see which party you are in; null before friends were used. */
        fun partySignIn(): PeekAuthenticate? =
            Settings.shared.listenTogetherAuthToken.takeIf { it.isNotBlank() }?.let {
                PeekAuthenticate(it, Settings.shared.listenTogetherUsername, presenceOnly = true)
            }
    }
}

val PeekFriend.displayName: String get() = name.ifBlank { code }

private fun PeekFriendInfo.toFriend() = PeekFriend(friendCode, username, isOnline, activeRoomCode)

/** Online first, then in a party, then by name. */
private fun List<PeekFriend>.sortedForList() =
    sortedWith(compareByDescending<PeekFriend> { it.online }.thenByDescending { it.roomCode != null }.thenBy { it.displayName.lowercase() })
