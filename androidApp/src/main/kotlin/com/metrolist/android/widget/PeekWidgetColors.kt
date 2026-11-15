/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.metrolist.android.R
import com.metrolist.android.ui.theme.systemDynamicColorScheme
import com.metrolist.android.ui.theme.systemPaletteSeed
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.settings.ThemePaletteStyle
import com.metrolist.shared.ui.theme.DefaultThemeColor
import com.metrolist.shared.ui.theme.orIfIllegible
import com.metrolist.shared.ui.theme.parseThemeSeedColor
import com.metrolist.shared.ui.theme.themeColorScheme

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

/** Light and dark schemes for widgets that do not follow a song: the chosen theme color or the system colors. */
private fun peekStaticWidgetSchemes(context: Context): Pair<ColorScheme, ColorScheme> {
    fun scheme(dark: Boolean) =
        musicWidgetColorScheme(peekWidgetSeed { null }, dark) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                systemDynamicColorScheme(context, dark).orIfIllegible(dark) {
                    themeColorScheme(systemPaletteSeed(context), dark, ThemePaletteStyle.TONAL_SPOT)
                }
            } else {
                themeColorScheme(DefaultThemeColor, dark, ThemePaletteStyle.TONAL_SPOT)
            }
        }
    return scheme(false) to scheme(true)
}

/** Colors the playlist widget: tonal surface and readable titles, in light and dark. */
internal fun bindPeekPlaylistColors(
    context: Context,
    views: RemoteViews,
    titleIds: List<Int>,
) {
    val (light, dark) = peekStaticWidgetSchemes(context)

    fun color(
        id: Int,
        method: String,
        role: (ColorScheme) -> Color,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorInt(id, method, role(light).toArgb(), role(dark).toArgb())
        } else {
            val isDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            views.setInt(id, method, role(if (isDark) dark else light).toArgb())
        }
    }

    color(R.id.widget_playlist_surface, "setColorFilter") { it.surfaceContainer }
    titleIds.forEach { id -> color(id, "setTextColor") { it.onSurface } }
}
