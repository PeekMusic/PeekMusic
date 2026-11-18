package com.metrolist.shared.utils

internal expect fun systemCountryCode(): String

/** The system language as a BCP 47 tag (e.g. de-DE). PeekMusic: for the shared Spotify/Apple Music Canvas lookup. */
internal expect fun systemLanguageTag(): String

/** Unicode NFKD normalization, used to compare song titles across catalogs. */
internal expect fun normalizeNfkd(value: String): String
