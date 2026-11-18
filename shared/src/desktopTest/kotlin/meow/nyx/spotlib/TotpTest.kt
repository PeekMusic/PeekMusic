/*
 * PeekMusic, a fork of Metrolist (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package meow.nyx.spotlib

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/** The plain-Kotlin SHA-1 and HMAC that iOS uses must match the JDK byte for byte. */
class TotpTest {
    @Test
    fun `sha1 matches the jdk for every padding length`() {
        val random = Random(7)
        for (size in 0..200) {
            val message = random.nextBytes(size)
            assertArrayEquals(MessageDigest.getInstance("SHA-1").digest(message), Totp.sha1(message))
        }
    }

    @Test
    fun `hmac sha1 matches the jdk for short and long keys`() {
        val random = Random(11)
        for (keySize in listOf(1, 20, 63, 64, 65, 200)) {
            val key = random.nextBytes(keySize)
            val message = random.nextBytes(37)
            val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }
            assertArrayEquals(mac.doFinal(message), Totp.hmacSha1(key, message))
        }
    }

    @Test
    fun `code is six digits`() {
        val code = Totp.code(nowEpochMillis = 1_700_000_000_000L)
        assertEquals(6, code.length)
        assertEquals(code, Totp.code(nowEpochMillis = 1_700_000_000_000L))
    }
}
