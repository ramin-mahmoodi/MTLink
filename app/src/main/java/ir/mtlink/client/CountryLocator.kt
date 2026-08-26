package ir.mtlink.client

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

object CountryLocator {
    fun lookup(host: String): String? {
        val ip = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
        val address = ip.hostAddress ?: return null
        if (ip.isAnyLocalAddress || ip.isLoopbackAddress || ip.isSiteLocalAddress || address.contains(':')) return null
        return lookupCountry("https://ipwho.is/$address", "country_code") ?: lookupCountry("https://api.country.is/$address", "country")
    }

    private fun lookupCountry(url: String, field: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MTLink/1.0")
        }
        return runCatching {
            connection.inputStream.bufferedReader().use { input ->
                val value = JSONObject(input.readText())
                value.optString(field).trim().uppercase().takeIf { it.length == 2 }
            }
        }.getOrNull().also { connection.disconnect() }
    }
}
