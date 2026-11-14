/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.android.ui.theme

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.metrolist.shared.ui.theme.orIfIllegible
import org.json.JSONObject

/**
 * Samsung system roles can be stale; use wallpaper tonal palettes instead of Android 14+ roles.
 * PeekMusic: except for Samsung's preset ("basic colors") palettes, monochrome among them. For those
 * Samsung hands apps an unrelated accent palette (green turns orange) while the roles match the system UI.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal fun systemDynamicColorScheme(
    context: Context,
    darkTheme: Boolean,
): ColorScheme {
    val base = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return base
    val roles = systemRoleColorScheme(context, darkTheme, base)

    val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    if (samsung && !usesPresetPalette(context) && !roles.isMonochrome()) {
        return dynamicColorScheme(
            primary = Color(context.getColor(android.R.color.system_accent1_500)),
            secondary = Color(context.getColor(android.R.color.system_accent2_500)),
            tertiary = Color(context.getColor(android.R.color.system_accent3_500)),
            neutral = Color(context.getColor(android.R.color.system_neutral1_500)),
            neutralVariant = Color(context.getColor(android.R.color.system_neutral2_500)),
            isDark = darkTheme,
            contrastLevel = context.getSystemService(UiModeManager::class.java).contrast.toDouble(),
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
    }
    return roles.orIfIllegible(darkTheme) { base }
}

/** The Android 14+ system color roles, falling back to [base] for any role the OEM omits. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun systemRoleColorScheme(
    context: Context,
    darkTheme: Boolean,
    base: ColorScheme,
): ColorScheme {
    val suffix = if (darkTheme) "dark" else "light"

    @SuppressLint("DiscouragedApi") // OEMs can omit system color roles; resolve each name before using the Compose fallback.
    fun role(
        name: String,
        fallback: Color,
    ): Color {
        val id = context.resources.getIdentifier("system_${name}_$suffix", "color", "android")
        return if (id == 0) fallback else Color(context.resources.getColor(id, context.theme))
    }

    val primary = role("primary", base.primary)
    return base.copy(
        primary = primary,
        onPrimary = role("on_primary", base.onPrimary),
        primaryContainer = role("primary_container", base.primaryContainer),
        onPrimaryContainer = role("on_primary_container", base.onPrimaryContainer),
        secondary = role("secondary", base.secondary),
        onSecondary = role("on_secondary", base.onSecondary),
        secondaryContainer = role("secondary_container", base.secondaryContainer),
        onSecondaryContainer = role("on_secondary_container", base.onSecondaryContainer),
        tertiary = role("tertiary", base.tertiary),
        onTertiary = role("on_tertiary", base.onTertiary),
        tertiaryContainer = role("tertiary_container", base.tertiaryContainer),
        onTertiaryContainer = role("on_tertiary_container", base.onTertiaryContainer),
        background = role("background", base.background),
        onBackground = role("on_background", base.onBackground),
        surface = role("surface", base.surface),
        onSurface = role("on_surface", base.onSurface),
        surfaceVariant = role("surface_variant", base.surfaceVariant),
        onSurfaceVariant = role("on_surface_variant", base.onSurfaceVariant),
        surfaceTint = primary,
        outline = role("outline", base.outline),
        outlineVariant = role("outline_variant", base.outlineVariant),
        error = role("error", base.error),
        onError = role("on_error", base.onError),
        errorContainer = role("error_container", base.errorContainer),
        onErrorContainer = role("on_error_container", base.onErrorContainer),
        surfaceBright = role("surface_bright", base.surfaceBright),
        surfaceDim = role("surface_dim", base.surfaceDim),
        surfaceContainerLowest = role("surface_container_lowest", base.surfaceContainerLowest),
        surfaceContainerLow = role("surface_container_low", base.surfaceContainerLow),
        surfaceContainer = role("surface_container", base.surfaceContainer),
        surfaceContainerHigh = role("surface_container_high", base.surfaceContainerHigh),
        surfaceContainerHighest = role("surface_container_highest", base.surfaceContainerHighest),
    )
}

/** True when the user picked one of the system's preset palettes instead of wallpaper colors. */
private fun usesPresetPalette(context: Context): Boolean =
    runCatching {
        val overlays = Settings.Secure.getString(context.contentResolver, THEME_CUSTOMIZATION_KEY) ?: return false
        JSONObject(overlays).optString(COLOR_SOURCE_KEY) == PRESET_COLOR_SOURCE
    }.getOrDefault(false)

private const val THEME_CUSTOMIZATION_KEY = "theme_customization_overlay_packages"
private const val COLOR_SOURCE_KEY = "android.theme.customization.color_source"
private const val PRESET_COLOR_SOURCE = "preset"

/** True when the accent roles carry no color, which only a monochrome system theme produces. */
private fun ColorScheme.isMonochrome(): Boolean =
    listOf(primary, primaryContainer, secondaryContainer, tertiaryContainer).all { color ->
        maxOf(color.red, color.green, color.blue) - minOf(color.red, color.green, color.blue) < MONOCHROME_SPREAD
    }

private const val MONOCHROME_SPREAD = 0.04f
