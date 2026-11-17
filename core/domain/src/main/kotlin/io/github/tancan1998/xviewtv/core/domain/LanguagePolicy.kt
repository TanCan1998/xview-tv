package io.github.tancan1998.xviewtv.core.domain

import java.util.Locale

/** Match system locales to the installed resource languages, then use English. */
object LanguagePolicy {
    fun select(systemTags: List<String>, supported: List<String>): String {
        require("en" in supported)
        for (tag in systemTags) {
            val locale = Locale.forLanguageTag(tag)
            if (locale.language == "zh") {
                val traditional = locale.script == "Hant" || (locale.script.isEmpty() && locale.country in setOf("TW", "HK", "MO"))
                val desired = if (traditional) "zh-Hant" else "zh-Hans"
                supported.firstOrNull { it.equals(desired, true) }?.let { return it }
            } else supported.firstOrNull { Locale.forLanguageTag(it).language == locale.language }?.let { return it }
        }
        return "en"
    }
}
