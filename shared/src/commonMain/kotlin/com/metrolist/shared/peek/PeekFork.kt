/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.BuildConfig
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.peek_listen_together_location_germany
import org.jetbrains.compose.resources.StringResource

/** Values where the PeekMusic fork differs from upstream Metrolist. Upstream files reference these instead of hardcoding them. */
object PeekFork {
    const val GITHUB_REPOSITORY = "PeekMusic/PeekMusic"
    const val REPOSITORY_URL = "https://github.com/$GITHUB_REPOSITORY"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPOSITORY/releases"
    const val LISTEN_TOGETHER_SERVER_URL = "wss://listen.peekmusic.org/ws"
    const val LISTEN_TOGETHER_SERVER_NAME = "PeekMusic Official"
    val LISTEN_TOGETHER_SERVER_LOCATION: StringResource get() = Res.string.peek_listen_together_location_germany
    const val LISTEN_TOGETHER_SERVER_OPERATOR = "Endrit"
    const val USER_AGENT_PRODUCT = "PeekMusic"

    /** Contact address that lyrics services see in our requests (lrc.red's terms require one). */
    const val CONTACT_EMAIL = "contact@peekmusic.org"

    /** User-Agent for lyrics services: who we are, where to find us, how to reach us. */
    const val LYRICS_CLIENT_USER_AGENT = "$USER_AGENT_PRODUCT (+$REPOSITORY_URL; $CONTACT_EMAIL)"

    /** Folder name for downloads, the audio cache and saved photos, so they never mix with an installed Metrolist. */
    const val MEDIA_FOLDER = "PeekMusic"

    /** URL scheme and invitation host for PeekMusic's own links (see PeekLinks). */
    const val APP_SCHEME = "peekmusic"
    const val INVITE_HOST = "listen.peekmusic.org"

    /** PeekMusic's own privacy policy, linked from onboarding. */
    const val PRIVACY_URL = "https://peekmusic.org/privacy"

    /** Release APK name the legacy (pre-KMP) PeekMusic updater recognizes: prefix `PeekMusic-`, suffix `Release.apk`. */
    fun releaseApkName(version: String): String = "PeekMusic-$version-fossRelease.apk"

    /** Lyprio needs upstream's private signing key; builds without it skip the provider and hide it from settings. */
    val LYPRIO_AVAILABLE: Boolean get() = BuildConfig.LYPRIO_BASE_URL.isNotBlank()

    /** Upstream's Sentry project is not ours, so crash reporting stays off until PeekMusic has its own DSN. */
    const val CRASH_REPORTING_AVAILABLE = false

    /** Upstream refuses to start on Omarchy, and Ryoku's Omarchy compatibility trips that check too. PeekMusic runs anywhere. */
    const val DESKTOP_BLOCKS_OMARCHY = false

    /** PeekMusic publishes no nightly builds, so the updates settings hide upstream's nightly switch. */
    const val NIGHTLY_BUILDS_AVAILABLE = false

    /** Upstream's launch dialog asks for donations to Metrolist's developers; PeekMusic does not show it. */
    const val SUPPORT_PROMPT_AVAILABLE = false
}
