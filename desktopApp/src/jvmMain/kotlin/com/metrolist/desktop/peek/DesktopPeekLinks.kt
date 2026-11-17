/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.desktop.peek

import com.metrolist.desktop.auth.linuxDiscordDesktopEntry
import com.metrolist.desktop.auth.linuxDiscordLauncher
import com.metrolist.desktop.utils.AppLogger
import com.metrolist.desktop.utils.registerWindowsUriHandler
import com.metrolist.shared.peek.PeekFork
import com.metrolist.shared.utils.OsUtil
import com.metrolist.shared.utils.XdgUtils
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * PeekMusic: makes `peekmusic://` links (the "Open in PeekMusic" buttons on listen.peekmusic.org for friends and
 * parties) open the desktop app. Packages declare the scheme in their desktop entry; an AppImage or a plain install
 * registers it here on start, like the Discord login callback does. A dev run (`gradlew run`) is plain java and
 * cannot be a link handler, so it registers nothing. A link that starts a second instance reaches the running one.
 */
object DesktopPeekLinks {
    private const val MIME_TYPE = "x-scheme-handler/${PeekFork.APP_SCHEME}"
    private const val DESKTOP_FILE = "com.peekmusic.desktop.links.desktop"
    private val packagedEntries = setOf("peekmusic.desktop", "PeekMusic.desktop", "com.peekmusic.desktop.desktop")

    fun register(displayName: String): Boolean =
        runCatching {
            val launcher =
                System.getProperty("jpackage.app-path")
                    ?: ProcessHandle
                        .current()
                        .info()
                        .command()
                        .orElse(null)
            when {
                OsUtil.isWindows -> {
                    registerWindowsUriHandler(launcher?.let(::File), displayName, PeekFork.APP_SCHEME) { key, name, value ->
                        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key)
                        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, name, value)
                    }
                }

                OsUtil.isLinux -> {
                    if (!System.getenv("FLATPAK_ID").isNullOrBlank()) return@runCatching true
                    val query = listOf("xdg-mime", "query", "default", MIME_TYPE)
                    if (run(query)?.trim() in packagedEntries) return@runCatching true
                    val executable = linuxDiscordLauncher(System.getenv("APPIMAGE"), launcher) ?: return@runCatching false
                    val desktopFile = File(XdgUtils.getXdgDataHome(), "applications/$DESKTOP_FILE")
                    desktopFile.parentFile.mkdirs()
                    desktopFile.writeText(
                        linuxDiscordDesktopEntry(executable.absolutePath, displayName)
                            .replace(Regex("(?m)^MimeType=.*$"), "MimeType=$MIME_TYPE;"),
                    )
                    run(listOf("xdg-mime", "default", DESKTOP_FILE, MIME_TYPE)) != null && run(query)?.trim() == DESKTOP_FILE
                }

                // macOS takes the scheme from the app bundle's Info.plist.
                else -> {
                    false
                }
            }
        }.onFailure { AppLogger.warning("PeekLinks", "Unable to register the peekmusic:// handler", it) }.getOrDefault(false)

    private fun run(command: List<String>): String? {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return if (process.exitValue() == 0) process.inputStream.bufferedReader().use { it.readText() } else null
    }
}
