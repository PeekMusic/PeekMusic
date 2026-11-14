/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import com.metrolist.shared.BuildConfig

/** Values where the PeekMusic fork differs from upstream Metrolist. Upstream files reference these instead of hardcoding them. */
object PeekFork {
    const val GITHUB_REPOSITORY = "PeekMusic/PeekMusic"
    const val REPOSITORY_URL = "https://github.com/$GITHUB_REPOSITORY"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_REPOSITORY/releases"
    const val LISTEN_TOGETHER_SERVER_URL = "wss://listen.peekmusic.org/ws"
    const val LISTEN_TOGETHER_SERVER_NAME = "PeekMusic Official"
    const val LISTEN_TOGETHER_SERVER_LOCATION = "Germany"
    const val LISTEN_TOGETHER_SERVER_OPERATOR = "Endrit"
    const val USER_AGENT_PRODUCT = "PeekMusic"

    /** Release APK name the legacy (pre-KMP) PeekMusic updater recognizes: prefix `PeekMusic-`, suffix `Release.apk`. */
    fun releaseApkName(version: String): String = "PeekMusic-$version-fossRelease.apk"

    /** Lyprio needs upstream's private signing key; builds without it skip the provider and hide it from settings. */
    val LYPRIO_AVAILABLE: Boolean get() = BuildConfig.LYPRIO_BASE_URL.isNotBlank()

    /** Upstream's Sentry project is not ours, so crash reporting stays off until PeekMusic has its own DSN. */
    const val CRASH_REPORTING_AVAILABLE = false
}
