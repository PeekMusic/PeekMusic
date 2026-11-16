/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared.peek

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.action_like
import com.metrolist.shared.generated.resources.app_name
import com.metrolist.shared.generated.resources.peek_onboarding_artwork_description
import com.metrolist.shared.generated.resources.peek_onboarding_artwork_title
import com.metrolist.shared.generated.resources.peek_onboarding_content_description
import com.metrolist.shared.generated.resources.peek_onboarding_content_title
import com.metrolist.shared.generated.resources.peek_onboarding_language_description
import com.metrolist.shared.generated.resources.peek_onboarding_language_title
import com.metrolist.shared.generated.resources.peek_onboarding_look_description
import com.metrolist.shared.generated.resources.peek_onboarding_look_title
import com.metrolist.shared.generated.resources.peek_onboarding_lyrics_description
import com.metrolist.shared.generated.resources.peek_onboarding_lyrics_title
import com.metrolist.shared.generated.resources.peek_onboarding_new
import com.metrolist.shared.generated.resources.peek_onboarding_player_description
import com.metrolist.shared.generated.resources.peek_onboarding_player_title
import com.metrolist.shared.generated.resources.peek_onboarding_sound_description
import com.metrolist.shared.generated.resources.peek_onboarding_sound_title
import com.metrolist.shared.generated.resources.repeat
import com.metrolist.shared.generated.resources.unlike
import com.metrolist.shared.settings.PlayerBackgroundStyle
import com.metrolist.shared.settings.PlayerType
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.components.PlayerPrimaryControls
import com.metrolist.shared.ui.components.VintageClassicControls
import com.metrolist.shared.ui.components.VintageClassicControlsText
import com.metrolist.shared.ui.components.VintagePlayerControls
import com.metrolist.shared.ui.components.vintageOnButtonColor
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.onboarding.OnboardingPage
import com.metrolist.shared.ui.player.vintagePlayerControlsText
import com.metrolist.shared.ui.settings.SettingsGroupRegistration
import com.metrolist.shared.ui.settings.SettingsItemGroup
import com.metrolist.shared.ui.settings.SettingsItemRegistration
import com.metrolist.shared.ui.settings.SharedSettingsHost
import com.metrolist.shared.ui.settings.SliderStylePreview
import com.metrolist.shared.ui.settings.rememberMetrolistSettingsRegistry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A setting shown on a PeekMusic onboarding page. [isNew] marks what PeekMusic 1.x did not have; people coming from 1.x see a badge. */
data class PeekOnboardingItem(
    val id: String,
    val isNew: Boolean = false,
)

/**
 * PeekMusic's feature tour in onboarding: one compact page per topic with the real settings from the registry, so new
 * users don't have to dig through Settings to find what PeekMusic can do. Decided in the onboarding review of 2026-11-15.
 */
object PeekOnboarding {
    val featurePages =
        listOf(
            OnboardingPage.PEEK_LANGUAGE,
            OnboardingPage.PEEK_LOOK,
            OnboardingPage.PEEK_PLAYER,
            OnboardingPage.PEEK_LYRICS,
            OnboardingPage.PEEK_ARTWORK,
            OnboardingPage.PEEK_SOUND,
            OnboardingPage.PEEK_CONTENT,
        )

