/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.logo
import org.jetbrains.compose.resources.imageResource

/**
 * PeekMusic's logo is a 512 px PNG. Drawn small with the default filter it looks pixelated on desktop,
 * so it is sampled with mipmaps (FilterQuality.Medium), which keeps it smooth at any size.
 */
@Composable
fun peekLogoPainter(): Painter {
    val image = imageResource(Res.drawable.logo)
    return remember(image) { BitmapPainter(image, filterQuality = FilterQuality.Medium) }
}
