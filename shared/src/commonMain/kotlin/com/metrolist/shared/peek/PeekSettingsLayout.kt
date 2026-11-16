/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.appearance_player
import com.metrolist.shared.generated.resources.peek_settings_appearance_description
import com.metrolist.shared.generated.resources.peek_settings_audio
import com.metrolist.shared.generated.resources.peek_settings_audio_description
import com.metrolist.shared.generated.resources.peek_settings_home_library_description
import com.metrolist.shared.generated.resources.peek_settings_player_description
import com.metrolist.shared.generated.resources.peek_settings_privacy_description
import com.metrolist.shared.generated.resources.peek_settings_queue_description
import com.metrolist.shared.generated.resources.peek_settings_translation
import com.metrolist.shared.generated.resources.peek_settings_translation_description
import com.metrolist.shared.generated.resources.peek_title
import com.metrolist.shared.generated.resources.peek_video_filter
import com.metrolist.shared.generated.resources.peek_video_filter_all
import com.metrolist.shared.generated.resources.peek_video_filter_description
import com.metrolist.shared.generated.resources.peek_video_filter_music
import com.metrolist.shared.generated.resources.peek_video_filter_none
import com.metrolist.shared.generated.resources.settings_content_title
import com.metrolist.shared.generated.resources.settings_interface
import com.metrolist.shared.generated.resources.settings_more_options
import com.metrolist.shared.generated.resources.settings_playback
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.settings.SettingsChoice
import com.metrolist.shared.ui.settings.SettingsControl
import com.metrolist.shared.ui.settings.SettingsGroupRegistration
import com.metrolist.shared.ui.settings.SettingsItemRegistration
import com.metrolist.shared.ui.settings.SettingsPageId
import com.metrolist.shared.ui.settings.SettingsRootGroupRegistration
import com.metrolist.shared.ui.settings.SettingsUiRegistry
import org.jetbrains.compose.resources.stringResource

/**
 * PeekMusic's settings layout on top of upstream's registry: fewer switches, and pages grouped like the legacy app
 * (Appearance, Navigation, Player, Audio, Queue, Lyrics, Content, Home and library). Decided in the settings review of
 * 2026-11-15. Hidden settings keep a fixed value through [PeekDefaults.pinHidden].
 */
internal object PeekSettingsLayout {
    val hiddenItemIds =
        setOf(
            // Appearance and navigation
            "appIcon",
            "uiEnhancements",
            "slimNavigation",
            "forceBottomNavBar",
            "swipeThumbnail",
            "swipeToSong",
            "swipeToRemoveSong",
            // Player
            "playerStyle",
            "roundedPlayButton",
            "expressiveMiniPlayerShapeMotion",
            // Audio and queue
            "skipSilence",
            "skipSilenceInstant",
            "keepPausedOnSongChange",
            "varispeed",
            "audioTrackPlaybackParams",
            "seekExtraSeconds",
            "persistentQueue",
            "promptContinuePlayback",
            "rememberShuffleRepeat",
            // Lyrics: translation is Google Translate only, always automatic
            "lyricsAutoScroll",
            "preferWordSyncedLyrics",
            "cyrillicPerLine",
            "aiSetup",
            "activationMode",
            "translationMode",
            "provider",
            "openRouterSignIn",
            "apiKey",
            "baseUrl",
            "deepLFormality",
            "translationModel",
            "detectorModel",
            "prompt",
            "systemPrompt",
            "aiConnectionTest",
            // Content and privacy
            "explicitOnly",
            "blockScreenshots",
            "queueOfflineHistory",
        )
}

