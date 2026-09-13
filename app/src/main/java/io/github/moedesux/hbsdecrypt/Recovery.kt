package io.github.moedesux.hbsdecrypt

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.util.concurrent.Executor

enum class CollisionPolicy { SKIP, REPLACE }
data class RecoveryState(val source: Uri? = null, val destination: Uri? = null, val passwordPresent: Boolean = false, val running: Boolean = false, val bytesRead: Long = 0, val outcome: String? = null, val collisionPolicy: CollisionPolicy = CollisionPolicy.SKIP) { val canStart get() = source != null && destination != null && passwordPresent && !running }

class RecoveryRunner(private val resolver: ContentResolver, private val executor: Executor) {
    fun run(source: Uri, tree: Uri, password: CharArray, policy: CollisionPolicy = CollisionPolicy.SKIP, cancellation: DecryptionCancellation = DecryptionCancellation { false }, onProgress: (Long) -> Unit, onResult: (String) -> Unit) = executor.execute {
        var temporary: Uri? = null
        var backup: Uri? = null
        try {
            val name = resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "recovered.bin" } ?: "recovered.bin"
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)); var found: Uri? = null
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { while (it.moveToNext()) if (name == it.getString(1)) found = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)) }
            if (found != null && policy == CollisionPolicy.SKIP) { onResult("Skipped: $name"); return@execute }
            temporary = DocumentsContract.createDocument(resolver, tree, "application/octet-stream", "$name.part") ?: error("Could not create temporary output")
            val result = resolver.openInputStream(source)!!.use { input ->
                resolver.openOutputStream(temporary!!)!!.use { output ->
                    BufferedOutputStream(output).use { bufferedOutput ->
                        HbsDecrypt.decrypt(input, bufferedOutput, password, cancellation = cancellation, progress = DecryptionProgress { onProgress(it) })
                    }
                }
            }
            if (result is DecryptionResult.Success) {
                if (found != null) {
                    val backupName = ".$name.replace-${System.nanoTime()}.part"
                    backup = DocumentsContract.renameDocument(resolver, found!!, backupName)
                        ?: error("Provider cannot safely replace existing output")
                }
                try {
                    check(DocumentsContract.renameDocument(resolver, temporary!!, name) != null) { "Could not finalize output safely" }
                } catch (e: Exception) {
                    val restored = backup?.let { runCatching { DocumentsContract.renameDocument(resolver, it, name) }.isSuccess } == true
                    if (restored) backup = null
                    throw e
                }
                backup?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
                temporary = null
                backup = null
                onResult("Recovered: $name (${result.bytesWritten} bytes)")
            } else {
                val failure = (result as DecryptionResult.Failure).reason
                onResult(when (failure) {
                    FailureReason.NOT_SALTED_ENVELOPE -> "Unsupported: $name (not an HBS encrypted file)"
                    FailureReason.CANCELLED -> "Cancelled: $name"
                    FailureReason.INVALID_PASSWORD_OR_DATA -> "Failed: $name (wrong password or corrupted data)"
                    FailureReason.IO_ERROR -> "Failed: $name (I/O error)"
                })
            }
        } catch (e: Exception) { onResult("Failed: ${e.message ?: "I/O error"}") } finally {
            temporary?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
            password.fill('\u0000')
        }
    }
}
