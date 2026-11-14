/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.lyrics_romanize_title
import com.metrolist.shared.generated.resources.lyrics_show_translations
import com.metrolist.shared.generated.resources.peek_artwork_motion
import com.metrolist.shared.generated.resources.peek_title
import com.metrolist.shared.ui.components.ExpressiveSwitch
import com.metrolist.shared.ui.components.Material3MenuGroup
import com.metrolist.shared.ui.components.Material3MenuItemData
import com.metrolist.shared.ui.components.ModalBottomSheet
import com.metrolist.shared.ui.components.playerInactiveControlContainerColor
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.menu.ExpressiveMediaMenuHeader
import com.metrolist.shared.ui.theme.ComponentShapes
import com.metrolist.shared.ui.utils.verticalScrollWithDrag
import org.jetbrains.compose.resources.stringResource

private data class PeekButton(
    val icon: ImageVector,
    val label: String,
    val checked: Boolean,
    val onClick: () -> Unit,
)

/** Peek (opens the Peek sheet) and moving artwork, the two buttons every player design gets. */
@Composable
private fun peekButtons(
    toggles: PeekPlayerToggles,
    onOpenPeekSheet: () -> Unit,
): List<PeekButton> =
    listOf(
        PeekButton(MetrolistIcons.Outlined.Lyrics, stringResource(Res.string.peek_title), toggles.peekOn, onOpenPeekSheet),
        PeekButton(
            PeekIcons.Animation,
            stringResource(Res.string.peek_artwork_motion),
            toggles.artworkMotionOn,
            toggles::toggleArtworkMotion,
        ),
    )

/** Modern and Vintage players: Peek and moving artwork, connected with the like button. */
@Composable
internal fun PeekActionButtonGroup(
    toggles: PeekPlayerToggles,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    groupHeight: Dp,
    buttonWidth: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMetrolistHaptics()
    var showPeekSheet by remember { mutableStateOf(false) }
    if (showPeekSheet) PeekSheet(toggles, onDismiss = { showPeekSheet = false })
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = playerInactiveControlContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    val buttons =
        peekButtons(toggles) { showPeekSheet = true } +
            PeekButton(
                if (isLiked) MetrolistIcons.Default.Favorite else MetrolistIcons.Outlined.FavoriteBorder,
                stringResource(Res.string.action_like),
                isLiked,
                onLikeClick,
            )
    val interactionSources = remember { List(buttons.size) { MutableInteractionSource() } }
    val shapes: List<ToggleButtonShapes> =
        buttons.indices.map { index ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
    // A real ButtonGroup, like upstream's like/share group: pressed buttons widen and shapes morph smoothly.
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        expandedRatio = 1f,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(groupHeight),
    ) {
        buttons.forEachIndexed { index, button ->
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = button.checked,
                        onCheckedChange = {
                            haptics.perform(HapticEvent.PRESS)
                            button.onClick()
                        },
                        shapes = shapes[index],
                        contentPadding = PaddingValues(0.dp),
                        colors = colors,
                        interactionSource = interactionSources[index],
                        modifier =
                            Modifier
                                .size(width = buttonWidth, height = groupHeight)
                                .animateWidth(interactionSources[index], 12.dp)
                                .semantics { contentDescription = button.label },
                    ) {
                        Icon(button.icon, contentDescription = null, modifier = Modifier.size(iconSize))
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        leadingIcon = { Icon(button.icon, contentDescription = null) },
                        text = { Text(button.label) },
                        onClick = {
                            button.onClick()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

/** Classic player: Peek and moving artwork as round buttons, in place of upstream's share button. */
@Composable
internal fun PeekClassicToggleButtons(
    toggles: PeekPlayerToggles,
    buttonColor: Color,
    onButtonColor: Color,
) {
    val haptics = rememberMetrolistHaptics()
    var showPeekSheet by remember { mutableStateOf(false) }
    if (showPeekSheet) PeekSheet(toggles, onDismiss = { showPeekSheet = false })
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        peekButtons(toggles) { showPeekSheet = true }.forEach { button ->
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (button.checked) buttonColor else buttonColor.copy(alpha = 0.35f))
                        .semantics {
                            contentDescription = button.label
                            selected = button.checked
                        }.clickable(role = Role.Button) {
                            haptics.perform(HapticEvent.PRESS)
                            button.onClick()
                        },
            ) {
                Icon(
                    button.icon,
                    contentDescription = null,
                    tint = onButtonColor,
                    modifier = Modifier.size(22.dp).alpha(if (button.checked) 1f else 0.7f),
                )
            }
        }
    }
}

/** The Peek sheet, styled like upstream's lyrics menu: the Peek itself, translation and romanization. */
@Composable
private fun PeekSheet(
    toggles: PeekPlayerToggles,
    onDismiss: () -> Unit,
) {
    val uiState by toggles.viewModel.uiState.collectAsState()
    val song = uiState.currentSong
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = ComponentShapes.BottomSheetShape,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScrollWithDrag(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (song != null) {
                ExpressiveMediaMenuHeader(
                    title = song.title,
                    subtitle = song.artists.joinToString { it.name },
                    thumbnail = song.displayThumbnail,
                )
            }
            Material3MenuGroup(
                items =
                    listOf(
                        peekSwitchItem(
                            MetrolistIcons.Outlined.Lyrics,
                            stringResource(Res.string.peek_title),
                            toggles.peekOn,
                            toggles::togglePeek,
                        ),
                        peekSwitchItem(
                            MetrolistIcons.Outlined.Translate,
                            stringResource(Res.string.lyrics_show_translations),
                            toggles.translationOn,
                            toggles::toggleTranslation,
                        ),
                        peekSwitchItem(
                            PeekIcons.Abc,
                            stringResource(Res.string.lyrics_romanize_title),
                            toggles.romanizationOn,
                            toggles::toggleRomanization,
                        ),
                    ),
            )
        }
    }
}

private fun peekSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
): Material3MenuItemData =
    Material3MenuItemData(
        title = { Text(title) },
        icon = { Icon(icon, contentDescription = null) },
        onClick = onToggle,
        trailingContent = { ExpressiveSwitch(checked = checked, onCheckedChange = { onToggle() }) },
    )
