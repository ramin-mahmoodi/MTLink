package ir.mtlink.client

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MTLinkStore(context: Context) {
    private val preferences = context.getSharedPreferences("mtlink_store", Context.MODE_PRIVATE)

    fun sources(): MutableList<SourceDefinition> {
        val stored = preferences.getString("sources", null) ?: return defaultSources().toMutableList()
        return runCatching {
            val list = JSONArray(stored)
            MutableList(list.length()) { index -> sourceFromJson(list.getJSONObject(index)) }
        }.getOrElse { defaultSources().toMutableList() }
    }

    fun saveSources(sources: List<SourceDefinition>) {
        preferences.edit().putString("sources", JSONArray().also { result -> sources.forEach { result.put(sourceToJson(it)) } }.toString()).apply()
    }

    fun proxies(): MutableList<ProxyRecord> {
        val stored = preferences.getString("proxies", null) ?: return mutableListOf()
        return runCatching {
            val list = JSONArray(stored)
            MutableList(list.length()) { index -> proxyFromJson(list.getJSONObject(index)) }
        }.getOrElse { mutableListOf() }
    }

    fun saveProxies(proxies: List<ProxyRecord>) {
        preferences.edit().putString("proxies", JSONArray().also { result -> proxies.forEach { result.put(proxyToJson(it)) } }.toString()).apply()
    }

    fun clearProxies() = preferences.edit().remove("proxies").apply()

    fun appPreferences(): AppPreferences = AppPreferences(
        autoTestAfterFetch = preferences.getBoolean("auto_test_after_fetch", false),
        hapticsEnabled = preferences.getBoolean("haptics_enabled", true),
        language = runCatching { AppLanguage.valueOf(preferences.getString("language", AppLanguage.FA.name) ?: AppLanguage.FA.name) }.getOrDefault(AppLanguage.FA),
        periodicTestEnabled = preferences.getBoolean("periodic_test_enabled", false),
        periodicTestMinutes = preferences.getInt("periodic_test_minutes", 60).coerceAtLeast(15),
    )

    fun saveAppPreferences(value: AppPreferences) {
        preferences.edit()
            .putBoolean("auto_test_after_fetch", value.autoTestAfterFetch)
            .putBoolean("haptics_enabled", value.hapticsEnabled)
            .putString("language", value.language.name)
            .putBoolean("periodic_test_enabled", value.periodicTestEnabled)
            .putInt("periodic_test_minutes", value.periodicTestMinutes.coerceAtLeast(15))
            .apply()
    }

    fun resetSources() = saveSources(defaultSources())

    private fun sourceToJson(source: SourceDefinition) = JSONObject().apply {
        put("id", source.id); put("title", source.title); put("url", source.url)
        put("type", source.type.name); put("enabled", source.enabled); put("builtIn", source.builtIn); put("fetchLimit", source.fetchLimit.coerceIn(5, 250))
        put("lastFetchedAt", source.lastFetchedAt); put("lastFetchCount", source.lastFetchCount)
        put("lastError", source.lastError)
    }

    private fun sourceFromJson(value: JSONObject) = SourceDefinition(
        id = value.optString("id"), title = value.optString("title"), url = value.optString("url"),
        type = runCatching { SourceType.valueOf(value.optString("type", "AUTO")) }.getOrDefault(SourceType.AUTO),
        enabled = value.optBoolean("enabled", true), builtIn = value.optBoolean("builtIn", false), fetchLimit = value.optInt("fetchLimit", 25).coerceIn(5, 250),
        lastFetchedAt = value.optLong("lastFetchedAt", 0), lastFetchCount = value.optInt("lastFetchCount", 0),
        lastError = value.optString("lastError").takeIf { it.isNotBlank() },
    )

    private fun proxyToJson(proxy: ProxyRecord) = JSONObject().apply {
        put("id", proxy.id); put("protocol", proxy.protocol.name); put("host", proxy.host); put("port", proxy.port)
        put("secret", proxy.secret); put("sourceId", proxy.sourceId); put("fetchedAt", proxy.fetchedAt)
        put("status", proxy.status.name); put("latencyMs", proxy.latencyMs); put("testedAt", proxy.testedAt); put("lastError", proxy.lastError)
        put("favorite", proxy.favorite); put("countryCode", proxy.countryCode)
    }

    private fun proxyFromJson(value: JSONObject) = ProxyRecord(
        id = value.optString("id"),
        protocol = runCatching { ProxyProtocol.valueOf(value.optString("protocol")) }.getOrDefault(ProxyProtocol.MTPROTO),
        host = value.optString("host"), port = value.optInt("port"), secret = value.optString("secret").takeIf { it.isNotBlank() },
        sourceId = value.optString("sourceId"), fetchedAt = value.optLong("fetchedAt"),
        status = runCatching { ProxyStatus.valueOf(value.optString("status")) }.getOrDefault(ProxyStatus.UNTESTED),
        latencyMs = value.optLong("latencyMs").takeIf { value.has("latencyMs") }, testedAt = value.optLong("testedAt"),
        lastError = value.optString("lastError").takeIf { it.isNotBlank() }, favorite = value.optBoolean("favorite", false),
        countryCode = value.optString("countryCode").takeIf { it.length == 2 }?.uppercase(),
    )

    private fun defaultSources(): List<SourceDefinition> {
        val base = "https://raw.githubusercontent.com/V2RAYCONFIGSPOOL/TELEGRAM_PROXY_SUB/refs/heads/main"
        val pools = (1..10).map { number ->
            SourceDefinition("v2ray-pool-$number", "V2Ray Pool $number", "$base/telegram_proxy_no$number.txt", SourceType.TEXT, builtIn = true)
        }
        return pools + listOf(
            SourceDefinition("argh94-scraper", "Argh94 Scraper", "https://raw.githubusercontent.com/Argh94/telegram-proxy-scraper/refs/heads/main/proxy.txt", SourceType.TEXT, builtIn = true),
            SourceDefinition("free-proxy-db", "FreeProxyDB", "https://freeproxydb.com/api/proxy/search?country=&protocol=socks5&anonymity=&speed=0,60&https=0&page_index=1&page_size=100", SourceType.JSON, builtIn = true),
            SourceDefinition("vanced-telegram", "Vanced Telegram", "https://vanced.to/telegram", SourceType.HTML, builtIn = true),
            SourceDefinition("mhdi-taheri-collector", "MhdiTaheri Collector", "https://raw.githubusercontent.com/MhdiTaheri/ProxyCollector/refs/heads/main/proxy.txt", SourceType.TEXT, builtIn = true),
        )
    }

    companion object { fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}" }
}