/** Applies [PeekSettingsLayout] to upstream's organized registry. */
@Composable
internal fun SettingsUiRegistry.peekLayout(): SettingsUiRegistry {
    val moreSettings = stringResource(Res.string.settings_more_options)
    val player = stringResource(Res.string.appearance_player)
    val playerDescription = stringResource(Res.string.peek_settings_player_description)
    val translation = stringResource(Res.string.peek_settings_translation)
    val translationDescription = stringResource(Res.string.peek_settings_translation_description)
    return withoutItems(PeekSettingsLayout.hiddenItemIds)
        .withoutNavigationTo(SettingsPageId.APPEARANCE_NAVIGATION, SettingsPageId.PLAYER_QUEUE, SettingsPageId.CONTENT_HOME)
        // Artwork (canvas, animated covers, artist videos) is a top-level page, not a link inside the player pages.
        .withoutItems(setOf("playerArtwork"))
        // The player's own page collects everything about the player; Appearance keeps theme and density.
        .moveGroup(SettingsPageId.APPEARANCE, "playerAppearance", SettingsPageId.PLAYER_STYLE, "more", moreSettings)
        .moveItems(SettingsPageId.PRIVACY, setOf("blockedArtists"), SettingsPageId.CONTENT, "blocklists", null)
        .moveItems(SettingsPageId.PRIVACY, setOf("hiddenHomeItems", "hiddenItems"), SettingsPageId.CONTENT_HOME, "hidden", null)
        .retitlePage(
            SettingsPageId.APPEARANCE,
            subtitle = stringResource(Res.string.peek_settings_appearance_description),
        ).retitlePage(SettingsPageId.APPEARANCE_NAVIGATION, icon = MetrolistIcons.Default.Menu)
        .retitlePage(SettingsPageId.PLAYER_ARTWORK, icon = PeekIcons.Animation)
        .retitlePage(SettingsPageId.PRIVACY, subtitle = stringResource(Res.string.peek_settings_privacy_description))
        .retitlePage(SettingsPageId.PLAYER_STYLE, player, playerDescription, MetrolistIcons.Outlined.SmartDisplay)
        .retitlePage(
            SettingsPageId.PLAYER,
            stringResource(Res.string.peek_settings_audio),
            stringResource(Res.string.peek_settings_audio_description),
            MetrolistIcons.Outlined.Headphones,
        ).retitlePage(
            SettingsPageId.PLAYER_QUEUE,
            subtitle = stringResource(Res.string.peek_settings_queue_description),
            icon = MetrolistIcons.AutoMirrored.Outlined.QueueMusic,
        ).retitlePage(
            SettingsPageId.CONTENT_HOME,
            subtitle = stringResource(Res.string.peek_settings_home_library_description),
            icon = MetrolistIcons.Outlined.Home,
        ).retitlePage(SettingsPageId.LYRICS_AI_TRANSLATION, translation, translationDescription)
        .retitleItem("aiTranslation", translation, translationDescription)
        .retitleItem("showLyricsOnPlayer", stringResource(Res.string.peek_title))
        .withVideoFilterChoice(
            title = stringResource(Res.string.peek_video_filter),
            description = stringResource(Res.string.peek_video_filter_description),
            labels =
                listOf(
                    VIDEO_FILTER_ALL to stringResource(Res.string.peek_video_filter_all),
                    VIDEO_FILTER_MUSIC to stringResource(Res.string.peek_video_filter_music),
                    VIDEO_FILTER_NONE to stringResource(Res.string.peek_video_filter_none),
                ),
        ).withRootGroups(
            listOf(
                SettingsRootGroupRegistration(
                    stringResource(Res.string.settings_interface),
                    listOf(
                        SettingsPageId.APPEARANCE,
                        SettingsPageId.APPEARANCE_NAVIGATION,
                        SettingsPageId.PLAYER_STYLE,
                        SettingsPageId.PLAYER_ARTWORK,
                    ),
                ),
                SettingsRootGroupRegistration(
                    stringResource(Res.string.settings_playback),
                    listOf(SettingsPageId.PLAYER, SettingsPageId.PLAYER_QUEUE, SettingsPageId.LYRICS),
                ),
                SettingsRootGroupRegistration(
                    stringResource(Res.string.settings_content_title),
                    listOf(SettingsPageId.CONTENT, SettingsPageId.CONTENT_HOME),
                ),
            ),
        )
}

private const val VIDEO_FILTER_ALL = "all"
private const val VIDEO_FILTER_MUSIC = "music"
private const val VIDEO_FILTER_NONE = "none"

/**
 * Upstream's two video switches overlap: non-musical videos are a subset of video songs. One choice replaces them and
 * drives both switches, so their side effects (refiltering content) stay upstream's.
 */
private fun SettingsUiRegistry.withVideoFilterChoice(
    title: String,
    description: String,
    labels: List<Pair<String, String>>,
): SettingsUiRegistry {
    val items = pages.flatMap { page -> page.groups.flatMap { it.items } }
    val videoSongs = items.firstOrNull { it.id == "hideVideoSongs" } ?: return this
    val nonMusical = items.firstOrNull { it.id == "hideNonMusicalVideos" } ?: return this
    val selected =
        when {
            videoSongs.checked == true -> VIDEO_FILTER_NONE
            nonMusical.checked == true -> VIDEO_FILTER_MUSIC
            else -> VIDEO_FILTER_ALL
        }
    val choice =
        SettingsItemRegistration(
            id = "peekVideoFilter",
            title = title,
            description = description,
            control = SettingsControl.CHOICE,
            valueLabel = labels.first { it.first == selected }.second,
            choices = labels.map { (id, label) -> SettingsChoice(id, label) },
            selectedChoiceId = selected,
            onChoiceSelected = { id ->
                if (videoSongs.checked != (id == VIDEO_FILTER_NONE)) videoSongs.onCheckedChange?.invoke(id == VIDEO_FILTER_NONE)
                if (nonMusical.checked != (id == VIDEO_FILTER_MUSIC)) nonMusical.onCheckedChange?.invoke(id == VIDEO_FILTER_MUSIC)
            },
        )
    return mapGroups { group ->
        if (group.items.none { it.id == videoSongs.id || it.id == nonMusical.id }) {
            group
        } else {
            group.copy(
                items =
                    group.items.mapNotNull { item ->
                        when (item.id) {
                            videoSongs.id -> choice
                            nonMusical.id -> null
                            else -> item
                        }
                    },
            )
        }
    }
}

