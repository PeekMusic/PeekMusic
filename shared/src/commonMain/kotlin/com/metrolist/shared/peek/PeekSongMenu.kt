package com.metrolist.shared.peek

import com.metrolist.shared.ui.components.PlayerMenuAction

/** Song menus have no play button: tapping the song plays it. The player menu is not affected. */
const val PEEK_SONG_MENU_PLAY = false

/** No "refetch metadata" in song menus; a repair tool that almost always finds nothing newer. */
const val PEEK_SONG_MENU_REFETCH = false

/**
 * PeekMusic's song menu, in the same order as the player menu: queue, playlist moves, artist and album,
 * then download, dislike, block and details, and the rarely used extras last. Subtitles that only repeat
 * the title ("Add to the top of your queue") are dropped; artist and album keep theirs, which name the
 * target. The song editor is gone from the menu.
 */
fun peekSongMenuActionGroups(
    queue: List<PlayerMenuAction>,
    removal: List<PlayerMenuAction>,
    navigation: List<PlayerMenuAction>,
    library: List<PlayerMenuAction>,
    extras: List<PlayerMenuAction>,
): List<List<PlayerMenuAction>> =
    listOf(
        queue.withoutSubtitles(),
        removal,
        navigation,
        library.withoutSubtitles(),
        extras.withoutSubtitles(),
    ).filter { it.isNotEmpty() }

private fun List<PlayerMenuAction>.withoutSubtitles() = map { it.copy(description = null) }
