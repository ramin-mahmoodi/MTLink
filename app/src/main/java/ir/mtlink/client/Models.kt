package ir.mtlink.client

enum class SourceType { AUTO, TEXT, JSON, HTML }
enum class ProxyProtocol { MTPROTO, SOCKS5 }
enum class ProxyStatus { UNTESTED, CHECKING, REACHABLE, UNREACHABLE }
enum class AppLanguage { FA, EN }
enum class AppTheme { SYSTEM, LIGHT, DARK }

data class SourceDefinition(
    val id: String,
    val title: String,
    val url: String,
    val type: SourceType,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val fetchLimit: Int = 25,
    val lastFetchedAt: Long = 0L,
    val lastFetchCount: Int = 0,
    val lastError: String? = null,
)

data class ProxyRecord(
    val id: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val secret: String? = null,
    val sourceId: String,
    val fetchedAt: Long,
    val status: ProxyStatus = ProxyStatus.UNTESTED,
    val latencyMs: Long? = null,
    val testedAt: Long = 0L,
    val lastError: String? = null,
    val favorite: Boolean = false,
    val countryCode: String? = null,
) {
    fun stableKey(): String = "${protocol.name}:${host.lowercase()}:$port:${secret ?: ""}"
    fun displayAddress(): String = "$host:$port"
}

data class AppPreferences(
    val autoTestAfterFetch: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.EN,
    val theme: AppTheme = AppTheme.SYSTEM,
    val testTimeoutSeconds: Int = 5,
    val testConcurrency: Int = 8,
    val globalFetchLimit: Int = 500,
    val periodicTestEnabled: Boolean = false,
    val periodicTestMinutes: Int = 60,
)

data class FetchSummary(val added: Int, val errors: Int, val totalSources: Int)