    fun items(page: OnboardingPage): List<PeekOnboardingItem> =
        when (page) {
            OnboardingPage.PEEK_LANGUAGE -> {
                listOf(PeekOnboardingItem("appLanguage"), PeekOnboardingItem("contentLanguage"), PeekOnboardingItem("contentCountry"))
            }

            OnboardingPage.PEEK_LOOK -> {
                listOf(
                    PeekOnboardingItem("themeMode"),
                    PeekOnboardingItem("materialYou"),
                    PeekOnboardingItem("themeColor"),
                    PeekOnboardingItem("dynamicTheme"),
                    PeekOnboardingItem("paletteStyle", isNew = true),
                )
            }

            OnboardingPage.PEEK_PLAYER -> {
                listOf(
                    PeekOnboardingItem("showLyricsOnPlayer"),
                    PeekOnboardingItem("playerType", isNew = true),
                    PeekOnboardingItem("playerBackground"),
                    PeekOnboardingItem("sliderStyle"),
                    PeekOnboardingItem("miniPlayerType", isNew = true),
                )
            }

            OnboardingPage.PEEK_LYRICS -> {
                listOf(
                    PeekOnboardingItem("translationEnabled"),
                    PeekOnboardingItem("targetLanguage"),
                    PeekOnboardingItem("showSecondaryLyrics"),
                    PeekOnboardingItem("lyricsProgressiveBlur", isNew = true),
                )
            }

            OnboardingPage.PEEK_ARTWORK -> {
                listOf(
                    PeekOnboardingItem("playerCanvasEnabled"),
                    PeekOnboardingItem("animatedCovers", isNew = true),
                    PeekOnboardingItem("appleMusicArtistVideos", isNew = true),
                    PeekOnboardingItem("musicVideoPlaybackEnabled"),
                    PeekOnboardingItem(PeekQuality.ITEM_ID),
                    PeekOnboardingItem("disableAnimatedArtworkOnMobileData", isNew = true),
                )
            }

            OnboardingPage.PEEK_SOUND -> {
                listOf(
                    PeekOnboardingItem("audioQuality"),
                    PeekOnboardingItem("audioNormalization"),
                    PeekOnboardingItem("crossfadeEnabled"),
                    PeekOnboardingItem("crossfadeDuration"),
                )
            }

            OnboardingPage.PEEK_CONTENT -> {
                listOf(
                    PeekOnboardingItem("hideExplicit"),
                    PeekOnboardingItem("blockAiArtists", isNew = true),
                    PeekOnboardingItem("hidePodcasts"),
                )
            }

            else -> {
                emptyList()
            }
        }
}

private fun OnboardingPage.peekTitleRes(): StringResource? =
    when (this) {
        OnboardingPage.PEEK_LANGUAGE -> Res.string.peek_onboarding_language_title
        OnboardingPage.PEEK_LOOK -> Res.string.peek_onboarding_look_title
        OnboardingPage.PEEK_PLAYER -> Res.string.peek_onboarding_player_title
        OnboardingPage.PEEK_LYRICS -> Res.string.peek_onboarding_lyrics_title
        OnboardingPage.PEEK_ARTWORK -> Res.string.peek_onboarding_artwork_title
        OnboardingPage.PEEK_SOUND -> Res.string.peek_onboarding_sound_title
        OnboardingPage.PEEK_CONTENT -> Res.string.peek_onboarding_content_title
        else -> null
    }

private fun OnboardingPage.peekDescriptionRes(): StringResource? =
    when (this) {
        OnboardingPage.PEEK_LANGUAGE -> Res.string.peek_onboarding_language_description
        OnboardingPage.PEEK_LOOK -> Res.string.peek_onboarding_look_description
        OnboardingPage.PEEK_PLAYER -> Res.string.peek_onboarding_player_description
        OnboardingPage.PEEK_LYRICS -> Res.string.peek_onboarding_lyrics_description
        OnboardingPage.PEEK_ARTWORK -> Res.string.peek_onboarding_artwork_description
        OnboardingPage.PEEK_SOUND -> Res.string.peek_onboarding_sound_description
        OnboardingPage.PEEK_CONTENT -> Res.string.peek_onboarding_content_description
        else -> null
    }

@Composable
fun OnboardingPage.peekTitle(): String = stringResource(checkNotNull(peekTitleRes()))

@Composable
fun OnboardingPage.peekDescription(): String = stringResource(checkNotNull(peekDescriptionRes()))

fun OnboardingPage.peekIcon(): ImageVector =
    when (this) {
        OnboardingPage.PEEK_LANGUAGE -> MetrolistIcons.Outlined.Language
        OnboardingPage.PEEK_LOOK -> MetrolistIcons.Outlined.Palette
        OnboardingPage.PEEK_PLAYER -> MetrolistIcons.Outlined.SmartDisplay
        OnboardingPage.PEEK_LYRICS -> MetrolistIcons.Outlined.Lyrics
        OnboardingPage.PEEK_ARTWORK -> PeekIcons.Animation
        OnboardingPage.PEEK_SOUND -> MetrolistIcons.Outlined.Headphones
        else -> MetrolistIcons.Outlined.Block
    }

