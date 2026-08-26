package ir.mtlink.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyEngineTest {
    @Test
    fun `extracts and deduplicates MTProto links from text`() {
        val body = """
            tg://proxy?server=example.test&port=443&secret=0123456789abcdef
            https://t.me/proxy?server=example.test&port=443&secret=0123456789abcdef
        """.trimIndent()

        val result = ProxyEngine.extractPayloadForTest(body)

        assertEquals(1, result.size)
        assertEquals(ProxyProtocol.MTPROTO, result.single().protocol)
        assertEquals("example.test", result.single().host)
    }

    @Test
    fun `decodes HTML encoded MTProto parameters`() {
        val body = "<a href=\"https://t.me/proxy?server=198.51.100.8&amp;port=443&amp;secret=0123456789abcdef\">connect</a>"

        val result = ProxyEngine.extractPayloadForTest(body)

        assertEquals(1, result.size)
        assertEquals(443, result.single().port)
        assertEquals("0123456789abcdef", result.single().secret)
    }

    @Test
    fun `extracts SOCKS5 URL`() {
        val result = ProxyEngine.extractPayloadForTest("socks5://198.51.100.9:1080")

        assertEquals(1, result.size)
        assertEquals(ProxyProtocol.SOCKS5, result.single().protocol)
        assertEquals(1080, result.single().port)
        assertTrue(result.single().secret == null)
    }

    @Test
    fun `extracts SOCKS5 object from JSON response`() {
        val body = """{"data":[{"protocol":"socks5","ip":"198.51.100.10","port":1081}]}"""

        val result = ProxyEngine.extractPayloadForTest(body, SourceType.JSON)

        assertEquals("نتیجهٔ استخراج: $result", 1, result.size)
        assertEquals(ProxyProtocol.SOCKS5, result.single().protocol)
        assertEquals("198.51.100.10", result.single().host)
        assertEquals(1081, result.single().port)
    }

    @Test
    fun `caps extraction from a large proxy response`() {
        val body = buildString {
            repeat(700) { index ->
                append("tg://proxy?server=node$index.example&port=443&secret=0123456789abcdef\n")
            }
        }

        val result = ProxyEngine.extractPayloadForTest(body)

        assertEquals(500, result.size)
    }

}
