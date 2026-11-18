/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.metrolist.shared.peek.friends.PeekFriendsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState

/**
 * PeekMusic: keeps PeekParty friends online while the app is in front or PeekMusic plays music, so friends see you
 * and can invite you. A closed or suspended app is offline; iOS has no background invites without push.
 */
@Composable
internal fun PeekFriendsPresenceEffect(
    friends: PeekFriendsManager,
    isPlaying: StateFlow<Boolean>,
) {
    val foreground =
        remember {
            MutableStateFlow(UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateBackground)
        }
    DisposableEffect(foreground) {
        val notifications = NSNotificationCenter.defaultCenter
        val activeObserver =
            notifications.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) {
                foreground.value = true
            }
        val backgroundObserver =
            notifications.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) {
                foreground.value = false
            }
        onDispose {
            notifications.removeObserver(activeObserver)
            notifications.removeObserver(backgroundObserver)
        }
    }
    LaunchedEffect(friends, isPlaying) {
        combine(foreground, isPlaying) { inFront, playing -> inFront || playing }
            .distinctUntilChanged()
            .collect(friends::setActive)
    }
}
