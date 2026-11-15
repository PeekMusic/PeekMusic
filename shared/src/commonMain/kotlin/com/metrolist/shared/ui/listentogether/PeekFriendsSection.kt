/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.ui.listentogether

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.peek_friends_accept
import com.metrolist.shared.generated.resources.peek_friends_add
import com.metrolist.shared.generated.resources.peek_friends_add_failed
import com.metrolist.shared.generated.resources.peek_friends_add_label
import com.metrolist.shared.generated.resources.peek_friends_added
import com.metrolist.shared.generated.resources.peek_friends_code_hint
import com.metrolist.shared.generated.resources.peek_friends_connecting
import com.metrolist.shared.generated.resources.peek_friends_copy_code
import com.metrolist.shared.generated.resources.peek_friends_decline
import com.metrolist.shared.generated.resources.peek_friends_delete
import com.metrolist.shared.generated.resources.peek_friends_delete_confirm
import com.metrolist.shared.generated.resources.peek_friends_deleted
import com.metrolist.shared.generated.resources.peek_friends_empty
import com.metrolist.shared.generated.resources.peek_friends_error_limit
import com.metrolist.shared.generated.resources.peek_friends_error_offline
import com.metrolist.shared.generated.resources.peek_friends_error_own
import com.metrolist.shared.generated.resources.peek_friends_error_unknown
import com.metrolist.shared.generated.resources.peek_friends_in_party
import com.metrolist.shared.generated.resources.peek_friends_invite
import com.metrolist.shared.generated.resources.peek_friends_invite_action
import com.metrolist.shared.generated.resources.peek_friends_invite_decline
import com.metrolist.shared.generated.resources.peek_friends_invite_declined
import com.metrolist.shared.generated.resources.peek_friends_invite_none
import com.metrolist.shared.generated.resources.peek_friends_invite_text
import com.metrolist.shared.generated.resources.peek_friends_invite_title
import com.metrolist.shared.generated.resources.peek_friends_invited
import com.metrolist.shared.generated.resources.peek_friends_join
import com.metrolist.shared.generated.resources.peek_friends_link_text
import com.metrolist.shared.generated.resources.peek_friends_link_title
import com.metrolist.shared.generated.resources.peek_friends_offline
import com.metrolist.shared.generated.resources.peek_friends_online
import com.metrolist.shared.generated.resources.peek_friends_remove
import com.metrolist.shared.generated.resources.peek_friends_remove_confirm
import com.metrolist.shared.generated.resources.peek_friends_request_received
import com.metrolist.shared.generated.resources.peek_friends_request_sent
import com.metrolist.shared.generated.resources.peek_friends_requests
import com.metrolist.shared.generated.resources.peek_friends_sent
import com.metrolist.shared.generated.resources.peek_friends_share_link
import com.metrolist.shared.generated.resources.peek_friends_title
import com.metrolist.shared.generated.resources.peek_friends_waiting
import com.metrolist.shared.generated.resources.peek_friends_withdraw
import com.metrolist.shared.generated.resources.peek_friends_your_code
import com.metrolist.shared.peek.PeekLinks
import com.metrolist.shared.peek.friends.PeekFriend
import com.metrolist.shared.peek.friends.PeekFriendsEvent
import com.metrolist.shared.peek.friends.PeekFriendsManager
import com.metrolist.shared.peek.friends.displayName
import com.metrolist.shared.ui.components.PopupVisibility
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/** PeekMusic: the app's PeekParty friends; null where the platform has none (desktop for now). */
val LocalPeekFriends = staticCompositionLocalOf<PeekFriendsManager?> { null }

/** PeekMusic: the friends part of the PeekParty start page. */
internal fun LazyListScope.peekFriendsItems(
    friends: PeekFriendsManager,
    onJoinRoom: (String) -> Unit,
    onCopyText: (label: String, text: String) -> Unit,
    onShareText: ((String) -> Unit)?,
) {
    item(key = "peek_friends_code") { PeekFriendCodeCard(friends, onCopyText, onShareText) }
    item(key = "peek_friends_add") { PeekAddFriendRow(friends) }
    item(key = "peek_friends_incoming") { PeekIncomingRequests(friends) }
    item(key = "peek_friends_list") { PeekFriendList(friends, onJoinRoom) }
    item(key = "peek_friends_outgoing") { PeekOutgoingRequests(friends) }
}

