package com.metrolist.shared.utils

import java.text.Normalizer
import java.util.Locale

object SystemLocale {
    @Volatile
    var current: Locale = Locale.getDefault()
}

internal actual fun systemCountryCode(): String = SystemLocale.current.country

internal actual fun systemLanguageTag(): String = SystemLocale.current.toLanguageTag()

internal actual fun normalizeNfkd(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
