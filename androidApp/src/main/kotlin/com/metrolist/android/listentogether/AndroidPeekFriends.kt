/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.listentogether

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.metrolist.android.MainActivity
import com.metrolist.android.R
import com.metrolist.shared.network.HttpFailureLog
import com.metrolist.shared.peek.PeekLinks
import com.metrolist.shared.peek.friends.PeekFriendsEvent
import com.metrolist.shared.peek.friends.PeekFriendsManager
import com.metrolist.shared.peek.friends.displayName
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * PeekParty friends on Android: online while the app is in front or PeekMusic plays music, so friends see you
 * and can invite you. A closed, silent app is offline.
 */
object AndroidPeekFriends {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playing = MutableStateFlow(false)
    private var started = false

    /** A party to join, from the invite notification's Join button; the app joins it and clears it. */
    val pendingJoin = MutableStateFlow<String?>(null)
    private const val ACTION_JOIN = "com.peekmusic.action.JOIN_FRIEND_PARTY"
    internal const val ACTION_DECLINE = "com.peekmusic.action.DECLINE_FRIEND_PARTY"
    private const val EXTRA_ROOM = "room_code"
    internal const val EXTRA_INVITER = "inviter_code"

    /** MainActivity hands every intent here; true when it was a Join from an invite notification or a friend link. */
    fun consumeJoinIntent(
        context: Context,
        intent: Intent?,
    ): Boolean {
        if (intent?.action == Intent.ACTION_VIEW) {
            val code = intent.dataString?.let(PeekLinks::parseFriendLink) ?: return false
            manager.openFriendLink(code)
            return true
        }
        if (intent?.action != ACTION_JOIN) return false
        intent.getStringExtra(EXTRA_ROOM)?.let { pendingJoin.value = it }
        manager.clearInvite()
        cancelInvite(context)
        return true
    }

    internal fun cancelInvite(context: Context) {
        NotificationManagerCompat.from(context).cancel(INVITE_TAG, 0)
    }

    val manager: PeekFriendsManager by lazy {
        PeekFriendsManager(
            callerScope = scope,
            httpClient =
                HttpClient(CIO) {
                    install(HttpFailureLog)
                    install(WebSockets) { maxFrameSize = 256L * 1024 }
                    install(HttpTimeout) {
                        connectTimeoutMillis = 30_000L
                        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        socketTimeoutMillis = 90_000L
                    }
                },
        )
    }

    /** Called by the music service whenever playback starts or stops. */
    fun setPlaying(value: Boolean) {
        playing.value = value
    }

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch { manager.events.collect { notifyInBackground(app, it) } }
        scope.launch {
            combine(ProcessLifecycleOwner.get().lifecycle.currentStateFlow, playing) { state, isPlaying ->
                state.isAtLeast(Lifecycle.State.STARTED) || isPlaying
            }.distinctUntilChanged().collect(manager::setActive)
        }
    }

    private const val CHANNEL_ID = "peek_friends"
    internal const val INVITE_TAG = "peek-friend-invite"
    private const val REQUEST_TAG = "peek-friend-request"

    /** Invites and requests as notifications when the app is not in front; in front, the app shows them itself. */
    private fun notifyInBackground(
        context: Context,
        event: PeekFriendsEvent,
    ) {
        if (ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val inviterCode = (event as? PeekFriendsEvent.InviteReceived)?.from?.code
        val (tag, title, text, intent, roomCode) =
            when (event) {
                is PeekFriendsEvent.InviteReceived -> {
                    BackgroundNotice(
                        INVITE_TAG,
                        context.getString(R.string.peek_friends_invite_title),
                        context.getString(R.string.peek_friends_invite_text, event.from.displayName),
                        Intent(Intent.ACTION_VIEW, Uri.parse(PeekLinks.partyInvitation(event.roomCode, usePartyParam = true)))
                            .setPackage(context.packageName),
                        event.roomCode,
                    )
                }

                is PeekFriendsEvent.RequestReceived -> {
                    BackgroundNotice(
                        REQUEST_TAG,
                        context.getString(R.string.peek_friends_requests),
                        context.getString(R.string.peek_friends_request_received, event.from.displayName),
                        Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_LISTEN_TOGETHER),
                        null,
                    )
                }

                else -> {
                    return
                }
            }
        val notifications = NotificationManagerCompat.from(context)
        notifications.createNotificationChannel(
            NotificationChannelCompat
                .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.peek_friends_title))
                .build(),
        )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        tag.hashCode(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).apply {
                    if (roomCode != null) {
                        val join =
                            Intent(context, MainActivity::class.java)
                                .setAction(ACTION_JOIN)
                                .putExtra(EXTRA_ROOM, roomCode)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addAction(
                            0,
                            context.getString(R.string.peek_friends_invite_decline),
                            PendingIntent.getBroadcast(
                                context,
                                1,
                                Intent(context, PeekFriendInviteReceiver::class.java)
                                    .setAction(ACTION_DECLINE)
                                    .putExtra(EXTRA_INVITER, inviterCode),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                            ),
                        )
                        addAction(
                            0,
                            context.getString(R.string.peek_friends_join),
                            PendingIntent.getActivity(context, 2, join, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                        )
                    }
                }.build()
        try {
            notifications.notify(tag, 0, notification)
        } catch (_: SecurityException) {
            // Notifications turned off.
        }
    }

    private data class BackgroundNotice(
        val tag: String,
        val title: String,
        val text: String,
        val intent: Intent,
        val roomCode: String?,
    )
}
