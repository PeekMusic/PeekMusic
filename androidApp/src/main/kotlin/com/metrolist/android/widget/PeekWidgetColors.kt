/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.widget

import androidx.compose.ui.graphics.Color
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.theme.parseThemeSeedColor

/**
 * Widget colors follow the app's own color settings (PeekMusic decision 2026-11-15): cover colors only when the app
 * uses them, the chosen theme color when Material You is off, otherwise the system colors (`null`).
 */
internal fun peekWidgetSeed(artworkSeed: () -> Color?): Color? {
    val settings = Settings.shared
    return when {
        !settings.materialYouEnabled -> parseThemeSeedColor(settings.selectedThemeColor)
        settings.dynamicTheme -> artworkSeed()
        else -> null
    }
}
