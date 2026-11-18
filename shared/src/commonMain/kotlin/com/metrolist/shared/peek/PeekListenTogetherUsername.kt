/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.settings.Settings

/**
 * PeekMusic: gives PeekParty a name before the first room, like Android: the account name, channel handle or e-mail
 * name, else [fallback]. Joining a room with a blank name fails, so iOS calls this before it builds its manager.
 */
fun ensurePeekListenTogetherUsername(fallback: String) {
    val settings = Settings.shared
    if (settings.listenTogetherUsername.isNotBlank()) return
    settings.listenTogetherUsername =
        settings.accountName
            .ifBlank { settings.accountChannelHandle }
            .ifBlank { settings.accountEmail.substringBefore('@') }
            .ifBlank { fallback }
}