/** Body of a PeekMusic feature page: a small preview where it helps, then the page's settings as one group. */
@Composable
fun PeekOnboardingFeature(
    page: OnboardingPage,
    host: SharedSettingsHost,
    showNewBadges: Boolean,
    artworkGradient: List<Color> = emptyList(),
    playerPreviewTheme: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    artworkPreview: @Composable () -> Unit = {},
) {
    var settingsRevision by remember { mutableIntStateOf(0) }
    val registry = rememberMetrolistSettingsRegistry(host, settingsRevision)
    val available = LinkedHashMap<String, SettingsItemRegistration>()
    registry.pages.forEach { registered ->
        registered.groups.forEach { group ->
            group.items.forEach { item -> available.getOrPut(item.id) { item.copy(enabled = item.enabled && group.enabled) } }
        }
    }
    available[PeekQuality.ITEM_ID] = peekHighQualityItem()
    // Translating and showing translations are two settings; in onboarding one switch turns both on, like the player's.
    available["translationEnabled"]?.let { item ->
        val settings = Settings.shared
        available["translationEnabled"] =
            item.copy(
                checked = settings.lyricsAiTranslationEnabled && settings.lyricsShowTranslations,
                onCheckedChange = { on ->
                    settings.lyricsAiTranslationEnabled = on
                    settings.lyricsShowTranslations = on
                    settingsRevision++
                },
            )
    }
    val newLabel = stringResource(Res.string.peek_onboarding_new)
    val accent = MaterialTheme.colorScheme.primary
    val items =
        PeekOnboarding.items(page).mapNotNull { wanted ->
            available[wanted.id]?.let { if (showNewBadges && wanted.isNew) it.withNewBadge(newLabel, accent) else it }
        }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (page) {
            OnboardingPage.PEEK_LOOK -> {
                ThemePreview()
            }

            OnboardingPage.PEEK_PLAYER -> {
                playerPreviewTheme { PeekPlayerPreview(artworkGradient) }
            }

            OnboardingPage.PEEK_ARTWORK -> {
                artworkPreview()
            }

            OnboardingPage.PEEK_LYRICS -> {
                // The preview is a lazy list, so it needs a bounded height inside the scrolling page.
                PeekSampleLyricsPreview(Modifier.fillMaxWidth())
            }

            else -> {}
        }
        if (items.isNotEmpty()) {
            SettingsItemGroup(
                group = SettingsGroupRegistration(id = "peekOnboarding", title = "", items = items),
                highlightedItemId = null,
                onSettingValueChanged = { settingsRevision++ },
                onItemClick = {},
                horizontalPadding = 0.dp,
                showHeading = false,
            )
        }
    }
}

private fun SettingsItemRegistration.withNewBadge(
    label: String,
    color: Color,
): SettingsItemRegistration {
    val rest = annotatedDescription ?: description?.let(::AnnotatedString)
    return copy(
        annotatedDescription =
            buildAnnotatedString {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) { append(label) }
                if (rest != null) {
                    append(" · ")
                    append(rest)
                }
            },
    )
}

/** A miniature home screen with a mini player, drawn from the live color scheme so theme changes show right away. */
@Composable
private fun ThemePreview() {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(colors.primary, colors.secondary, colors.tertiary, colors.primaryContainer, colors.secondaryContainer).forEach {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(it))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(3) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (index == 0) colors.tertiaryContainer else colors.surfaceContainerHighest),
                    )
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = colors.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            MetrolistIcons.Outlined.Lyrics,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(colors.onSecondaryContainer),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    0.4f,
                                ).height(8.dp)
                                .clip(CircleShape)
                                .background(colors.onSecondaryContainer.copy(alpha = 0.5f)),
                        )
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(colors.primary))
                }
            }
        }
    }
}
