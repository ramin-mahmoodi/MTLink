package ir.mtlink.client

import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object ProxyEngine {
    private const val MAX_RESPONSE_CHARS = 900_000
    private const val MAX_EXTRACTED_PER_SOURCE = 500

    fun fetch(source: SourceDefinition, limit: Int): List<ProxyRecord> {
        val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain, text/html;q=0.9, */*;q=0.8")
            setRequestProperty("User-Agent", "MTLink/1.0")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            // fixed: the streaming cap below also covers chunked responses without Content-Length.
            val body = readLimitedBody(connection.inputStream.bufferedReader())
            val type = if (source.type == SourceType.AUTO) detectType(connection.contentType, body) else source.type
            val extracted = if (type == SourceType.JSON) parseJson(body, limit) else parseLinks(body, limit)
            extracted.map { it.copy(sourceId = source.id) }.distinctBy { it.stableKey() }.take(limit)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimitedBody(reader: BufferedReader): String = reader.use {
        val buffer = CharArray(8_192)
        val output = StringBuilder()
        while (true) {
            val read = it.read(buffer)
            if (read < 0) break
            if (output.length + read > MAX_RESPONSE_CHARS) throw IllegalStateException("پاسخ منبع بیش از حد بزرگ است")
            output.append(buffer, 0, read)
        }
        output.toString()
    }

    fun test(proxy: ProxyRecord, timeoutSeconds: Int): ProxyRecord {
        val timeoutMillis = MTLinkStore.normalizeTestTimeout(timeoutSeconds) * 1_000
        val startedAt = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(proxy.host, proxy.port), timeoutMillis)
                if (proxy.protocol == ProxyProtocol.SOCKS5) {
                    val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toInt()
                    socket.soTimeout = (timeoutMillis - elapsedMillis).coerceAtLeast(1)
                    socket.getOutputStream().write(byteArrayOf(0x05, 0x01, 0x00))
                    socket.getOutputStream().flush()
                    val response = ByteArray(2)
                    if (socket.getInputStream().read(response) != 2 || response[0].toInt() != 0x05 || response[1].toInt() != 0x00) {
                        throw IllegalStateException("پاسخ SOCKS5 معتبر نیست")
                    }
                }
            }
            proxy.copy(status = ProxyStatus.REACHABLE, latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), testedAt = System.currentTimeMillis(), lastError = null)
        } catch (error: Exception) {
            proxy.copy(status = ProxyStatus.UNREACHABLE, latencyMs = null, testedAt = System.currentTimeMillis(), lastError = error.message?.take(120) ?: "اتصال برقرار نشد")
        }
    }

    private fun detectType(contentType: String?, body: String): SourceType {
        val text = body.trimStart()
        return when {
            contentType.orEmpty().contains("json", true) || text.startsWith("{") || text.startsWith("[") -> SourceType.JSON
            contentType.orEmpty().contains("html", true) || text.contains("<html", true) -> SourceType.HTML
            else -> SourceType.TEXT
        }
    }

    internal fun extractPayloadForTest(raw: String, type: SourceType = SourceType.TEXT): List<ProxyRecord> =
        if (type == SourceType.JSON) parseJson(raw, MAX_EXTRACTED_PER_SOURCE) else parseLinks(raw, MAX_EXTRACTED_PER_SOURCE)

    private fun parseLinks(raw: String, requestedLimit: Int): List<ProxyRecord> {
        val clean = raw.replace("&amp;", "&").replace("&#x2F;", "/")
        val limit = requestedLimit.coerceIn(1, MAX_EXTRACTED_PER_SOURCE)
        val output = LinkedHashMap<String, ProxyRecord>()
        val markers = listOf("tg://proxy?", "https://t.me/proxy?", "http://t.me/proxy?", "socks5://")
        markers.forEach { marker ->
            var cursor = 0
            while (output.size < limit) {
                val start = clean.indexOf(marker, cursor, ignoreCase = true)
                if (start < 0) break
                val end = endOfLink(clean, start)
                val candidate = clean.substring(start, end)
                val record = if (marker == "socks5://") parseSocks(candidate) else parseTelegram(candidate)
                if (record != null) output.putIfAbsent(record.stableKey(), record)
                cursor = if (end > start) end else start + marker.length
            }
        }
        return output.values.toList()
    }

    private fun endOfLink(value: String, start: Int): Int {
        var index = start
        while (index < value.length) {
            when (value[index]) {
                ' ', '\n', '\r', '\t', '\"', '\'', '<', '>' -> return index
            }
            index += 1
        }
        return value.length
    }

    private fun parseTelegram(raw: String): ProxyRecord? = runCatching {
        val uri = URI(raw)
        val parameters = queryParameters(uri.rawQuery.orEmpty())
        val host = parameters["server"]?.trim()?.removeSuffix(".").orEmpty()
        val port = parameters["port"]?.toIntOrNull() ?: 0
        val secret = parameters["secret"]?.trim().orEmpty()
        if (!validHost(host) || !validPort(port) || secret.length < 16) return null
        ProxyRecord(MTLinkStore.newId("proxy"), ProxyProtocol.MTPROTO, host, port, secret, "", System.currentTimeMillis())
    }.getOrNull()

    private fun parseSocks(raw: String): ProxyRecord? = runCatching {
        val uri = URI(raw)
        val host = uri.host?.trim()?.removeSuffix(".").orEmpty()
        val port = uri.port
        if (!validHost(host) || !validPort(port)) return null
        ProxyRecord(MTLinkStore.newId("proxy"), ProxyProtocol.SOCKS5, host, port, null, "", System.currentTimeMillis())
    }.getOrNull()

    private fun parseJson(raw: String, requestedLimit: Int): List<ProxyRecord> {
        val limit = requestedLimit.coerceIn(1, MAX_EXTRACTED_PER_SOURCE)
        val output = LinkedHashMap<String, ProxyRecord>()
        parseLinks(raw, limit).forEach { output.putIfAbsent(it.stableKey(), it) }
        var cursor = 0
        while (output.size < limit) {
            val protocolIndex = raw.indexOf("socks5", cursor, ignoreCase = true)
            if (protocolIndex < 0) break
            val start = raw.lastIndexOf('{', protocolIndex)
            val end = raw.indexOf('}', protocolIndex)
            if (start >= 0 && end > start) {
                runCatching {
                    val item = raw.substring(start, end + 1)
                    val protocol = jsonString(item, "protocol").ifBlank { jsonString(item, "site_protocol") }
                    val host = jsonString(item, "ip").ifBlank { jsonString(item, "host") }.ifBlank { jsonString(item, "server") }.trim().removeSuffix(".")
                    val port = jsonInt(item, "port")
                    if (protocol.equals("socks5", true) && validHost(host) && validPort(port)) {
                        val proxy = ProxyRecord(MTLinkStore.newId("proxy"), ProxyProtocol.SOCKS5, host, port, null, "", System.currentTimeMillis())
                        output.putIfAbsent(proxy.stableKey(), proxy)
                    }
                }
                cursor = end + 1
            } else {
                cursor = protocolIndex + 6
            }
        }
        return output.values.toList()
    }

    private fun jsonString(objectText: String, key: String): String {
        val keyIndex = objectText.indexOf("\"$key\"", ignoreCase = true)
        if (keyIndex < 0) return ""
        val colon = objectText.indexOf(':', keyIndex)
        val firstQuote = objectText.indexOf('\"', colon + 1)
        if (colon < 0 || firstQuote < 0) return ""
        val secondQuote = objectText.indexOf('\"', firstQuote + 1)
        return if (secondQuote > firstQuote) objectText.substring(firstQuote + 1, secondQuote) else ""
    }

    private fun jsonInt(objectText: String, key: String): Int {
        val keyIndex = objectText.indexOf("\"$key\"", ignoreCase = true)
        if (keyIndex < 0) return 0
        val colon = objectText.indexOf(':', keyIndex)
        if (colon < 0) return 0
        val digits = objectText.substring(colon + 1).trimStart().takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    private fun validHost(value: String) = value.isNotBlank() && value.length <= 253 && value.none { it.isWhitespace() || it in "/?#@" }
    private fun validPort(value: Int) = value in 1..65535
    private fun queryParameters(rawQuery: String): Map<String, String> = rawQuery.split("&").mapNotNull { part ->
        val key = part.substringBefore("=", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        key to URLDecoder.decode(part.substringAfter("=", ""), "UTF-8")
    }.toMap()
}
