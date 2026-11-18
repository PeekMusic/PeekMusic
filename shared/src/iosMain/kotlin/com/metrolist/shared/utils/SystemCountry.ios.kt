package com.metrolist.shared.utils

import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.decomposedStringWithCompatibilityMapping
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

internal actual fun systemCountryCode(): String = NSLocale.currentLocale.countryCode.orEmpty()

internal actual fun systemLanguageTag(): String =
    ((NSLocale.preferredLanguages.firstOrNull() as? String)?.takeIf(String::isNotBlank) ?: NSLocale.currentLocale.localeIdentifier)
        .replace('_', '-')

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun normalizeNfkd(value: String): String = (value as NSString).decomposedStringWithCompatibilityMapping
