package com.metrolist.shared.peek

import com.metrolist.shared.ui.components.PlayerMenuAction
import com.metrolist.shared.ui.icons.MetrolistIcons
import kotlin.test.Test
import kotlin.test.assertEquals

class PeekSongMenuTest {
    private fun item(
        title: String,
        description: String? = null,
    ) = PlayerMenuAction(MetrolistIcons.Outlined.Info, title, description) {}

    @Test
    fun followsThePlayerMenuOrderAndSkipsEmptyGroups() {
        val groups =
            peekSongMenuActionGroups(
                queue = listOf(item("next"), item("queue")),
                removal = emptyList(),
                navigation = listOf(item("artist"), item("album")),
                library = listOf(item("download"), item("dislike"), item("block"), item("details")),
                extras = listOf(item("pin")),
            )
        assertEquals(
            listOf(listOf("next", "queue"), listOf("artist", "album"), listOf("download", "dislike", "block", "details"), listOf("pin")),
            groups.map { group -> group.map { it.title } },
        )
    }

    @Test
    fun keepsOnlyTheSubtitlesThatNameATarget() {
        val groups =
            peekSongMenuActionGroups(
                queue = listOf(item("next", "Add to the top of your queue")),
                removal = emptyList(),
                navigation = listOf(item("artist", "KAI")),
                library = listOf(item("download", "Make available for offline playback")),
                extras = emptyList(),
            )
        assertEquals(listOf(null, "KAI", null), groups.flatten().map { it.description })
    }
}
