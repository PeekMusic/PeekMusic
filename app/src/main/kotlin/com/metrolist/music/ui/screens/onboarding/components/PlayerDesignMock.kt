/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R

import com.metrolist.music.ui.screens.onboarding.components.FakePlayerLyricsLine

/**
 * A comprehensive mockup of the full player layout.
 * It switches between the new and legacy designs and shows/hides peek lyrics based on the toggles.
 */
@Composable
fun PlayerDesignMock(
    useNewDesign: Boolean,
    showPeekLyrics: Boolean,
    showRomanization: Boolean = false,
    showTranslation: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Cover placeholder
            Box(
                modifier =
                    Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (showPeekLyrics) {
                val subline = buildString {
                    if (showRomanization) append("naneun dalla dalla dalla")
                    if (showRomanization && showTranslation) append(" • ")
                    if (showTranslation) append("I am different different different")
                }.takeIf { it.isNotEmpty() }

                FakePlayerLyricsLine(
                    line = "나는 달라 달라 달라",
                    subLine = subline,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Title, Artist and action buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIFFERENT",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "LE SSERAFIM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (useNewDesign) {
                        FilledIconButton(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                            Icon(painter = painterResource(R.drawable.share), contentDescription = null)
                        }
                        FilledIconButton(onClick = {}, shape = RoundedCornerShape(12.dp)) {
                            Icon(painter = painterResource(R.drawable.favorite), contentDescription = null)
                        }
                    } else {
                        IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                            Icon(painter = painterResource(R.drawable.share), contentDescription = null)
                        }
                        IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                            Icon(painter = painterResource(R.drawable.more_horiz), contentDescription = null)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0:57", style = MaterialTheme.typography.labelSmall)
                Text("2:21", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Full player controls
            if (useNewDesign) {
                NewPlayerControls()
            } else {
                LegacyPlayerControls()
            }
        }
    }
}

@Composable
private fun NewPlayerControls() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            FilledIconButton(
                onClick = {},
                modifier = Modifier
                    .height(64.dp)
                    .width(140.dp),
                shape = RoundedCornerShape(32.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Bottom row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))) {
                    Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))) {
                    Icon(painter = painterResource(R.drawable.bedtime), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))) {
                    Icon(painter = painterResource(R.drawable.lyrics), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))) {
                    Icon(painter = painterResource(R.drawable.repeat), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Icon(painter = painterResource(R.drawable.more_horiz), contentDescription = null)
            }
        }
    }
}

@Composable
private fun LegacyPlayerControls() {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {}) {
                Icon(painter = painterResource(R.drawable.repeat), contentDescription = null)
            }
            IconButton(onClick = {}) {
                Icon(painter = painterResource(R.drawable.skip_previous), contentDescription = null)
            }
            FilledIconButton(
                onClick = {},
                modifier = Modifier.size(72.dp),
                shape = CircleShape
            ) {
                Icon(painter = painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = {}) {
                Icon(painter = painterResource(R.drawable.skip_next), contentDescription = null)
            }
            IconButton(onClick = {}) {
                Icon(painter = painterResource(R.drawable.favorite), contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Bottom row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.queue_music), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Queue", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.bedtime), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sleep timer", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(R.drawable.lyrics), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Lyrics", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}


