package com.staticradio.app.ui.common

import java.util.Locale

/** ISO 3166-1 alpha-2 country codes, resolved via the platform's own locale
 * data rather than a hand-maintained list — accurate and needs no upkeep. */
object KnownCountries {
    val all: List<Pair<String, String>> by lazy {
        Locale.getISOCountries()
            .map { code -> code to Locale("", code).getDisplayCountry(Locale.ENGLISH) }
            .filter { it.second.isNotBlank() }
            .sortedBy { it.second }
    }

    fun nameForCode(code: String): String? =
        all.firstOrNull { it.first.equals(code, ignoreCase = true) }?.second
}

/** ISO 639-1 language display names — Radio Browser's own `language` field is
 * a free-text name (e.g. "german"), not a code, so that's what gets stored;
 * this just constrains entry to a known set instead of hand-typed text. */
object KnownLanguages {
    val all: List<String> by lazy {
        Locale.getISOLanguages()
            .map { code -> Locale(code).getDisplayLanguage(Locale.ENGLISH) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
