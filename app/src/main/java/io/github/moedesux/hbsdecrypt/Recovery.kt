package io.github.moedesux.hbsdecrypt

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.util.concurrent.Executor

enum class CollisionPolicy { SKIP, REPLACE }
data class RecoveryState(val source: Uri? = null, val destination: Uri? = null, val passwordPresent: Boolean = false, val running: Boolean = false, val bytesRead: Long = 0, val outcome: String? = null) { val canStart get() = source != null && destination != null && passwordPresent && !running }

class RecoveryRunner(private val resolver: ContentResolver, private val executor: Executor) {
    fun run(source: Uri, tree: Uri, password: CharArray, policy: CollisionPolicy = CollisionPolicy.SKIP, onProgress: (Long) -> Unit, onResult: (String) -> Unit) = executor.execute {
        try {
            val name = resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "recovered.bin" } ?: "recovered.bin"
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)); var found: Uri? = null
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { while (it.moveToNext()) if (name == it.getString(1)) found = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0)) }
            if (found != null && policy == CollisionPolicy.SKIP) { onResult("Skipped: $name"); return@execute }
            val temporary = DocumentsContract.createDocument(resolver, tree, "application/octet-stream", "$name.part") ?: error("Could not create temporary output")
            val result = resolver.openInputStream(source)!!.use { input -> resolver.openOutputStream(temporary)!!.use { output -> HbsDecrypt.decrypt(input, BufferedOutputStream(output), password, progress = DecryptionProgress { onProgress(it) }) } }
            if (result is DecryptionResult.Success) { if (found != null) DocumentsContract.deleteDocument(resolver, found!!); DocumentsContract.renameDocument(resolver, temporary, name); onResult("Recovered: $name (${result.bytesWritten} bytes)") } else { DocumentsContract.deleteDocument(resolver, temporary); onResult("Failed: $result") }
        } catch (e: Exception) { onResult("Failed: ${e.message ?: "I/O error"}") } finally { password.fill('\u0000') }
    }
}
