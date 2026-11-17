/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** How long a press shows at least, so the M3 Expressive shape morph has time to play. */
const val PEEK_MIN_PRESS_MS = 150L

/**
 * An interaction source for buttons that should bounce like on a phone. A mouse click presses a button for only a few
 * milliseconds, far too short for Material 3 Expressive's pressed-shape morph (and ButtonGroup's width animation) to
 * show; a finger tap lasts long enough. This source holds every press for at least [minPressMs] before it lets the
 * release through. The click itself is not delayed, only the release of the pressed look.
 */
@Composable
fun rememberPeekPressSource(minPressMs: Long = PEEK_MIN_PRESS_MS): MutableInteractionSource =
    remember(minPressMs) { peekPressSource(minPressMs) }

/** Non-composable form of [rememberPeekPressSource], for code that builds several sources inside one remember. */
fun peekPressSource(minPressMs: Long = PEEK_MIN_PRESS_MS): MutableInteractionSource =
    PeekHeldPressSource(MutableInteractionSource(), minPressMs)

private class PeekHeldPressSource(
    private val delegate: MutableInteractionSource,
    private val minPressMs: Long,
) : MutableInteractionSource by delegate {
    private val pressedAt = mutableMapOf<PressInteraction.Press, TimeMark>()

    override suspend fun emit(interaction: Interaction) {
        when (interaction) {
            is PressInteraction.Press -> pressedAt[interaction] = TimeSource.Monotonic.markNow()
            is PressInteraction.Release -> holdUntilShown(interaction.press)
            is PressInteraction.Cancel -> holdUntilShown(interaction.press)
        }
        delegate.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        // Non-suspending callers can't wait; forget the press so the map does not grow.
        when (interaction) {
            is PressInteraction.Release -> pressedAt.remove(interaction.press)
            is PressInteraction.Cancel -> pressedAt.remove(interaction.press)
            is PressInteraction.Press -> pressedAt[interaction] = TimeSource.Monotonic.markNow()
        }
        return delegate.tryEmit(interaction)
    }

    private suspend fun holdUntilShown(press: PressInteraction.Press) {
        val start = pressedAt.remove(press) ?: return
        val remaining = minPressMs - start.elapsedNow().inWholeMilliseconds
        if (remaining > 0) delay(remaining)
    }
}
