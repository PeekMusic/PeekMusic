/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

/**
 * PeekMusic's own links: `peekmusic://…` and invitations on [PeekFork.INVITE_HOST]. Incoming links are rewritten to
 * upstream's `metrolist://` and metrolist.cc forms before upstream's parsers see them, so those parsers stay untouched.
 */
object PeekLinks {
    private val inviteHost = Regex("^https?://${Regex.escape(PeekFork.INVITE_HOST)}/", RegexOption.IGNORE_CASE)
    private const val SCHEME_PREFIX = "${PeekFork.APP_SCHEME}://"

    fun toUpstream(link: String): String =
        when {
            link.startsWith(SCHEME_PREFIX, ignoreCase = true) -> "metrolist://" + link.substring(SCHEME_PREFIX.length)
            inviteHost.containsMatchIn(link) -> link.replaceFirst(inviteHost, "https://metrolist.cc/")
            else -> link
        }

    fun partyInvitation(
        roomCode: String,
        usePartyParam: Boolean,
    ): String =
        if (usePartyParam) {
            "https://${PeekFork.INVITE_HOST}/party?r=$roomCode"
        } else {
            "https://${PeekFork.INVITE_HOST}/listen?code=$roomCode"
        }

    /** A link that asks to add [friendCode] as a PeekParty friend. */
    fun friendLink(friendCode: String): String = "https://${PeekFork.INVITE_HOST}/friend?c=$friendCode"

    /** The friend code in a friend link (https or peekmusic://friend/CODE), as ABC-123-XYZ, or null. */
    fun parseFriendLink(link: String): String? {
        val raw =
            Regex("^https?://${Regex.escape(PeekFork.INVITE_HOST)}/friend/?\\?(?:.*&)?c=([^&#]+)", RegexOption.IGNORE_CASE)
                .find(link)
                ?.groupValues
                ?.get(1)
                ?: Regex("^${Regex.escape(SCHEME_PREFIX)}friend/([^/?#]+)", RegexOption.IGNORE_CASE).find(link)?.groupValues?.get(1)
                ?: return null
        val clean = raw.filter { it.isLetterOrDigit() }.uppercase()
        return clean.takeIf { it.length == 9 && it.all { c -> c in 'A'..'Z' || c in '0'..'9' } }?.chunked(3)?.joinToString("-")
    }
}
