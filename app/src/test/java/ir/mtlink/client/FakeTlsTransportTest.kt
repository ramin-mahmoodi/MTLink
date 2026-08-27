package ir.mtlink.client

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeTlsTransportTest {
    @Test
    fun `performs validated fake tls handshake and transfers application data`() {
        val secret = ByteArray(16) { (it + 1).toByte() }
        val server = ServerSocket(0)
        val serverFailure = AtomicReference<Throwable?>(null)
        val worker = Thread {
            runCatching {
                server.accept().use { peer ->
                    val hello = readExact(peer, 517)
                    val clientRandom = hello.copyOfRange(11, 43)
                    val unsignedHello = hello.copyOf().also { ByteArray(32).copyInto(it, 11) }
                    assertArrayEquals(hmac(secret, unsignedHello).copyOfRange(0, 28), clientRandom.copyOfRange(0, 28))

                    val serverHello = tlsRecord(0x16, byteArrayOf(0x02, 0x00, 0x00, 0x22, 0x03, 0x03) + ByteArray(32))
                    val ccs = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)
                    val certificate = tlsRecord(0x17, byteArrayOf(0))
                    val handshake = ByteArrayOutputStream().apply { write(serverHello); write(ccs); write(certificate) }.toByteArray()
                    val signed = handshake.copyOf().also { ByteArray(32).copyInto(it, 11) }
                    hmac(secret, clientRandom + signed).copyInto(handshake, 11)
                    peer.getOutputStream().write(handshake)

                    assertArrayEquals(ccs, readExact(peer, ccs.size))
                    val applicationHeader = readExact(peer, 5)
                    assertEquals(0x17, applicationHeader[0].toInt() and 0xFF)
                    val applicationSize = ((applicationHeader[3].toInt() and 0xFF) shl 8) or (applicationHeader[4].toInt() and 0xFF)
                    assertArrayEquals(byteArrayOf(7, 8, 9), readExact(peer, applicationSize))
                    peer.getOutputStream().write(tlsRecord(0x17, byteArrayOf(9, 8, 7)))
                    peer.getOutputStream().flush()
                }
            }.onFailure(serverFailure::set)
        }
        worker.start()
        Socket("127.0.0.1", server.localPort).use { socket ->
            socket.soTimeout = 3_000
            val transport = FakeTlsTransport(socket, secret, "example.com")
            transport.handshake()
            transport.write(byteArrayOf(7, 8, 9))
            assertArrayEquals(byteArrayOf(9, 8, 7), transport.readExact(3))
        }
        worker.join(3_000)
        server.close()
        assertNull(serverFailure.get())
    }

    @Test
    fun `builds fixed length client hello with embedded random`() {
        val (hello, clientRandom) = FakeTlsTransport.buildClientHelloForTest(ByteArray(16), "example.com")
        assertEquals(517, hello.size)
        assertEquals(0x16, hello[0].toInt() and 0xFF)
        assertArrayEquals(clientRandom, hello.copyOfRange(11, 43))
    }

    private fun readExact(socket: Socket, count: Int): ByteArray {
        val output = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = socket.getInputStream().read(output, offset, count - offset)
            check(read >= 0) { "loopback connection ended" }
            offset += read
        }
        return output
    }

    private fun tlsRecord(type: Int, payload: ByteArray) = byteArrayOf(type.toByte(), 0x03, 0x03, (payload.size ushr 8).toByte(), payload.size.toByte()) + payload
    private fun hmac(secret: ByteArray, payload: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret, "HmacSHA256")); doFinal(payload) }
}
