package meow.nyx.spotlib

import kotlin.time.Clock

internal object Totp {
    private const val SECRET = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N"
    private const val VERSION = 61
    private const val PERIOD_SECONDS = 30L

    fun version(): Int = VERSION

    fun code(nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()): String {
        val bytes = SECRET.mapIndexed { index, character -> character.code xor (index % 33 + 9) }
        val raw = bytes.joinToString("").encodeToByteArray()
        val key = base32Decode(base32Encode(raw))
        return hotp(key, nowEpochMillis / 1000 / PERIOD_SECONDS)
    }

    private fun hotp(
        key: ByteArray,
        counter: Long,
    ): String {
        val data = ByteArray(8)
        for (index in 0 until 8) data[7 - index] = ((counter shr (8 * index)) and 0xFF).toByte()
        val digest = hmacSha1(key, data)
        val offset = digest[digest.size - 1].toInt() and 0x0F
        val binary =
            ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    // PeekMusic: HMAC-SHA1 in plain Kotlin (RFC 2104 over FIPS 180-1) instead of javax.crypto, so this runs on iOS too.
    internal fun hmacSha1(
        key: ByteArray,
        message: ByteArray,
    ): ByteArray {
        val blockSize = 64
        val normalizedKey = (if (key.size > blockSize) sha1(key) else key).copyOf(blockSize)
        val inner = ByteArray(blockSize) { (normalizedKey[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(blockSize) { (normalizedKey[it].toInt() xor 0x5c).toByte() }
        return sha1(outer + sha1(inner + message))
    }

    internal fun sha1(message: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val bitLength = message.size.toLong() * 8
        val paddedSize = ((message.size + 8) / 64 + 1) * 64
        val padded = message.copyOf(paddedSize)
        padded[message.size] = 0x80.toByte()
        for (index in 0 until 8) padded[paddedSize - 1 - index] = (bitLength ushr (8 * index)).toByte()
        val words = IntArray(80)
        for (chunk in 0 until paddedSize step 64) {
            for (index in 0 until 16) {
                val offset = chunk + index * 4
                words[index] =
                    ((padded[offset].toInt() and 0xFF) shl 24) or
                    ((padded[offset + 1].toInt() and 0xFF) shl 16) or
                    ((padded[offset + 2].toInt() and 0xFF) shl 8) or
                    (padded[offset + 3].toInt() and 0xFF)
            }
            for (index in 16 until 80) {
                words[index] = (words[index - 3] xor words[index - 8] xor words[index - 14] xor words[index - 16]).rotateLeft(1)
            }
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (index in 0 until 80) {
                val (f, k) =
                    when (index) {
                        in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                        in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                        in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                        else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                    }
                val temp = a.rotateLeft(5) + f + e + k + words[index]
                e = d
                d = c
                c = b.rotateLeft(30)
                b = a
                a = temp
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
        }
        return intArrayOf(h0, h1, h2, h3, h4)
            .flatMap { word -> (3 downTo 0).map { shift -> (word ushr (8 * shift)).toByte() } }
            .toByteArray()
    }

    private fun base32Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val output = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                output.append(alphabet[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) output.append(alphabet[(buffer shl (5 - bits)) and 0x1F])
        return output.toString()
    }

    private fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val output = ArrayList<Byte>()
        for (character in base32) {
            val value = alphabet.indexOf(character)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                output.add(((buffer shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return output.toByteArray()
    }
}
