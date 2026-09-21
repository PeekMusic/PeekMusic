/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.DEFAULT_HOME_SECTION_ORDER
import com.metrolist.music.constants.HiddenYouTubeHomeSectionsKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.RandomizeHomeOrderKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private fun categoryLabelRes(category: String): Int =
    when (category) {
        "speed_dial" -> R.string.category_speed_dial
        "quick_picks" -> R.string.category_quick_picks
        "daily_discover" -> R.string.category_daily_discover
        "keep_listening" -> R.string.category_keep_listening
        "forgotten_favorites" -> R.string.category_forgotten_favorites
        "similar_to" -> R.string.category_similar_to
        "recommended_mixes" -> R.string.category_recommended_mixes
        "new_releases" -> R.string.category_new_releases
        "community_playlists" -> R.string.category_community_playlists
        "moods_and_genres" -> R.string.category_moods_and_genres
        "other" -> R.string.category_other
        else -> 0
    }

/**
 * Home screen section settings: randomize toggle, draggable section order and hide/restore.
 * The order list is read-only when the user is not logged in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSectionsSettings(
    navController: NavController,
) {
    val haptic = LocalHapticFeedback.current
    val (hiddenCategories, onHiddenCategoriesChange) =
        rememberPreference(HiddenYouTubeHomeSectionsKey, emptySet<String>())

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) { innerTubeCookie.isNotBlank() }

    fun toggleHidden(category: String) {
        if (!isLoggedIn) return
        onHiddenCategoriesChange(
            if (category in hiddenCategories) hiddenCategories - category
            else hiddenCategories + category
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        Spacer(Modifier.height(16.dp))

        if (!isLoggedIn) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = {},
                        description = {
                            Text(stringResource(R.string.home_sections_settings_login_required))
                        },
                        onClick = null
                    )
                )
            )
            Spacer(Modifier.height(16.dp))
        }

        val hiddenCategoriesSorted = hiddenCategories.filter { it != "speed_dial" && it != "other" }.sorted()
        
        if (hiddenCategoriesSorted.isEmpty()) {
            Material3SettingsGroup(
                title = "Startseite anpassen",
                items = listOf(
                    Material3SettingsItem(
                        title = { Text("Papierkorb ist leer") },
                        description = { Text("Du hast aktuell keine Sektionen ausgeblendet.\n\nKlicke auf der Startseite bei einer beliebigen Sektion auf das 3-Punkte-Menü, um sie auszublenden. Sie taucht dann hier auf und kann jederzeit wiederhergestellt werden.") },
                        onClick = null
                    )
                )
            )
        } else {
            Material3SettingsGroup(
                title = "Ausgeblendete Sektionen",
                items = hiddenCategoriesSorted.map { category ->
                    Material3SettingsItem(
                        title = {
                            val res = categoryLabelRes(category)
                            Text(text = if (res != 0) stringResource(res) else category)
                        },
                        trailingContent = {
                            IconButton(onClick = { toggleHidden(category) }, onLongClick = {}) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Wiederherstellen",
                                )
                            }
                        },
                        onClick = {
                            toggleHidden(category)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    )
                },
            )
        }
    }
}