/** Drops hidden items. A group left empty by that disappears; groups that were empty before stay. */
private fun SettingsUiRegistry.withoutItems(ids: Set<String>): SettingsUiRegistry =
    mapGroups { group ->
        val items = group.items.filterNot { it.id in ids }
        when {
            items.size == group.items.size -> group
            items.isEmpty() -> null
            else -> group.copy(items = items)
        }
    }

/** Removes the links to pages that become top-level pages of their own. */
private fun SettingsUiRegistry.withoutNavigationTo(vararg pages: SettingsPageId): SettingsUiRegistry =
    mapGroups { group ->
        val items = group.items.filterNot { it.destination in pages && it.id.startsWith("subpage:") }
        when {
            items.size == group.items.size -> group
            items.isEmpty() -> null
            else -> group.copy(items = items)
        }
    }

private fun SettingsUiRegistry.moveGroup(
    from: SettingsPageId,
    groupId: String,
    to: SettingsPageId,
    newGroupId: String,
    title: String,
): SettingsUiRegistry {
    val group = get(from)?.groups?.firstOrNull { it.id == groupId } ?: return this
    return moveItems(from, group.items.mapTo(mutableSetOf()) { it.id }, to, newGroupId, title)
}

/** Moves items between pages into a group appended to the target page, titled after their old group unless [title] is set. */
private fun SettingsUiRegistry.moveItems(
    from: SettingsPageId,
    itemIds: Set<String>,
    to: SettingsPageId,
    newGroupId: String,
    title: String?,
): SettingsUiRegistry {
    val source = get(from) ?: return this
    if (get(to) == null) return this
    val moved = source.groups.flatMap { group -> group.items.filter { it.id in itemIds } }
    if (moved.isEmpty()) return this
    val sourceTitle = source.groups.first { group -> group.items.any { it.id in itemIds } }.title
    val remaining =
        source.groups.mapNotNull { group ->
            val items = group.items.filterNot { it.id in itemIds }
            when {
                items.size == group.items.size -> group
                items.isEmpty() -> null
                else -> group.copy(items = items)
            }
        }
    val target = SettingsGroupRegistration(id = newGroupId, title = title ?: sourceTitle, items = moved)
    return copy(
        pages =
            pages.map { page ->
                when (page.id) {
                    from -> page.copy(groups = remaining)
                    to -> page.copy(groups = page.groups + target)
                    else -> page
                }
            },
    )
}

private fun SettingsUiRegistry.retitlePage(
    id: SettingsPageId,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
): SettingsUiRegistry =
    copy(
        pages =
            pages.map { page ->
                if (page.id != id) {
                    page
                } else {
                    page.copy(title = title ?: page.title, subtitle = subtitle ?: page.subtitle, icon = icon ?: page.icon)
                }
            },
    )

private fun SettingsUiRegistry.retitleItem(
    id: String,
    title: String,
    description: String? = null,
): SettingsUiRegistry =
    mapItems { item -> if (item.id == id) item.copy(title = title, description = description ?: item.description) else item }

/** PeekMusic's first root groups, followed by upstream's remaining ones (privacy, storage, app and about). */
private fun SettingsUiRegistry.withRootGroups(leading: List<SettingsRootGroupRegistration>): SettingsUiRegistry {
    val claimed = leading.flatMap { it.pages }.toSet()
    val groups =
        leading.map { group -> group.copy(pages = group.pages.filter { get(it) != null }) } +
            rootGroups.map { group -> group.copy(pages = group.pages.filterNot { it in claimed }) }
    return copy(rootGroups = groups.filter { it.pages.isNotEmpty() })
}

private fun SettingsUiRegistry.mapGroups(transform: (SettingsGroupRegistration) -> SettingsGroupRegistration?): SettingsUiRegistry =
    copy(pages = pages.map { page -> page.copy(groups = page.groups.mapNotNull(transform)) })

private fun SettingsUiRegistry.mapItems(transform: (SettingsItemRegistration) -> SettingsItemRegistration): SettingsUiRegistry =
    mapGroups { group -> group.copy(items = group.items.map(transform)) }
