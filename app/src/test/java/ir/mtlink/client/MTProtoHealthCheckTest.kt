package ir.mtlink.client

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MTProtoHealthCheckTest {
    @Test
    fun `classifies standard dd and ee secrets without network access`() {
        assertEquals("STANDARD", MTProtoHealthCheck.secretKindForTest("104462821249bd7ac519130220c25d09"))
        assertEquals("DD", MTProtoHealthCheck.secretKindForTest("dd104462821249bd7ac519130220c25d09"))
        assertEquals("EE", MTProtoHealthCheck.secretKindForTest("ee104462821249bd7ac519130220c25d0963646e2e79656b74616e65742e636f6d"))
        assertEquals("INVALID", MTProtoHealthCheck.secretKindForTest("invalid"))
    }

    @Test
    fun `accepts only matching resPQ reply`() {
        val nonce = ByteArray(16) { it.toByte() }
        val serverNonce = ByteArray(16) { (it + 16).toByte() }
        val body = littleEndian(52)
            .putInt(0x05162463)
            .put(nonce)
            .put(serverNonce)
            .putInt(0) // pq bytes length
            .putInt(0) // vector constructor placeholder for this bounded parser test
            .putInt(0) // fingerprints count
            .array()
        val frame = littleEndian(20 + body.size)
            .putLong(0)
            .putLong(0x0102030405060708L)
            .putInt(body.size)
            .put(body)
            .array()

        assertTrue(MTProtoHealthCheck.validateResPqForTest(frame, nonce))
        assertFalse(MTProtoHealthCheck.validateResPqForTest(frame, ByteArray(16)))
    }

    @Test
    fun `reverses the exact MTProxy key and IV ranges`() {
        val init = ByteArray(64) { it.toByte() }
        assertTrue(MTProtoHealthCheck.reversedRangeForTest(init, 55, 24).contentEquals((55 downTo 24).map(Int::toByte).toByteArray()))
        assertTrue(MTProtoHealthCheck.reversedRangeForTest(init, 23, 8).contentEquals((23 downTo 8).map(Int::toByte).toByteArray()))
    }

    private fun littleEndian(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
}
