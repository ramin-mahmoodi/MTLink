package ir.mtlink.client

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MTProtoFakeTlsHealthCheckTest {
    @Test
    fun `verifies ee proxy only after fake tls and matching telegram reply`() {
        val secret = ByteArray(16) { (it + 20).toByte() }
        val server = ServerSocket(0)
        val failure = AtomicReference<Throwable?>(null)
        val thread = Thread {
            runCatching { serveFakeTlsMtProxy(server, secret) }.onFailure(failure::set)
        }
        thread.start()

        val proxy = ProxyRecord(
            id = "fake-tls-test",
            protocol = ProxyProtocol.MTPROTO,
            host = "127.0.0.1",
            port = server.localPort,
            secret = "ee${secret.joinToString("") { "%02x".format(it) }}6578616d706c652e636f6d",
            sourceId = "test",
            fetchedAt = 0,
        )
        val result = MTProtoHealthCheck.test(proxy, 3)
        thread.join(3_000)
        server.close()

        assertNull(failure.get())
        assertEquals(ProxyStatus.REACHABLE, result.status)
        assertEquals(ProxyVerification.MTPROTO_PROTOCOL, result.verification)
        assertTrue(result.latencyMs != null)
    }

    private fun serveFakeTlsMtProxy(server: ServerSocket, secret: ByteArray) {
        server.accept().use { peer ->
            val hello = readExact(peer, 517)
            val clientRandom = hello.copyOfRange(11, 43)
            val zeroedHello = hello.copyOf().also { ByteArray(32).copyInto(it, 11) }
            check(clientRandom.copyOfRange(0, 28).contentEquals(hmac(secret, zeroedHello).copyOfRange(0, 28))) { "ClientHello HMAC mismatch" }

            val serverHello = tlsRecord(0x16, byteArrayOf(0x02, 0x00, 0x00, 0x22, 0x03, 0x03) + ByteArray(32))
            val ccs = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)
            val certificate = tlsRecord(0x17, byteArrayOf(0))
            val handshake = ByteArrayOutputStream().apply { write(serverHello); write(ccs); write(certificate) }.toByteArray()
            val signed = handshake.copyOf().also { ByteArray(32).copyInto(it, 11) }
            hmac(secret, clientRandom + signed).copyInto(handshake, 11)
            peer.getOutputStream().write(handshake)

            check(readExact(peer, ccs.size).contentEquals(ccs)) { "client ChangeCipherSpec missing" }
            val init = readTlsApplication(peer)
            check(init.size == 64) { "obfuscated init missing" }
            val outgoing = aesCtr(sha256(init.copyOfRange(8, 40) + secret), init.copyOfRange(40, 56))
            outgoing.update(init)
            val request = outgoing.update(readTlsApplication(peer))
            val frameSize = littleEndianInt(request, 0)
            val message = request.copyOfRange(4, 4 + frameSize)
            check(littleEndianInt(message, 20) == 0xBE7E8EF1.toInt()) { "req_pq_multi missing" }
            val nonce = message.copyOfRange(24, 40)

            val body = littleEndianBytes(0x05162463) + nonce + ByteArray(16) { 7 } + littleEndianBytes(0) + littleEndianBytes(0) + littleEndianBytes(0)
            val reply = ByteArray(8) + longLittleEndian(0x0102030405060708L) + littleEndianBytes(body.size) + body
            val reverseKey = ByteArray(32) { index -> init[55 - index] }
            val reverseIv = ByteArray(16) { index -> init[23 - index] }
            val incoming = aesCtr(sha256(reverseKey + secret), reverseIv)
            val encrypted = incoming.update(littleEndianBytes(reply.size) + reply)
            peer.getOutputStream().write(tlsRecord(0x17, encrypted))
            peer.getOutputStream().flush()
        }
    }

    private fun readTlsApplication(socket: Socket): ByteArray {
        val header = readExact(socket, 5)
        check(header[0].toInt() and 0xFF == 0x17) { "expected application record" }
        val size = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        return readExact(socket, size)
    }

    private fun readExact(socket: Socket, count: Int): ByteArray {
        val output = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = socket.getInputStream().read(output, offset, count - offset)
            check(read >= 0) { "loopback connection closed" }
            offset += read
        }
        return output
    }

    private fun tlsRecord(type: Int, payload: ByteArray) = byteArrayOf(type.toByte(), 0x03, 0x03, (payload.size ushr 8).toByte(), payload.size.toByte()) + payload
    private fun aesCtr(key: ByteArray, iv: ByteArray): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
    private fun hmac(secret: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret, "HmacSHA256")); doFinal(value) }
    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)
    private fun littleEndianInt(value: ByteArray, offset: Int) = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8) or ((value[offset + 2].toInt() and 0xFF) shl 16) or ((value[offset + 3].toInt() and 0xFF) shl 24)
    private fun littleEndianBytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
    private fun longLittleEndian(value: Long) = ByteArray(8) { index -> (value ushr (index * 8)).toByte() }
}
