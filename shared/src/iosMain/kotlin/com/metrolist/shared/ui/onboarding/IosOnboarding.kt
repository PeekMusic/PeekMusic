@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.shared.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_language_system
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.continue_action
import com.metrolist.shared.generated.resources.device_language
import com.metrolist.shared.generated.resources.finish
import com.metrolist.shared.generated.resources.log_in
import com.metrolist.shared.generated.resources.no
import com.metrolist.shared.generated.resources.onboarding_begin_setup
import com.metrolist.shared.generated.resources.onboarding_crash_reports_description
import com.metrolist.shared.generated.resources.onboarding_crash_reports_title
import com.metrolist.shared.generated.resources.onboarding_finish_description
import com.metrolist.shared.generated.resources.onboarding_finish_title
import com.metrolist.shared.generated.resources.onboarding_welcome_privacy_description
import com.metrolist.shared.generated.resources.peek_onboarding_settings_hint
import com.metrolist.shared.generated.resources.restore_from_backup
import com.metrolist.shared.generated.resources.side_menu_welcome
import com.metrolist.shared.generated.resources.skip
import com.metrolist.shared.generated.resources.yes
import com.metrolist.shared.peek.PeekOnboarding
import com.metrolist.shared.peek.PeekOnboardingFeature
import com.metrolist.shared.peek.peekDescription
import com.metrolist.shared.peek.peekIcon
import com.metrolist.shared.peek.peekLogoPainter
import com.metrolist.shared.peek.peekTitle
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.state.GlobalYouTube
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.settings.SettingsChoice
import com.metrolist.shared.ui.settings.SettingsHostChoice
import com.metrolist.shared.ui.settings.SettingsPageId
import com.metrolist.shared.ui.settings.SettingsPlatform
import com.metrolist.shared.ui.settings.SharedSettingsHost
import com.metrolist.shared.ui.settings.applyIosAppLanguage
import com.metrolist.shared.utils.isCrashReportingAvailable
import org.jetbrains.compose.resources.stringResource

/**
 * PeekMusic: the iOS onboarding with the same PeekMusic feature pages as Android and desktop (language, look, player,
 * lyrics, artwork, sound, content). Settings iOS has no use for drop out on their own, because the feature pages only
 * show what the iOS settings registry offers.
 */
