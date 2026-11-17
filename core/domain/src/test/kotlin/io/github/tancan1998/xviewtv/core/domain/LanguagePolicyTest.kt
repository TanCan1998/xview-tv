package io.github.tancan1998.xviewtv.core.domain

import org.junit.Assert.*
import org.junit.Test

class LanguagePolicyTest {
    private val supported = listOf("zh-Hans", "en")
    @Test fun simplifiedChineseMatchesInstalledChinese() {
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans", "zh-Hans-CN", "zh"))
            assertEquals("zh-Hans", LanguagePolicy.select(listOf(tag), supported))
    }
    @Test fun englishRegionalVariantsMatchEnglish() {
        assertEquals("en", LanguagePolicy.select(listOf("en-GB"), supported))
    }
    @Test fun unsupportedAndTraditionalChineseFallBackToEnglish() {
        for (tag in listOf("fr-FR", "ja-JP", "zh-TW", "zh-HK", "zh-Hant"))
            assertEquals("en", LanguagePolicy.select(listOf(tag), supported))
        assertEquals("en", LanguagePolicy.select(emptyList(), supported))
    }
    @Test fun followsFirstSupportedSystemLocaleAndAllowsFutureResources() {
        assertEquals("zh-Hans", LanguagePolicy.select(listOf("fr-FR", "zh-CN"), supported))
        assertEquals("fr", LanguagePolicy.select(listOf("fr-CA"), supported + "fr"))
    }
}
