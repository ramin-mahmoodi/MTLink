package ir.mtlink.client

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Fake-TLS wrapper used by MTProxy `ee` secrets. It validates the server HMAC
 * before exposing the obfuscated MTProto stream carried in TLS-like records.
 */
internal class FakeTlsTransport(
    private val socket: Socket,
    private val secret: ByteArray,
    private val domain: String,
) {
    private var firstWrite = true
    private var pending = ByteArray(0)

    fun handshake() {
        val (hello, clientRandom) = buildClientHello(secret, domain)
        socket.getOutputStream().write(hello)
        socket.getOutputStream().flush()

        val serverBytes = ByteArrayOutputStream()
        val first = readRecord()
        require(first.type == TYPE_HANDSHAKE) { "پاسخ Fake-TLS از نوع ServerHello نبود" }
        serverBytes.write(first.raw)

        var ccsFound = false
        var recordsSeen = 0
        while (!ccsFound && recordsSeen < MAX_HANDSHAKE_RECORDS) {
            val record = readRecord()
            serverBytes.write(record.raw)
            when (record.type) {
                TYPE_HANDSHAKE -> recordsSeen += 1
                TYPE_CHANGE_CIPHER_SPEC -> {
                    require(record.payload.contentEquals(byteArrayOf(1))) { "ChangeCipherSpec جعلی معتبر نیست" }
                    ccsFound = true
                }
                else -> throw IllegalStateException("رکورد غیرمنتظره در Handshake Fake-TLS")
            }
        }
        require(ccsFound) { "ChangeCipherSpec در Handshake Fake-TLS دریافت نشد" }

        val certificate = readRecord()
        require(certificate.type == TYPE_APPLICATION_DATA) { "رکورد نهایی Handshake Fake-TLS معتبر نیست" }
        serverBytes.write(certificate.raw)
        validateServerHandshake(secret, clientRandom, serverBytes.toByteArray())
    }

    fun write(value: ByteArray) {
        val output = socket.getOutputStream()
        if (firstWrite) {
            output.write(CHANGE_CIPHER_SPEC)
            firstWrite = false
        }
        var offset = 0
        while (offset < value.size) {
            val count = minOf(MAX_APPLICATION_DATA, value.size - offset)
            output.write(record(TYPE_APPLICATION_DATA, value.copyOfRange(offset, offset + count)))
            offset += count
        }
        output.flush()
    }

    fun readExact(count: Int): ByteArray {
        require(count >= 0) { "طول خواندن Fake-TLS نامعتبر است" }
        while (pending.size < count) {
            val record = readRecord()
            when (record.type) {
                TYPE_CHANGE_CIPHER_SPEC -> require(record.payload.contentEquals(byteArrayOf(1))) { "ChangeCipherSpec دریافتی معتبر نیست" }
                TYPE_APPLICATION_DATA -> pending += record.payload
                else -> throw IllegalStateException("رکورد پاسخ Fake-TLS معتبر نیست")
            }
        }
        val result = pending.copyOfRange(0, count)
        pending = pending.copyOfRange(count, pending.size)
        return result
    }

    private fun readRecord(): TlsRecord {
        val header = readSocketExact(5)
        require(header[1] == 0x03.toByte() && (header[2] == 0x01.toByte() || header[2] == 0x03.toByte())) { "نسخهٔ رکورد Fake-TLS معتبر نیست" }
        val size = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        require(size <= MAX_TLS_RECORD_SIZE) { "رکورد Fake-TLS بیش‌ازحد بزرگ است" }
        val payload = readSocketExact(size)
        return TlsRecord(header[0].toInt() and 0xFF, payload, header + payload)
    }

    private fun readSocketExact(size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = socket.getInputStream().read(result, offset, size - offset)
            if (read < 0) throw IllegalStateException("اتصال در Handshake Fake-TLS بسته شد")
            offset += read
        }
        return result
    }

    private data class TlsRecord(val type: Int, val payload: ByteArray, val raw: ByteArray)

    companion object {
        private const val TYPE_HANDSHAKE = 0x16
        private const val TYPE_CHANGE_CIPHER_SPEC = 0x14
        private const val TYPE_APPLICATION_DATA = 0x17
        private const val MAX_TLS_RECORD_SIZE = 65_535
        private const val MAX_APPLICATION_DATA = 1_425
        private const val MAX_HANDSHAKE_RECORDS = 16
        private const val CLIENT_HELLO_LENGTH = 517
        private val CHANGE_CIPHER_SPEC = byteArrayOf(0x14, 0x03, 0x03, 0x00, 0x01, 0x01)

        internal fun buildClientHelloForTest(secret: ByteArray, domain: String): Pair<ByteArray, ByteArray> = buildClientHello(secret, domain)

        private fun buildClientHello(secret: ByteArray, domain: String): Pair<ByteArray, ByteArray> {
            require(secret.size == 16) { "secret Fake-TLS باید ۱۶ بایت باشد" }
            val host = domain.filter { it.isLetterOrDigit() || it == '.' || it == '-' }.ifBlank { DEFAULT_DOMAIN }.take(253).encodeToByteArray()
            val grease = ByteArray(7) { (((SecureRandom().nextInt() and 0xF0) + 0x0A) and 0xFF).toByte() }
            for (index in 1 until grease.size step 2) if (grease[index] == grease[index - 1]) grease[index] = (grease[index].toInt() xor 0x10).toByte()
            fun greaseAt(index: Int) = byteArrayOf(grease[index], grease[index])
            fun u16(value: Int) = byteArrayOf((value ushr 8).toByte(), value.toByte())
            val out = ByteArrayOutputStream()
            out.write(byteArrayOf(0x16, 0x03, 0x01, 0x02, 0x00, 0x01, 0x00, 0x01, 0xFC.toByte(), 0x03, 0x03))
            val randomOffset = out.size()
            out.write(ByteArray(32))
            out.write(0x20); out.write(ByteArray(32).also(SecureRandom()::nextBytes)); out.write(byteArrayOf(0x00, 0x22)); out.write(greaseAt(0))
            out.write(byteArrayOf(0x13, 0x01, 0x13, 0x02, 0x13, 0x03, 0xC0.toByte(), 0x2B, 0xC0.toByte(), 0x2F, 0xC0.toByte(), 0x2C, 0xC0.toByte(), 0x30, 0xCC.toByte(), 0xA9.toByte(), 0xCC.toByte(), 0xA8.toByte(), 0xC0.toByte(), 0x13, 0xC0.toByte(), 0x14, 0x00, 0x9C.toByte(), 0x00, 0x9D.toByte(), 0x00, 0x2F, 0x00, 0x35, 0x00, 0x0A, 0x01, 0x00, 0x01, 0x91.toByte()))
            out.write(greaseAt(2)); out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00)); out.write(u16(host.size + 5)); out.write(u16(host.size + 3)); out.write(0); out.write(u16(host.size)); out.write(host)
            out.write(byteArrayOf(0x00, 0x17, 0x00, 0x00, 0xFF.toByte(), 0x01, 0x00, 0x01, 0x00, 0x00, 0x0A, 0x00, 0x0A, 0x00, 0x08)); out.write(greaseAt(4))
            out.write(byteArrayOf(0x00, 0x1D, 0x00, 0x17, 0x00, 0x18, 0x00, 0x0B, 0x00, 0x02, 0x01, 0x00, 0x00, 0x23, 0x00, 0x00, 0x00, 0x10, 0x00, 0x0E, 0x00, 0x0C, 0x02, 0x68, 0x32, 0x08, 0x68, 0x74, 0x74, 0x70, 0x2F, 0x31, 0x2E, 0x31, 0x00, 0x05, 0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0D, 0x00, 0x14, 0x00, 0x12, 0x04, 0x03, 0x08, 0x04, 0x04, 0x01, 0x05, 0x03, 0x08, 0x05, 0x05, 0x01, 0x08, 0x06, 0x06, 0x01, 0x02, 0x01, 0x00, 0x12, 0x00, 0x00, 0x00, 0x33, 0x00, 0x2B, 0x00, 0x29))
            out.write(greaseAt(4)); out.write(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x1D, 0x00, 0x20)); out.write(x25519PublicKey())
            out.write(byteArrayOf(0x00, 0x2D, 0x00, 0x02, 0x01, 0x01, 0x00, 0x2B, 0x00, 0x0B, 0x0A)); out.write(greaseAt(6)); out.write(byteArrayOf(0x03, 0x04, 0x03, 0x03, 0x03, 0x02, 0x03, 0x01, 0x00, 0x1B, 0x00, 0x03, 0x02, 0x00, 0x02)); out.write(greaseAt(3)); out.write(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x15))
            val padding = CLIENT_HELLO_LENGTH - 2 - out.size()
            require(padding >= 0) { "domain Fake-TLS بیش‌ازحد بلند است" }
            out.write(u16(padding)); out.write(ByteArray(padding))
            val hello = out.toByteArray()
            require(hello.size == CLIENT_HELLO_LENGTH) { "طول ClientHello Fake-TLS معتبر نیست" }
            val digest = hmac(secret, hello)
            val now = (System.currentTimeMillis() / 1_000L).toInt()
            val tail = littleEndianInt(digest, 28) xor now
            val clientRandom = digest.copyOfRange(0, 28) + littleEndianBytes(tail)
            clientRandom.copyInto(hello, randomOffset)
            return hello to clientRandom
        }

        private fun validateServerHandshake(secret: ByteArray, clientRandom: ByteArray, response: ByteArray) {
            require(response.size >= 43) { "پاسخ Handshake Fake-TLS کوتاه است" }
            val received = response.copyOfRange(11, 43)
            val unsigned = response.copyOf()
            ByteArray(32).copyInto(unsigned, 11)
            require(received.contentEquals(hmac(secret, clientRandom + unsigned))) { "امضای پاسخ Fake-TLS با secret مطابقت ندارد" }
        }

        private fun record(type: Int, payload: ByteArray): ByteArray = byteArrayOf(type.toByte(), 0x03, 0x03, (payload.size ushr 8).toByte(), payload.size.toByte()) + payload
        private fun hmac(secret: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(secret, "HmacSHA256")); doFinal(value) }
        private fun x25519PublicKey(): ByteArray {
            val scalar = ByteArray(32).also(SecureRandom()::nextBytes)
            scalar[0] = (scalar[0].toInt() and 248).toByte()
            scalar[31] = ((scalar[31].toInt() and 127) or 64).toByte()
            val prime = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
            val a24 = BigInteger.valueOf(121665)
            var x2 = BigInteger.ONE
            var z2 = BigInteger.ZERO
            var x3 = BigInteger.valueOf(9)
            var z3 = BigInteger.ONE
            var swap = 0
            for (bit in 254 downTo 0) {
                val value = ((scalar[bit ushr 3].toInt() and 0xFF) ushr (bit and 7)) and 1
                if (swap != value) {
                    val oldX2 = x2; x2 = x3; x3 = oldX2
                    val oldZ2 = z2; z2 = z3; z3 = oldZ2
                }
                swap = value
                val a = x2.add(z2).mod(prime); val aa = a.multiply(a).mod(prime)
                val b = x2.subtract(z2).mod(prime); val bb = b.multiply(b).mod(prime)
                val e = aa.subtract(bb).mod(prime)
                val c = x3.add(z3).mod(prime); val d = x3.subtract(z3).mod(prime)
                val da = d.multiply(a).mod(prime); val cb = c.multiply(b).mod(prime)
                x3 = da.add(cb).mod(prime).let { it.multiply(it).mod(prime) }
                z3 = BigInteger.valueOf(9).multiply(da.subtract(cb).mod(prime)).mod(prime).let { it.multiply(it).mod(prime) }
                x2 = aa.multiply(bb).mod(prime)
                z2 = e.multiply(aa.add(a24.multiply(e)).mod(prime)).mod(prime)
            }
            if (swap != 0) {
                val oldX2 = x2; x2 = x3; x3 = oldX2
                val oldZ2 = z2; z2 = z3; z3 = oldZ2
            }
            val coordinate = x2.multiply(z2.modPow(prime.subtract(BigInteger.valueOf(2)), prime)).mod(prime)
            val bigEndian = coordinate.toByteArray()
            return ByteArray(32).also { result ->
                var output = 0
                for (index in bigEndian.lastIndex downTo maxOf(0, bigEndian.size - 32)) result[output++] = bigEndian[index]
            }
        }
        private fun littleEndianInt(value: ByteArray, offset: Int) = (value[offset].toInt() and 0xFF) or ((value[offset + 1].toInt() and 0xFF) shl 8) or ((value[offset + 2].toInt() and 0xFF) shl 16) or ((value[offset + 3].toInt() and 0xFF) shl 24)
        private fun littleEndianBytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())
        private const val DEFAULT_DOMAIN = "www.google.com"
    }
}
