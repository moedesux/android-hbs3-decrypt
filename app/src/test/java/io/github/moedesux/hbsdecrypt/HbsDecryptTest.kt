package io.github.moedesux.hbsdecrypt

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HbsDecryptTest {
    @Test
    fun decryptsSaltedFixtureThroughPublicBoundary() {
        val encrypted = fixtureBytes()
        assertEquals("6af8e56583a8614442251756d8ead1e18d0a906dc1f883b717cf0f74d2f6c5f5", encrypted.sha256())
        val output = ByteArrayOutputStream()
        val result = HbsDecrypt.decrypt(ByteArrayInputStream(encrypted), output, "123456".toCharArray(), bufferSize = 3)
        assertEquals(DecryptionResult.Success(4), result)
        assertArrayEquals("test".toByteArray(), output.toByteArray())
    }

    @Test
    fun decryptsInputLargerThanWorkingBuffer() {
        val plain = ByteArray(100_003) { (it * 31).toByte() }
        val output = ByteArrayOutputStream()
        val result = HbsDecrypt.decrypt(ByteArrayInputStream(encryptForTest(plain)), output, "123456".toCharArray(), bufferSize = 17)
        assertTrue(result is DecryptionResult.Success)
        assertArrayEquals(plain, output.toByteArray())
    }

    @Test
    fun reportsEnvelopeAndCancellationAsTypedOutcomes() {
        assertEquals(DecryptionResult.Failure(FailureReason.NOT_SALTED_ENVELOPE), HbsDecrypt.decrypt(ByteArrayInputStream(ByteArray(20)), ByteArrayOutputStream(), "123456".toCharArray()))
        assertEquals(DecryptionResult.Failure(FailureReason.CANCELLED), HbsDecrypt.decrypt(ByteArrayInputStream(fixtureBytes()), ByteArrayOutputStream(), "123456".toCharArray(), cancellation = DecryptionCancellation { true }))
    }

    private fun fixtureBytes(): ByteArray = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .map { File(it, "assets/encrypted_sample.txt") }
        .first { it.isFile }
        .readBytes()

    private fun encryptForTest(plain: ByteArray): ByteArray {
        val salt = "12345678".toByteArray()
        val material = MessageDigest.getInstance("MD5").let { md ->
            var previous = ByteArray(0); val result = ByteArrayOutputStream()
            while (result.size() < 48) { md.reset(); md.update(previous); md.update("123456".toByteArray()); md.update(salt); previous = md.digest(); result.write(previous) }
            result.toByteArray()
        }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(material.copyOfRange(0, 32), "AES"), IvParameterSpec(material.copyOfRange(32, 48)))
        return "Salted__".toByteArray() + salt + cipher.doFinal(plain)
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
}
