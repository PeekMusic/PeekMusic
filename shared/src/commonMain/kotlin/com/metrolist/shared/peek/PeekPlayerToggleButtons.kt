/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
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
import com.metrolist.shared.generated.resources.canvas
import com.metrolist.shared.generated.resources.more_options
import com.metrolist.shared.generated.resources.peek_title
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.playerControlBlend
import com.metrolist.shared.ui.components.playerInactiveControlContainerColor
import com.metrolist.shared.ui.haptics.HapticEvent
import com.metrolist.shared.ui.haptics.rememberMetrolistHaptics
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.navigation.navigationPopupSource
import org.jetbrains.compose.resources.stringResource

private data class PeekButton(
    val icon: ImageVector,
    val label: String,
    val checked: Boolean,
    val onClick: () -> Unit,
)

/** Peek (opens the lyrics menu) and Canvas, the two buttons every player design gets. */
@Composable
private fun peekButtons(toggles: PeekPlayerToggles): List<PeekButton> =
    listOf(
        PeekButton(MetrolistIcons.Outlined.Lyrics, stringResource(Res.string.peek_title), toggles.peekOn, toggles::openLyricsMenu),
        PeekButton(
            PeekIcons.Animation,
            stringResource(Res.string.canvas),
            toggles.canvasOn,
            toggles::toggleCanvas,
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
    PeekConnectedButtonGroup(
        buttons = peekButtons(toggles) + likeButton(isLiked, onLikeClick),
        groupHeight = groupHeight,
        buttonWidth = buttonWidth,
        iconSize = iconSize,
        modifier = modifier,
    )
}

/**
 * Desktop full player: Canvas and like in place of upstream's like/share group, plus the ⋮ button.
 * Peek lives on the desktop's lyrics button, share stays in the ⋮ menu.
 */
@Composable
fun PeekCanvasLikeButtons(
    canvasOn: Boolean,
    onCanvasClick: () -> Unit,
    isLiked: Boolean,
    onLikeClick: () -> Unit,
    groupHeight: Dp,
    buttonWidth: Dp,
    iconSize: Dp,
    onMoreClick: () -> Unit,
    isMoreActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val darkTheme = Settings.shared.getCurrentThemeIsDark()
    val moreDesc = stringResource(Res.string.more_options)
    Row(modifier = modifier.height(groupHeight), verticalAlignment = Alignment.CenterVertically) {
        PeekConnectedButtonGroup(
            buttons =
                listOf(
                    PeekButton(PeekIcons.Animation, stringResource(Res.string.canvas), canvasOn, onCanvasClick),
                    likeButton(isLiked, onLikeClick),
                ),
            groupHeight = groupHeight,
            buttonWidth = buttonWidth,
            iconSize = iconSize,
            modifier = Modifier.playerControlBlend(),
        )
        Spacer(Modifier.width(8.dp))
        ToggleButton(
            checked = isMoreActive,
            onCheckedChange = { onMoreClick() },
            shapes = ToggleButtonDefaults.shapesFor(groupHeight),
            contentPadding = PaddingValues(0.dp),
            colors =
                ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (darkTheme) 0.5f else 1f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            modifier =
                Modifier
                    .size(buttonWidth, groupHeight)
                    .semantics { contentDescription = moreDesc }
                    .navigationPopupSource(),
        ) {
            Icon(MetrolistIcons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun likeButton(
    isLiked: Boolean,
    onLikeClick: () -> Unit,
) = PeekButton(
    if (isLiked) MetrolistIcons.Default.Favorite else MetrolistIcons.Outlined.FavoriteBorder,
    stringResource(Res.string.action_like),
    isLiked,
    onLikeClick,
)

/** Connected toggle buttons: pressed buttons widen and shapes morph smoothly, like upstream's like/share group. */
@Composable
private fun PeekConnectedButtonGroup(
    buttons: List<PeekButton>,
    groupHeight: Dp,
    buttonWidth: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMetrolistHaptics()
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = playerInactiveControlContainerColor(),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    val interactionSources = remember(buttons.size) { List(buttons.size) { peekPressSource() } }
    val shapes: List<ToggleButtonShapes> =
        buttons.indices.map { index ->
            when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                buttons.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
        }
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
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        peekButtons(toggles).forEach { button ->
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