@Composable
private fun PeekFriendCodeCard(
    friends: PeekFriendsManager,
    onCopyText: (label: String, text: String) -> Unit,
    onShareText: ((String) -> Unit)?,
) {
    val code by friends.friendCode.collectAsState()
    val copyLabel = stringResource(Res.string.peek_friends_copy_code)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.peek_friends_your_code), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = code ?: stringResource(Res.string.peek_friends_connecting),
                    style = if (code != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                    fontFamily = if (code != null) FontFamily.Monospace else null,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(Res.string.peek_friends_code_hint), style = MaterialTheme.typography.bodySmall)
            }
            code?.let { current ->
                IconButton(onClick = { onCopyText(copyLabel, current) }) {
                    Icon(MetrolistIcons.Outlined.ContentCopy, contentDescription = copyLabel)
                }
                if (onShareText != null) {
                    IconButton(onClick = { onShareText(PeekLinks.friendLink(current)) }) {
                        Icon(MetrolistIcons.Outlined.Share, contentDescription = stringResource(Res.string.peek_friends_share_link))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeekAddFriendRow(friends: PeekFriendsManager) {
    var input by remember { mutableStateOf("") }
    val connected by friends.connected.collectAsState()
    val valid = input.length == 9
    val submit = {
        if (valid) {
            friends.addFriend(input)
            input = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { value -> input = value.filter { it.isLetterOrDigit() }.uppercase().take(9) },
            visualTransformation = FriendCodeDashes,
            label = { Text(stringResource(Res.string.peek_friends_add_label)) },
            placeholder = { Text("ABC-123-XYZ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = submit, enabled = valid && connected, shapes = ButtonDefaults.shapes()) {
            Icon(MetrolistIcons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.peek_friends_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** Shows "ABC123XYZ" as "ABC-123-XYZ" while typing, like the code everywhere else. */
private val FriendCodeDashes =
    VisualTransformation { text ->
        val raw = text.text
        val shown = raw.chunked(3).joinToString("-")
        TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                // A dash follows every third character, so offsets 1-3 have none before them, 4-6 one, 7-9 two.
                override fun originalToTransformed(offset: Int): Int = offset + if (offset == 0) 0 else (offset - 1) / 3

                override fun transformedToOriginal(offset: Int): Int = (offset - offset / 4).coerceIn(0, raw.length)
            },
        )
    }

@Composable
private fun PeekIncomingRequests(friends: PeekFriendsManager) {
    val incoming by friends.incomingRequests.collectAsState()
    if (incoming.isEmpty()) return
    SettingsSubScreenGroup(
        title = stringResource(Res.string.peek_friends_requests),
        horizontalPadding = 0.dp,
        items =
            incoming.map { request ->
                SettingsSubScreenItem(
                    title = request.displayName,
                    description = request.code,
                    leadingContent = { PeekFriendAvatar(request) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { friends.declineRequest(request.code) }) {
                                Icon(MetrolistIcons.Outlined.Close, contentDescription = stringResource(Res.string.peek_friends_decline))
                            }
                            IconButton(onClick = { friends.acceptRequest(request.code) }) {
                                Icon(
                                    MetrolistIcons.Filled.Check,
                                    contentDescription = stringResource(Res.string.peek_friends_accept),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
            },
    )
}

@Composable
private fun PeekFriendList(
    friends: PeekFriendsManager,
    onJoinRoom: (String) -> Unit,
) {
    val list by friends.friends.collectAsState()
    var removing by remember { mutableStateOf<PeekFriend?>(null) }
    removing?.let { friend ->
        PopupVisibility(visible = true) {
            AlertDialog(
                onDismissRequest = { removing = null },
                text = { Text(stringResource(Res.string.peek_friends_remove_confirm, friend.displayName)) },
                confirmButton = {
                    TextButton(onClick = {
                        friends.removeFriend(friend.code)
                        removing = null
                    }) { Text(stringResource(Res.string.peek_friends_remove)) }
                },
                dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.cancel)) } },
            )
        }
    }
    if (list.isEmpty()) {
        Text(
            stringResource(Res.string.peek_friends_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val online = stringResource(Res.string.peek_friends_online)
    val offline = stringResource(Res.string.peek_friends_offline)
    val inParty = stringResource(Res.string.peek_friends_in_party)
    SettingsSubScreenGroup(
        horizontalPadding = 0.dp,
        items =
            list.map { friend ->
                SettingsSubScreenItem(
                    title = friend.displayName,
                    description =
                        when {
                            friend.roomCode != null -> inParty
                            friend.online -> online
                            else -> offline
                        },
                    leadingContent = { PeekFriendAvatar(friend) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            friend.roomCode?.let { room ->
                                FilledTonalButton(onClick = { onJoinRoom(room) }, shapes = ButtonDefaults.shapes()) {
                                    Text(stringResource(Res.string.peek_friends_join))
                                }
                            }
                            IconButton(onClick = { removing = friend }) {
                                Icon(
                                    MetrolistIcons.Filled.PersonRemove,
                                    contentDescription = stringResource(Res.string.peek_friends_remove),
                                )
                            }
                        }
                    },
                )
            },
    )
}

@Composable
private fun PeekOutgoingRequests(friends: PeekFriendsManager) {
    val outgoing by friends.outgoingRequests.collectAsState()
    if (outgoing.isEmpty()) return
    val waiting = stringResource(Res.string.peek_friends_waiting)
    SettingsSubScreenGroup(
        title = stringResource(Res.string.peek_friends_sent),
        horizontalPadding = 0.dp,
        items =
            outgoing.map { request ->
                SettingsSubScreenItem(
                    title = request.displayName,
                    description = waiting,
                    leadingContent = { PeekFriendAvatar(request) },
                    trailingContent = {
                        IconButton(onClick = { friends.removeFriend(request.code) }) {
                            Icon(MetrolistIcons.Outlined.Close, contentDescription = stringResource(Res.string.peek_friends_withdraw))
                        }
                    },
                )
            },
    )
}

@Composable
private fun PeekFriendAvatar(friend: PeekFriend) {
    Box {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                friend.displayName
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    .orEmpty(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (friend.online) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (friend.roomCode != null) MaterialTheme.colorScheme.primary else Color(0xFF34A853)),
            )
        }
    }
}

/** PeekMusic: inside a party, the host or a guest can invite online friends who are not in it yet. */
@Composable
internal fun PeekInviteFriendsCard(
    friends: PeekFriendsManager,
    roomCode: String,
) {
    val list by friends.friends.collectAsState()
    val candidates = list.filter { it.online && it.roomCode == null }
    var invited by remember(roomCode) { mutableStateOf(setOf<String>()) }
    // An invite can be sent again once the friend joined and left a party, or after half a minute.
    val candidateCodes = candidates.map { it.code }.toSet()
    LaunchedEffect(candidateCodes) { invited = invited intersect candidateCodes }
    LaunchedEffect(friends) {
        friends.events.collect { event -> if (event is PeekFriendsEvent.InviteDeclined) invited = invited - event.friendCode }
    }
    LaunchedEffect(invited) {
        if (invited.isNotEmpty()) {
            delay(30.seconds)
            invited = emptySet()
        }
    }
    if (candidates.isEmpty()) {
        SettingsSubScreenGroup(title = stringResource(Res.string.peek_friends_invite)) {
            Text(
                stringResource(Res.string.peek_friends_invite_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }
    SettingsSubScreenGroup(
        title = stringResource(Res.string.peek_friends_invite),
        horizontalPadding = 0.dp,
        items =
            candidates.map { friend ->
                SettingsSubScreenItem(
                    title = friend.displayName,
                    description = if (friend.code in invited) stringResource(Res.string.peek_friends_invited, friend.displayName) else null,
                    leadingContent = { PeekFriendAvatar(friend) },
                    trailingContent = {
                        FilledTonalButton(
                            onClick = {
                                friends.inviteToParty(friend.code)
                                invited = invited + friend.code
                            },
                            enabled = friend.code !in invited,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(stringResource(Res.string.peek_friends_invite_action)) }
                    },
                )
            },
    )
}

/** PeekMusic: messages and party invites from PeekParty friends, anywhere in the app. */
@Composable
fun PeekFriendsGlobalEvents(
    friends: PeekFriendsManager,
    onMessage: suspend (String) -> Unit,
    onJoinInvite: (roomCode: String) -> Unit,
) {
    val invite by friends.activeInvite.collectAsState()
    val linkCode by friends.friendLink.collectAsState()
    linkCode?.let { code ->
        PopupVisibility(visible = true) {
            AlertDialog(
                onDismissRequest = friends::clearFriendLink,
                title = { Text(stringResource(Res.string.peek_friends_link_title)) },
                text = { Text(stringResource(Res.string.peek_friends_link_text, code)) },
                confirmButton = {
                    TextButton(onClick = {
                        friends.clearFriendLink()
                        friends.addFriend(code)
                    }) { Text(stringResource(Res.string.peek_friends_add)) }
                },
                dismissButton = { TextButton(onClick = friends::clearFriendLink) { Text(stringResource(Res.string.cancel)) } },
            )
        }
    }
    val currentOnMessage by rememberUpdatedState(onMessage)
    LaunchedEffect(friends) {
        friends.events.collect { event ->
            when (event) {
                is PeekFriendsEvent.InviteReceived -> {
                    Unit // shown from activeInvite
                }

                is PeekFriendsEvent.InviteDeclined -> {
                    val name =
                        friends.friends.value
                            .firstOrNull { it.code == event.friendCode }
                            ?.displayName ?: event.friendCode
                    currentOnMessage(getString(Res.string.peek_friends_invite_declined, name))
                }

                is PeekFriendsEvent.RequestSent -> {
                    currentOnMessage(getString(Res.string.peek_friends_request_sent, event.name))
                }

                is PeekFriendsEvent.FriendAdded -> {
                    currentOnMessage(getString(Res.string.peek_friends_added, event.name))
                }

                is PeekFriendsEvent.RequestReceived -> {
                    currentOnMessage(getString(Res.string.peek_friends_request_received, event.from.displayName))
                }

                is PeekFriendsEvent.AddFailed -> {
                    val reason =
                        when {
                            event.message.contains("own friend code") -> getString(Res.string.peek_friends_error_own)
                            event.message.contains("no one has") -> getString(Res.string.peek_friends_error_unknown)
                            event.message.contains("too many") -> getString(Res.string.peek_friends_error_limit)
                            event.message.contains("Friend request sent") -> null
                            else -> getString(Res.string.peek_friends_add_failed, event.message)
                        }
                    reason?.let { currentOnMessage(it) }
                }

                is PeekFriendsEvent.Failed -> {
                    currentOnMessage(event.message.ifBlank { getString(Res.string.peek_friends_error_offline) })
                }

                PeekFriendsEvent.AccountDeleted -> {
                    currentOnMessage(getString(Res.string.peek_friends_deleted))
                }
            }
        }
    }
    invite?.let { current ->
        PopupVisibility(visible = true) {
            AlertDialog(
                onDismissRequest = { friends.declineInvite(current.from.code) },
                title = { Text(stringResource(Res.string.peek_friends_invite_title)) },
                text = { Text(stringResource(Res.string.peek_friends_invite_text, current.from.displayName)) },
                confirmButton = {
                    TextButton(onClick = {
                        friends.clearInvite()
                        onJoinInvite(current.roomCode)
                    }) { Text(stringResource(Res.string.peek_friends_join)) }
                },
                dismissButton = {
                    TextButton(onClick = { friends.declineInvite(current.from.code) }) {
                        Text(stringResource(Res.string.peek_friends_invite_decline))
                    }
                },
            )
        }
    }
}

@Composable
internal fun PeekDeleteFriendsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    PopupVisibility(visible = visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.peek_friends_delete)) },
            text = { Text(stringResource(Res.string.peek_friends_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(Res.string.peek_friends_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        )
    }
}
