/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.onboarding

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import com.metrolist.android.extensions.findActivity
import com.metrolist.android.locale.AppLocale
import com.metrolist.android.locale.rememberAppLanguageChoices
import com.metrolist.android.ui.theme.MetrolistTheme
import com.metrolist.android.ui.theme.PlayerColorExtractor
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_language_system
import com.metrolist.shared.generated.resources.device_language
import com.metrolist.shared.peek.PeekSampleSong
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.state.GlobalYouTube
import com.metrolist.shared.state.contentCountryCodes
import com.metrolist.shared.ui.settings.SettingsChoice
import com.metrolist.shared.ui.settings.SettingsHostChoice
import com.metrolist.shared.ui.settings.SettingsPageId
import com.metrolist.shared.ui.settings.SettingsPlatform
import com.metrolist.shared.ui.settings.SharedSettingsHost
import com.metrolist.shared.ui.theme.DefaultThemeColor
import com.metrolist.shared.ui.theme.resolveThemeSeedColor
import com.metrolist.shared.viewmodels.PlayerArtworkColors
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.util.Locale

/** Settings host for PeekMusic's onboarding pages: the same pages and choices as Android's Settings screen, without navigation. */
@Composable
internal fun rememberPeekOnboardingHost(): SharedSettingsHost {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemLocale = remember(configuration) { AppLocale.systemLocale(context) }
    val systemDefaultLabel = stringResource(Res.string.app_language_system)
    val languages = rememberAppLanguageChoices()
    val countries =
        remember(systemLocale, systemDefaultLabel) {
            listOf(SettingsChoice(GlobalYouTube.SYSTEM, systemDefaultLabel)) +
                contentCountryCodes
                    .asSequence()
                    .map { code ->
                        SettingsChoice(
                            code,
                            Locale
                                .Builder()
                                .setRegion(code)
                                .build()
                                .getDisplayCountry(systemLocale)
                                .ifBlank { code },
                        )
                    }.sortedBy(SettingsChoice::label)
                    .toList()
        }
    val deviceLanguageLabel = stringResource(Res.string.device_language)
    val lyricsTargetLanguages =
        remember(languages, deviceLanguageLabel) { listOf(SettingsChoice("device", deviceLanguageLabel)) + languages.drop(1) }
    return SharedSettingsHost(
        platform = SettingsPlatform.ANDROID,
        onClose = {},
        availablePages =
            setOf(
                SettingsPageId.PLAYER_STYLE,
                SettingsPageId.PLAYER_ARTWORK,
                SettingsPageId.MINI_PLAYER_STYLE,
                SettingsPageId.LYRICS_SOURCES,
                SettingsPageId.LYRICS_AI_TRANSLATION,
                SettingsPageId.LYRICS_ROMANIZATION,
            ),
        hostChoices =
            mapOf(
                SettingsHostChoice.APP_LANGUAGE to languages,
                SettingsHostChoice.CONTENT_LANGUAGE to languages,
                SettingsHostChoice.CONTENT_COUNTRY to countries,
                SettingsHostChoice.LYRICS_TARGET_LANGUAGE to lyricsTargetLanguages,
            ),
        onHostChoiceSelected = { choice, selected ->
            when (choice) {
                SettingsHostChoice.APP_LANGUAGE -> {
                    Settings.shared.appLanguage = selected
                    AppLocale.applySelection(context, selected)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) context.findActivity()?.recreate()
                }

                SettingsHostChoice.CONTENT_LANGUAGE -> {
                    Settings.shared.contentLanguage = selected
                }

                SettingsHostChoice.CONTENT_COUNTRY -> {
                    Settings.shared.contentCountry = selected
                }

                SettingsHostChoice.LYRICS_TARGET_LANGUAGE -> {
                    Settings.shared.lyricsAiTargetLanguage = selected
                }

                else -> {}
            }
        },
    )
}

/** The app theme for onboarding, built from the same settings as the main app, so theme changes show while choosing them. */
@Composable
internal fun PeekOnboardingTheme(content: @Composable () -> Unit) {
    val settings = Settings.shared
    settings.systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = settings.getCurrentThemeIsDark()
    MetrolistTheme(
        darkTheme = darkTheme,
        dynamicColor = settings.materialYouEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        themeColor =
            resolveThemeSeedColor(
                materialYouEnabled = settings.materialYouEnabled,
                songArtworkColorsEnabled = false,
                selectedThemeColor = settings.selectedThemeColor,
                artworkSeedColor = null,
            ),
        pureBlack = settings.isPureBlackTheme(darkTheme),
        content = content,
    )
}

/** The example cover's colors for the player preview, from the same extractor the player uses. */
@Composable
internal fun rememberPeekSampleArtworkColors(): PlayerArtworkColors? {
    val context = LocalContext.current
    val imageLoader: ImageLoader = koinInject()
    val colors by produceState<PlayerArtworkColors?>(null, imageLoader) {
        value =
            try {
                PlayerColorExtractor.extractColors(context, imageLoader, PeekSampleSong.ARTWORK_URL, DefaultThemeColor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
    }
    return colors
}

/** Like the real player: with "colors from the cover" on, the preview takes its theme from the example cover. */
@Composable
internal fun PeekSampleArtworkTheme(
    colors: PlayerArtworkColors?,
    content: @Composable () -> Unit,
) {
    val settings = Settings.shared
    val dominant = colors?.dominant
    if (dominant == null || !settings.materialYouEnabled || !settings.dynamicTheme) {
        content()
        return
    }
    val darkTheme = settings.getCurrentThemeIsDark()
    MetrolistTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        themeColor = dominant,
        pureBlack = settings.isPureBlackTheme(darkTheme),
        content = content,
    )
}
