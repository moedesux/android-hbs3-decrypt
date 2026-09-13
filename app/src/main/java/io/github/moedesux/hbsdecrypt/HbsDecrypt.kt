package io.github.moedesux.hbsdecrypt

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The result of one streaming decryption attempt. */
sealed interface DecryptionResult {
    data class Success(val bytesWritten: Long) : DecryptionResult
    data class Failure(val reason: FailureReason) : DecryptionResult
}

enum class FailureReason { NOT_SALTED_ENVELOPE, INVALID_PASSWORD_OR_DATA, CANCELLED, IO_ERROR }

fun interface DecryptionProgress { fun onProgress(bytesRead: Long): Unit }
fun interface DecryptionCancellation { fun isCancellationRequested(): Boolean }

/** Public file boundary; key derivation and cipher details intentionally stay private. */
object HbsDecrypt {
    private val noProgress = DecryptionProgress { }
    private val neverCancelled = DecryptionCancellation { false }

    fun decrypt(
        input: InputStream,
        output: OutputStream,
        password: CharArray,
        progress: DecryptionProgress = noProgress,
        cancellation: DecryptionCancellation = neverCancelled,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ): DecryptionResult {
        if (bufferSize <= 0) return DecryptionResult.Failure(FailureReason.IO_ERROR)
        if (password.isEmpty()) return DecryptionResult.Failure(FailureReason.INVALID_PASSWORD_OR_DATA)
        return try {
            val header = input.readFully(ENVELOPE_HEADER.size)
                ?: return DecryptionResult.Failure(FailureReason.NOT_SALTED_ENVELOPE)
            if (!header.contentEquals(ENVELOPE_HEADER)) {
                return DecryptionResult.Failure(FailureReason.NOT_SALTED_ENVELOPE)
            }
            val salt = input.readFully(SALT_SIZE)
                ?: return DecryptionResult.Failure(FailureReason.INVALID_PASSWORD_OR_DATA)
            val (key, iv) = evpBytesToKey(password.concatToString().toByteArray(Charsets.UTF_8), salt)
            password.fill('\u0000')
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val buffer = ByteArray(bufferSize)
            var readTotal = ENVELOPE_HEADER.size.toLong() + SALT_SIZE
            var written = 0L
            while (true) {
                if (cancellation.isCancellationRequested()) return DecryptionResult.Failure(FailureReason.CANCELLED)
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                cipher.update(buffer, 0, count)?.let { output.write(it); written += it.size }
                readTotal += count
                progress.onProgress(readTotal)
            }
            if (cancellation.isCancellationRequested()) return DecryptionResult.Failure(FailureReason.CANCELLED)
            cipher.doFinal()?.let { output.write(it); written += it.size }
            DecryptionResult.Success(written)
        } catch (_: javax.crypto.BadPaddingException) {
            DecryptionResult.Failure(FailureReason.INVALID_PASSWORD_OR_DATA)
        } catch (_: javax.crypto.IllegalBlockSizeException) {
            DecryptionResult.Failure(FailureReason.INVALID_PASSWORD_OR_DATA)
        } catch (_: java.io.IOException) {
            DecryptionResult.Failure(FailureReason.IO_ERROR)
        } finally { password.fill('\u0000') }
    }

    private fun InputStream.readFully(size: Int): ByteArray? {
        val result = ByteArray(size); var offset = 0
        while (offset < size) { val count = read(result, offset, size - offset); if (count < 0) return null; if (count == 0) continue; offset += count }
        return result
    }

    private fun evpBytesToKey(password: ByteArray, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val material = ByteArray(48); var filled = 0; var previous = ByteArray(0)
        while (filled < material.size) {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(previous); digest.update(password); digest.update(salt)
            previous = digest.digest()
            val count = minOf(previous.size, material.size - filled)
            previous.copyInto(material, filled, 0, count); filled += count
        }
        return material.copyOfRange(0, 32) to material.copyOfRange(32, 48)
    }

    private const val SALT_SIZE = 8
    private const val DEFAULT_BUFFER_SIZE = 32 * 1024
    private val ENVELOPE_HEADER = "Salted__".toByteArray(Charsets.US_ASCII)
}
