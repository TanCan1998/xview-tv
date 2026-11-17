package io.github.tancan1998.xviewtv

import android.content.Context
import io.github.tancan1998.xviewtv.core.network.ProxyConfig
import io.github.tancan1998.xviewtv.core.network.ProxyMode

class AppPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    fun readProxy(): ProxyConfig {
        val mode = runCatching { ProxyMode.valueOf(preferences.getString("proxy_mode", "DIRECT")!!) }.getOrDefault(ProxyMode.DIRECT)
        val config = ProxyConfig(mode, preferences.getString("proxy_host", "").orEmpty(), preferences.getInt("proxy_port", 0))
        return if (config.validate() == null) config else ProxyConfig()
    }
    fun saveProxy(config: ProxyConfig): Boolean {
        require(config.validate() == null)
        return preferences.edit().putString("proxy_mode", config.mode.name)
            .putString("proxy_host", config.host).putInt("proxy_port", config.port).commit()
    }
    fun language(): String {
        val supported = appContext.resources.getStringArray(io.github.tancan1998.xviewtv.core.designsystem.R.array.supported_language_tags).toList()
        val saved = preferences.getString("language", null)
        if (saved in supported) return saved!!
        if (saved == "zh" && "zh-Hans" in supported) {
            preferences.edit().putString("language", "zh-Hans").remove("hide_sensitive").apply()
            return "zh-Hans"
        }
        val configuration = android.content.res.Resources.getSystem().configuration
        @Suppress("DEPRECATION") val tags = if (android.os.Build.VERSION.SDK_INT >= 24)
            (0 until configuration.locales.size()).map { configuration.locales[it].toLanguageTag() }
        else listOf(configuration.locale.toLanguageTag())
        val selected = io.github.tancan1998.xviewtv.core.domain.LanguagePolicy.select(tags, supported)
        preferences.edit().putString("language", selected).remove("hide_sensitive").apply()
        return selected
    }
    fun saveLanguage(value: String): Boolean {
        require(value in appContext.resources.getStringArray(io.github.tancan1998.xviewtv.core.designsystem.R.array.supported_language_tags))
        return preferences.edit().putString("language", value).commit()
    }
    fun seekBack(): Int = io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekSeconds(preferences.getInt("seek_back", 10))
    fun seekForward(): Int = io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekSeconds(preferences.getInt("seek_forward", 10))
    fun saveSeek(back: Int, forward: Int): Boolean {
        require(back in io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekChoices && forward in io.github.tancan1998.xviewtv.core.domain.PlaybackPolicy.seekChoices)
        return preferences.edit().putInt("seek_back", back).putInt("seek_forward", forward).commit()
    }
}
