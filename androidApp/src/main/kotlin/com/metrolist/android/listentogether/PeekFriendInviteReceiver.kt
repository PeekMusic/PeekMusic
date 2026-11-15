/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.listentogether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** "Decline" on a PeekParty invite notification: dismisses it and tells the inviter, who can invite again. */
class PeekFriendInviteReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != AndroidPeekFriends.ACTION_DECLINE) return
        intent.getStringExtra(AndroidPeekFriends.EXTRA_INVITER)?.let(AndroidPeekFriends.manager::declineInvite)
        AndroidPeekFriends.cancelInvite(context)
    }
}