@Composable
internal fun IosOnboarding(
    contentLanguageChoices: List<SettingsChoice>,
    contentCountryChoices: List<SettingsChoice>,
    systemLanguage: String,
    systemCountry: String,
    onComplete: (sendCrashReports: Boolean, login: Boolean, restore: Boolean) -> Unit,
) {
    val pages =
        remember {
            buildList {
                add(OnboardingPage.WELCOME)
                if (isCrashReportingAvailable()) add(OnboardingPage.CRASH_REPORTS)
                addAll(PeekOnboarding.featurePages)
                add(OnboardingPage.FINISH)
            }
        }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    var consent by rememberSaveable { mutableStateOf(false) }
    // Changing the app language needs the strings to load again.
    var languageRevision by remember { mutableIntStateOf(0) }

    key(languageRevision) {
        val host =
            rememberIosOnboardingHost(contentLanguageChoices, contentCountryChoices, systemLanguage, systemCountry) {
                languageRevision++
            }

        fun nextPage() {
            if (pageIndex < pages.lastIndex) pageIndex++ else onComplete(consent, false, false)
        }

        @Composable
        fun pageContent(index: Int): OnboardingPageContent {
            val page = pages[index]
            val back: (() -> Unit)? = if (index > 0) ({ pageIndex = index - 1 }) else null
            if (page in PeekOnboarding.featurePages) {
                return OnboardingPageContent(
                    page = page,
                    title = page.peekTitle(),
                    description = page.peekDescription(),
                    icon = page.peekIcon(),
                    step = index + 1,
                    stepCount = pages.size,
                    primaryLabel = stringResource(Res.string.continue_action),
                    secondaryLabel = stringResource(Res.string.skip),
                    isWorking = false,
                    error = null,
                    backLabel = stringResource(Res.string.back),
                    onPrimary = ::nextPage,
                    // Skip jumps past the whole tour, like Android.
                    onSecondary = { pageIndex = pages.lastIndex },
                    onBack = back,
                )
            }
            return OnboardingPageContent(
                page = page,
                title =
                    stringResource(
                        when (page) {
                            OnboardingPage.WELCOME -> Res.string.side_menu_welcome
                            OnboardingPage.CRASH_REPORTS -> Res.string.onboarding_crash_reports_title
                            else -> Res.string.onboarding_finish_title
                        },
                    ),
                description =
                    stringResource(
                        when (page) {
                            OnboardingPage.WELCOME -> Res.string.onboarding_welcome_privacy_description
                            OnboardingPage.CRASH_REPORTS -> Res.string.onboarding_crash_reports_description
                            else -> Res.string.onboarding_finish_description
                        },
                    ),
                icon =
                    when (page) {
                        OnboardingPage.WELCOME -> MetrolistIcons.Outlined.MusicNote
                        OnboardingPage.CRASH_REPORTS -> MetrolistIcons.Outlined.Security
                        else -> MetrolistIcons.Filled.Check
                    },
                step = index + 1,
                stepCount = pages.size,
                primaryLabel =
                    stringResource(
                        when (page) {
                            OnboardingPage.WELCOME -> Res.string.onboarding_begin_setup
                            OnboardingPage.CRASH_REPORTS -> Res.string.yes
                            else -> Res.string.finish
                        },
                    ),
                secondaryLabel = if (page == OnboardingPage.CRASH_REPORTS) stringResource(Res.string.no) else null,
                isWorking = false,
                error = null,
                backLabel = stringResource(Res.string.back),
                onPrimary = {
                    if (page == OnboardingPage.CRASH_REPORTS) consent = true
                    nextPage()
                },
                onSecondary =
                    if (page == OnboardingPage.CRASH_REPORTS) {
                        {
                            consent = false
                            nextPage()
                        }
                    } else {
                        null
                    },
                onBack = back,
            )
        }
        val content = pageContent(pageIndex)
        val previousContent = if (pageIndex > 0) pageContent(pageIndex - 1) else null

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            OnboardingContent(
                content = content,
                previousContent = previousContent,
                welcomeContent = { modifier ->
                    Image(
                        painter = peekLogoPainter(),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        modifier = modifier,
                    )
                },
                featureContent = { targetPage ->
                    if (targetPage in PeekOnboarding.featurePages) {
                        PeekOnboardingFeature(targetPage, host, showNewBadges = false)
                    }
                    if (targetPage == OnboardingPage.FINISH) {
                        FinishActions(
                            onLogin = { onComplete(consent, true, false) },
                            onRestore = { onComplete(consent, false, true) },
                        )
                    }
                },
            )
        }
    }
}

/** Settings host for the onboarding pages: the iOS Settings' choices, without navigation. */
@Composable
private fun rememberIosOnboardingHost(
    contentLanguageChoices: List<SettingsChoice>,
    contentCountryChoices: List<SettingsChoice>,
    systemLanguage: String,
    systemCountry: String,
    onAppLanguageChanged: () -> Unit,
): SharedSettingsHost {
    val systemDefaultLabel = stringResource(Res.string.app_language_system)
    val deviceLanguageLabel = stringResource(Res.string.device_language)
    val languages = listOf(SettingsChoice(GlobalYouTube.SYSTEM, systemDefaultLabel)) + contentLanguageChoices
    val countries = listOf(SettingsChoice(GlobalYouTube.SYSTEM, systemDefaultLabel)) + contentCountryChoices
    val lyricsTargetLanguages = listOf(SettingsChoice("device", deviceLanguageLabel)) + contentLanguageChoices
    return SharedSettingsHost(
        platform = SettingsPlatform.IOS,
        onClose = {},
        availablePages =
            setOf(
                SettingsPageId.PLAYER_STYLE,
                SettingsPageId.PLAYER_ARTWORK,
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
                    applyIosAppLanguage(selected)
                    onAppLanguageChanged()
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
            if (choice == SettingsHostChoice.CONTENT_LANGUAGE || choice == SettingsHostChoice.CONTENT_COUNTRY) {
                GlobalYouTube.applyContentRegion(
                    Settings.shared.contentCountry,
                    Settings.shared.contentLanguage,
                    systemCountry,
                    systemLanguage,
                )
            }
        },
    )
}

@Composable
private fun FinishActions(
    onLogin: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.AutoMirrored.Filled.Login, contentDescription = null)
            Text(stringResource(Res.string.log_in), modifier = Modifier.padding(start = 10.dp))
        }
        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.Outlined.SettingsBackupRestore, contentDescription = null)
            Text(stringResource(Res.string.restore_from_backup), modifier = Modifier.padding(start = 10.dp))
        }
        Text(
            stringResource(Res.string.peek_onboarding_settings_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
