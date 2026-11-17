@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.metrolist.desktop.backup.DesktopBackupRestoreManager
import com.metrolist.desktop.state.DesktopLocaleManager
import com.metrolist.desktop.state.appLanguageChoices
import com.metrolist.desktop.utils.pickDesktopFile
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.app_language_system
import com.metrolist.shared.generated.resources.back
import com.metrolist.shared.generated.resources.continue_action
import com.metrolist.shared.generated.resources.device_language
import com.metrolist.shared.generated.resources.finish
import com.metrolist.shared.generated.resources.log_in
import com.metrolist.shared.generated.resources.no
import com.metrolist.shared.generated.resources.onboarding_backup_picker_error
import com.metrolist.shared.generated.resources.onboarding_backup_picker_title
import com.metrolist.shared.generated.resources.onboarding_backup_restore_failed
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
import com.metrolist.shared.state.applyContentRegion
import com.metrolist.shared.state.contentCountryCodes
import com.metrolist.shared.ui.components.ContainedLoadingIndicator
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.onboarding.OnboardingContent
import com.metrolist.shared.ui.onboarding.OnboardingPage
import com.metrolist.shared.ui.onboarding.OnboardingPageContent
import com.metrolist.shared.ui.settings.SettingsChoice
import com.metrolist.shared.ui.settings.SettingsHostChoice
import com.metrolist.shared.ui.settings.SettingsPageId
import com.metrolist.shared.ui.settings.SettingsPlatform
import com.metrolist.shared.ui.settings.SharedSettingsHost
import com.metrolist.shared.utils.isCrashReportingAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

/**
 * PeekMusic: the desktop onboarding as a full page with the same PeekMusic feature pages as Android (language, look,
 * player, lyrics, artwork, sound, content), built on the shared OnboardingContent like iOS. Settings the desktop has
 * no use for drop out on their own, because the feature pages only show what the desktop settings registry offers.
 */
@Composable
fun DesktopOnboarding(onComplete: (sendCrashReports: Boolean, openLogin: Boolean) -> Unit) {
    val pages =
        remember {
            buildList {
                add(OnboardingPage.WELCOME)
                if (isCrashReportingAvailable()) add(OnboardingPage.CRASH_REPORTS)
                addAll(PeekOnboarding.featurePages)
                add(OnboardingPage.FINISH)
            }
        }
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    var sendCrashReports by rememberSaveable { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val host = rememberDesktopOnboardingHost()

    val restorePickerTitle = stringResource(Res.string.onboarding_backup_picker_title)

    fun nextPage() {
        error = null
        if (pageIndex < pages.lastIndex) {
            pageIndex++
        } else {
            onComplete(sendCrashReports, false)
        }
    }

    fun restoreBackup() {
        if (isWorking) return
        isWorking = true
        scope.launch {
            try {
                error = null
                val selected =
                    runCatching {
                        pickDesktopFile(restorePickerTitle, listOf("zip", "backup"))
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        error = getString(Res.string.onboarding_backup_picker_error, it.message ?: "")
                        return@launch
                    } ?: return@launch

                withContext(Dispatchers.IO) {
                    DesktopBackupRestoreManager.stageRestoreAndRestart(selected) {
                        Settings.shared.desktopOnboardingComplete = true
                        if (isCrashReportingAvailable()) {
                            Settings.shared.desktopPendingOnboardingSentryConsent = sendCrashReports.toString()
                        }
                    }
                }.onFailure {
                    Settings.shared.desktopOnboardingComplete = false
                    Settings.shared.desktopPendingOnboardingSentryConsent = ""
                    error = getString(Res.string.onboarding_backup_restore_failed, it.message ?: "")
                }
            } finally {
                isWorking = false
            }
        }
    }

    @Composable
    fun pageContent(index: Int): OnboardingPageContent {
        val page = pages[index]
        val back: (() -> Unit)? =
            if (index > 0) {
                {
                    error = null
                    pageIndex = index - 1
                }
            } else {
                null
            }
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
                isWorking = isWorking,
                error = error.takeIf { index == pageIndex },
                backLabel = stringResource(Res.string.back),
                onPrimary = ::nextPage,
                // Skip jumps past the whole tour, like Android.
                onSecondary = {
                    error = null
                    pageIndex = pages.lastIndex
                },
                onBack = back,
            )
        }
        return OnboardingPageContent(
            page = page,
            title =
                when (page) {
                    OnboardingPage.WELCOME -> stringResource(Res.string.side_menu_welcome)
                    OnboardingPage.CRASH_REPORTS -> stringResource(Res.string.onboarding_crash_reports_title)
                    else -> stringResource(Res.string.onboarding_finish_title)
                },
            description =
                when (page) {
                    OnboardingPage.WELCOME -> stringResource(Res.string.onboarding_welcome_privacy_description)
                    OnboardingPage.CRASH_REPORTS -> stringResource(Res.string.onboarding_crash_reports_description)
                    else -> stringResource(Res.string.onboarding_finish_description)
                },
            icon =
                when (page) {
                    OnboardingPage.WELCOME -> MetrolistIcons.Outlined.MusicNote
                    OnboardingPage.CRASH_REPORTS -> MetrolistIcons.Outlined.Security
                    else -> MetrolistIcons.Filled.Check
                },
            step = index + 1,
            stepCount = pages.size,
            primaryLabel =
                when (page) {
                    OnboardingPage.WELCOME -> stringResource(Res.string.onboarding_begin_setup)
                    OnboardingPage.CRASH_REPORTS -> stringResource(Res.string.yes)
                    else -> stringResource(Res.string.finish)
                },
            secondaryLabel = if (page == OnboardingPage.CRASH_REPORTS) stringResource(Res.string.no) else null,
            isWorking = isWorking,
            error = error.takeIf { index == pageIndex },
            backLabel = stringResource(Res.string.back),
            onPrimary = {
                if (page == OnboardingPage.CRASH_REPORTS) sendCrashReports = true
                nextPage()
            },
            onSecondary =
                if (page == OnboardingPage.CRASH_REPORTS) {
                    {
                        sendCrashReports = false
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
                        onLogin = { onComplete(sendCrashReports, true) },
                        onRestore = ::restoreBackup,
                        enabled = !isWorking,
                    )
                }
                if (isWorking) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            },
        )
    }
}

/** Settings host for the onboarding pages: the desktop Settings' choices, without navigation. */
@Composable
private fun rememberDesktopOnboardingHost(): SharedSettingsHost {
    val languages = appLanguageChoices()
    val systemDefaultLabel = stringResource(Res.string.app_language_system)
    val deviceLanguageLabel = stringResource(Res.string.device_language)
    val countries =
        remember(systemDefaultLabel) {
            val systemLocale = Locale.getDefault()
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
    val lyricsTargetLanguages =
        listOf(SettingsChoice("device", deviceLanguageLabel)) + languages.filterNot { it.id == GlobalYouTube.SYSTEM }
    return SharedSettingsHost(
        platform = SettingsPlatform.DESKTOP,
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
                    Settings.shared.appLanguage = selected
                    DesktopLocaleManager.apply(selected)
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
                    contentCountry = Settings.shared.contentCountry,
                    contentLanguage = Settings.shared.contentLanguage,
                    systemLocale = DesktopLocaleManager.systemLocale,
                )
            }
        },
    )
}

@Composable
private fun FinishActions(
    onLogin: () -> Unit,
    onRestore: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onLogin,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(MetrolistIcons.AutoMirrored.Filled.Login, contentDescription = null)
            Text(stringResource(Res.string.log_in), modifier = Modifier.padding(start = 10.dp))
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = enabled,
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
