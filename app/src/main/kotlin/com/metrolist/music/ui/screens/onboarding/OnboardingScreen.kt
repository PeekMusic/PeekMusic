/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.R
import com.metrolist.music.ui.screens.onboarding.pages.ContentFiltersPage
import com.metrolist.music.ui.screens.onboarding.pages.FinishPage
import com.metrolist.music.ui.screens.onboarding.pages.PlaybackPage
import com.metrolist.music.ui.screens.onboarding.pages.PlayerPage
import com.metrolist.music.ui.screens.onboarding.pages.QueueCachePage
import com.metrolist.music.ui.screens.onboarding.pages.ThemePage
import com.metrolist.music.ui.screens.onboarding.pages.WelcomePage
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 8

@Composable
fun OnboardingScreen(
    onLogin: () -> Unit,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val (crossfade) = com.metrolist.music.utils.rememberPreference(com.metrolist.music.constants.CrossfadeEnabledKey, false)
    val (preloadCount) = com.metrolist.music.utils.rememberPreference(com.metrolist.music.constants.PreloadQueueCountKey, 1)
    val (enableSongCache) = com.metrolist.music.utils.rememberPreference(com.metrolist.music.constants.EnableSongCacheKey, true)
    val (maxSongCacheSize) = com.metrolist.music.utils.rememberPreference(com.metrolist.music.constants.MaxSongCacheSizeKey, 1024)
    val showCrossfadeWarning = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar with skip button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                TextButton(
                    onClick = { viewModel.complete(login = false, onLogin = onLogin, onDismiss = onFinish) },
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> WelcomePage(
                        onNextClick = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    1 -> ThemePage(modifier = Modifier.fillMaxSize())
                    2 -> PlayerPage(modifier = Modifier.fillMaxSize())
                    3 -> PlaybackPage(modifier = Modifier.fillMaxSize())
                    4 -> QueueCachePage(modifier = Modifier.fillMaxSize())
                    5 -> ContentFiltersPage(modifier = Modifier.fillMaxSize())
                    6 -> com.metrolist.music.ui.screens.onboarding.pages.ExtraSettingsPage(modifier = Modifier.fillMaxSize())
                    7 -> FinishPage(
                        onLoginClick = {
                            viewModel.complete(login = true, onLogin = onLogin, onDismiss = onFinish)
                        },
                        onFinishClick = {
                            viewModel.complete(login = false, onLogin = onLogin, onDismiss = onFinish)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Bottom navigation
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageIndicator(
                    pageCount = PAGE_COUNT,
                    currentPage = pagerState.currentPage,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            },
                        ) {
                            Text(stringResource(R.string.onboarding_back))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(64.dp))
                    }

                    if (pagerState.currentPage in 1 until PAGE_COUNT - 1) {
                        Button(
                            onClick = {
                                if (pagerState.currentPage == 4 && crossfade && (!enableSongCache || maxSongCacheSize == 0 || preloadCount == 0)) {
                                    showCrossfadeWarning.value = true
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.onboarding_next))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(64.dp))
                    }
                }
            }
        }
    }
    
    if (showCrossfadeWarning.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCrossfadeWarning.value = false },
            title = { Text(stringResource(R.string.onboarding_crossfade_warning_title)) },
            text = { Text(stringResource(R.string.onboarding_crossfade_warning_desc)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showCrossfadeWarning.value = false
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                ) {
                    Text(stringResource(R.string.onboarding_crossfade_warning_continue))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showCrossfadeWarning.value = false }
                ) {
                    Text(stringResource(R.string.onboarding_crossfade_warning_back))
                }
            }
        )
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .size(if (index == currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentPage) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        ),
            )
        }
    }
}
