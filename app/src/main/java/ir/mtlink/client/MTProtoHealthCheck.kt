package ir.mtlink.client

import android.util.Base64
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A compact MTProxy health check for standard and `dd` secrets. It verifies a
 * real Telegram `resPQ` reply to an unauthenticated `req_pq_multi` request.
 * Fake-TLS (`ee`) remains TCP-only until that transport is separately proven.
 */
object MTProtoHealthCheck {
    private const val REQ_PQ_MULTI = 0xBE7E8EF1.toInt()
    private const val RES_PQ = 0x05162463
    private const val MAX_FRAME_SIZE = 2 * 1024 * 1024
    private val secureRandom = SecureRandom()

    private enum class SecretKind { STANDARD, DD, EE, EXTENDED, INVALID }
    private data class ParsedSecret(val bytes: ByteArray, val kind: SecretKind, val fakeTlsDomain: String? = null)
    private data class Session(val init: ByteArray, val encryptor: Cipher, val decryptor: Cipher)

    fun test(proxy: ProxyRecord, timeoutSeconds: Int): ProxyRecord {
        val startedAt = System.nanoTime()
        val parsed = parseSecret(proxy.secret.orEmpty())
        if (parsed == null) return failed(proxy, startedAt, "MTProto secret نامعتبر است")
        val timeoutMillis = MTLinkStore.normalizeTestTimeout(timeoutSeconds) * 1_000
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        val errors = mutableListOf<String>()
        for (dcId in intArrayOf(2, 1, 3, 4, 5)) {
            val remaining = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
            if (remaining <= 0) break
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(proxy.host, proxy.port), remaining)
                    socket.soTimeout = ((deadline - System.nanoTime()) / 1_000_000L).toInt().coerceAtLeast(1)
                    val fakeTls = parsed.fakeTlsDomain?.let { FakeTlsTransport(socket, parsed.bytes, it).also(FakeTlsTransport::handshake) }
                    val write: (ByteArray) -> Unit = fakeTls?.let { transport -> { value -> transport.write(value) } }
                        ?: { value -> socket.getOutputStream().write(value); socket.getOutputStream().flush() }
                    val read: (Int) -> ByteArray = fakeTls?.let { transport -> transport::readExact } ?: { size -> readExact(socket, size) }
                    val session = makeSession(parsed.bytes, dcId)
                    write(session.init)
                    val nonce = ByteArray(16).also(secureRandom::nextBytes)
                    write(session.encryptor.update(framePaddedIntermediate(makeReqPqMulti(nonce))))
                    validateResPq(readPaddedFrame(read, session.decryptor), nonce)
                }
                return proxy.copy(
                    status = ProxyStatus.REACHABLE,
                    latencyMs = elapsedMillis(startedAt),
                    testedAt = System.currentTimeMillis(),
                    lastError = null,
                    verification = ProxyVerification.MTPROTO_PROTOCOL,
                )
            } catch (error: Exception) {
                errors += error.message.orEmpty()
            }
        }
        return failed(proxy, startedAt, errors.lastOrNull()?.take(120) ?: "پاسخ معتبر Telegram دریافت نشد")
    }

    internal fun secretKindForTest(secret: String): String = parseSecret(secret)?.kind?.name ?: SecretKind.INVALID.name

    internal fun validateResPqForTest(frame: ByteArray, nonce: ByteArray): Boolean = runCatching {
        validateResPq(frame, nonce)
        true
    }.getOrDefault(false)

    private fun failed(proxy: ProxyRecord, startedAt: Long, reason: String) = proxy.copy(
        status = ProxyStatus.UNREACHABLE,
        latencyMs = null,
        testedAt = System.currentTimeMillis(),
        lastError = reason,
        verification = ProxyVerification.NONE,
    )

    private fun elapsedMillis(startedAt: Long) = (System.nanoTime() - startedAt) / 1_000_000L

    private fun parseSecret(value: String): ParsedSecret? {
        if (value.length !in 16..512) return null
        val raw = runCatching {
            if (value.length % 2 == 0 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) hex(value)
            else Base64.decode(value.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        }.getOrNull() ?: return null
        return when {
            raw.size == 16 -> ParsedSecret(raw, SecretKind.STANDARD)
            raw.size >= 17 && (raw[0].toInt() and 0xFF) == 0xDD -> ParsedSecret(raw.copyOfRange(1, 17), SecretKind.DD)
            raw.size >= 17 && (raw[0].toInt() and 0xFF) == 0xEE -> ParsedSecret(raw.copyOfRange(1, 17), SecretKind.EE, extractFakeTlsDomain(raw.copyOfRange(17, raw.size)))
            raw.size > 16 -> ParsedSecret(raw.copyOfRange(0, 16), SecretKind.EXTENDED)
            else -> null
        }
    }

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }

    private fun makeSession(secret: ByteArray, dcId: Int): Session {
        val forbidden = setOf("GET ".encodeToByteArray(), "POST".encodeToByteArray(), "HEAD".encodeToByteArray(), "OPTI".encodeToByteArray(), byteArrayOf(0, 0, 0, 0), byteArrayOf(0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte(), 0xEF.toByte()), byteArrayOf(0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte(), 0xEE.toByte()), byteArrayOf(0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte(), 0xDD.toByte()))
        val init = ByteArray(64)
        do {
            secureRandom.nextBytes(init)
        } while (init[0].toInt() and 0xFF == 0xEF || forbidden.any { it.contentEquals(init.copyOfRange(0, 4)) } || init.copyOfRange(4, 8).all { it == 0.toByte() })
        byteBuffer(init, 56).putInt(PROTO_PADDED_INTERMEDIATE)
        byteBuffer(init, 60).putShort(dcId.toShort()).putShort(0)
        val encryptor = ctr(sha256(init.copyOfRange(8, 40) + secret), init.copyOfRange(40, 56), Cipher.ENCRYPT_MODE)
        val decryptor = ctr(sha256(reversedRange(init, 55, 24) + secret), reversedRange(init, 23, 8), Cipher.DECRYPT_MODE)
        val encrypted = encryptor.update(init)
        encrypted.copyInto(init, destinationOffset = 56, startIndex = 56, endIndex = 64)
        return Session(init, encryptor, decryptor)
    }

    private fun makeReqPqMulti(nonce: ByteArray): ByteArray {
        val body = littleEndian(4 + nonce.size).putInt(REQ_PQ_MULTI).put(nonce).array()
        val messageId = (System.currentTimeMillis() * 4_294_967L) and -4L
        return littleEndian(8 + 8 + 4 + body.size).putLong(0).putLong(messageId).putInt(body.size).put(body).array()
    }

    private fun framePaddedIntermediate(message: ByteArray): ByteArray {
        val padding = ByteArray(secureRandom.nextInt(16)).also(secureRandom::nextBytes)
        return littleEndian(4 + message.size + padding.size).putInt(message.size + padding.size).put(message).put(padding).array()
    }

    private fun extractFakeTlsDomain(value: ByteArray): String {
        val text = value.decodeToString().takeWhile { it.isLetterOrDigit() || it == '.' || it == '-' }
        return text.takeIf { it.isNotBlank() } ?: "www.google.com"
    }

    private fun readPaddedFrame(read: (Int) -> ByteArray, decryptor: Cipher): ByteArray {
        val frameLength = byteBuffer(decryptor.update(read(4)), 0).int and 0x7FFFFFFF
        require(frameLength in 1..MAX_FRAME_SIZE) { "طول پاسخ Telegram معتبر نیست" }
        return decryptor.update(read(frameLength))
    }

    private fun readExact(socket: Socket, size: Int): ByteArray {
        val result = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val count = socket.getInputStream().read(result, offset, size - offset)
            if (count < 0) throw IllegalStateException("اتصال پیش از پاسخ Telegram بسته شد")
            offset += count
        }
        return result
    }

    private fun validateResPq(frame: ByteArray, nonce: ByteArray) {
        require(frame.size >= 56) { "پاسخ MTProto کوتاه است" }
        require(frame.copyOfRange(0, 8).all { it == 0.toByte() }) { "پاسخ MTProto بدون احراز هویت نیست" }
        val bodySize = byteBuffer(frame, 16).int
        require(bodySize >= 36 && bodySize + 20 <= frame.size) { "طول پیام Telegram معتبر نیست" }
        val body = frame.copyOfRange(20, 20 + bodySize)
        require(byteBuffer(body, 0).int == RES_PQ) { "پاسخ مورد انتظار Telegram دریافت نشد" }
        require(body.copyOfRange(4, 20).contentEquals(nonce)) { "nonce پاسخ Telegram تطابق ندارد" }
    }

    private fun ctr(key: ByteArray, iv: ByteArray, mode: Int): Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
    internal fun reversedRangeForTest(value: ByteArray, first: Int, last: Int): ByteArray = reversedRange(value, first, last)
    private fun reversedRange(value: ByteArray, first: Int, last: Int): ByteArray = ByteArray(first - last + 1) { offset -> value[first - offset] }
    private fun littleEndian(size: Int): ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
    private fun byteBuffer(value: ByteArray, offset: Int): ByteBuffer = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).apply { position(offset) }
    private const val PROTO_PADDED_INTERMEDIATE = 0xDDDDDDDD.toInt()
}
