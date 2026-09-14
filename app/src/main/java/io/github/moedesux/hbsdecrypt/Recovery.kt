package io.github.moedesux.hbsdecrypt

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedOutputStream
import java.util.concurrent.Executor

enum class CollisionPolicy { SKIP, REPLACE }
data class RecoveryCounts(val decrypted: Int = 0, val skipped: Int = 0, val unsupported: Int = 0, val cancelled: Int = 0, val failed: Int = 0)
data class RecoveryState(val source: Uri? = null, val destination: Uri? = null, val passwordPresent: Boolean = false, val running: Boolean = false, val bytesRead: Long = 0, val outcome: String? = null, val collisionPolicy: CollisionPolicy = CollisionPolicy.SKIP, val counts: RecoveryCounts = RecoveryCounts()) { val canStart get() = source != null && destination != null && passwordPresent && !running }

class RecoveryRunner(private val resolver: ContentResolver, private val executor: Executor) {
    fun run(source: Uri, tree: Uri, password: CharArray, policy: CollisionPolicy = CollisionPolicy.SKIP, cancellation: DecryptionCancellation = DecryptionCancellation { false }, onProgress: (Long) -> Unit, onResult: (String) -> Unit) = executor.execute {
        runFiles(listOf(FileEntry(source, listOf(displayName(source)))), tree, password, policy, cancellation, onProgress, onResult, {}, legacyResult = true)
    }

    fun runTree(sourceTree: Uri, destinationTree: Uri, password: CharArray, policy: CollisionPolicy = CollisionPolicy.SKIP, cancellation: DecryptionCancellation = DecryptionCancellation { false }, onProgress: (Long) -> Unit, onResult: (String) -> Unit, onComplete: (RecoveryCounts) -> Unit = {}) = executor.execute {
        try { runFiles(entries(sourceTree), destinationTree, password, policy, cancellation, onProgress, onResult, onComplete) }
        catch (e: Exception) { password.fill('\u0000'); onResult("Failed: ${e.message ?: "I/O error"}"); onComplete(RecoveryCounts(failed = 1)) }
    }

    private fun runFiles(entries: List<FileEntry>, destination: Uri, password: CharArray, policy: CollisionPolicy, cancellation: DecryptionCancellation, onProgress: (Long) -> Unit, onResult: (String) -> Unit, onComplete: (RecoveryCounts) -> Unit = {}, legacyResult: Boolean = false) {
        var counts = RecoveryCounts(); val messages = mutableListOf<String>()
        for (entry in entries) {
            if (cancellation.isCancellationRequested()) {
                counts = counts.copy(cancelled = counts.cancelled + 1)
                break
            }
            val parent = ensureDirectory(destination, entry.parts.dropLast(1))
            val name = entry.parts.last(); val existing = child(parent, name)
            if (existing != null && policy == CollisionPolicy.SKIP) { counts = counts.copy(skipped = counts.skipped + 1); messages += "Skipped: $name"; continue }
            var temporary: Uri? = null
            try {
                temporary = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.part") ?: error("Could not create temporary output")
                val result = resolver.openInputStream(entry.uri)!!.use { input -> resolver.openOutputStream(temporary)!!.use { output -> BufferedOutputStream(output).use { HbsDecrypt.decrypt(input, it, password.copyOf(), cancellation = cancellation, progress = DecryptionProgress { onProgress(it) }) } } }
                when (result) {
                    is DecryptionResult.Success -> {
                        val finalized = finalize(parent, name, temporary, existing)
                        temporary = null
                        preserveTimestamp(entry.uri, finalized)
                        counts = counts.copy(decrypted = counts.decrypted + 1)
                        messages += "Recovered: $name (${result.bytesWritten} bytes)"
                    }
                    is DecryptionResult.Failure -> { counts = when (result.reason) { FailureReason.NOT_SALTED_ENVELOPE -> counts.copy(unsupported = counts.unsupported + 1); FailureReason.CANCELLED -> counts.copy(cancelled = counts.cancelled + 1); else -> counts.copy(failed = counts.failed + 1) }; messages += "${result.reason}: $name" }
                }
            } catch (e: Exception) { counts = counts.copy(failed = counts.failed + 1); messages += "Failed: $name (${e.message ?: "I/O error"})" }
            finally { temporary?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } } }
        }
        password.fill('\u0000'); onResult(if (legacyResult && messages.size == 1) { val message = messages.single(); if (message.startsWith("NOT_SALTED_ENVELOPE:") || message.startsWith("IO_ERROR:") || message.startsWith("INVALID_PASSWORD_OR_DATA:")) "Failed: $message" else message } else "${counts.decrypted} recovered, ${counts.skipped} skipped, ${counts.unsupported} unsupported, ${counts.cancelled} cancelled, ${counts.failed} failed" + if (messages.isEmpty()) "" else ": ${messages.joinToString("; ")}"); onComplete(counts)
    }

    private data class FileEntry(val uri: Uri, val parts: List<String>)
    private fun finalize(parent: Uri, name: String, temporary: Uri, existing: Uri?): Uri {
        var backup: Uri? = null
        try {
            if (existing != null) {
                backup = runCatching { DocumentsContract.renameDocument(resolver, existing, ".${name}.replace-${System.nanoTime()}.part") }
                    .getOrNull()?.let { existing }
            }
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, temporary, name) }.getOrNull()
            if (renamed != null) {
                backup?.let { check(DocumentsContract.deleteDocument(resolver, it)) }
                return temporary
            }
            // Some providers do not implement rename. Copy the completed temporary
            // document into its final name, keeping the old file until the copy wins.
            var final = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            if (final == null && existing != null && backup == null) {
                    // The provider may reject duplicate names. The temporary is
                    // complete at this point, so deleting the old output is the
                    // safest available non-atomic replacement operation.
                    check(DocumentsContract.deleteDocument(resolver, existing))
                final = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            }
            val finalDocument = final ?: error("Could not create final output")
            try {
                resolver.openInputStream(temporary)!!.use { input -> resolver.openOutputStream(finalDocument)!!.use { output -> input.copyTo(output) } }
                backup?.let { check(DocumentsContract.deleteDocument(resolver, it)) }
                if (existing != null && backup == null) check(DocumentsContract.deleteDocument(resolver, existing))
                check(DocumentsContract.deleteDocument(resolver, temporary))
                return finalDocument
            } catch (failure: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, finalDocument) }
                throw failure
            }
        } catch (failure: Exception) {
            backup?.let { runCatching { DocumentsContract.renameDocument(resolver, it, name) } }
            throw failure
        }
    }

    private fun preserveTimestamp(source: Uri, destination: Uri) {
        val modified = resolver.query(source, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null } ?: return
        runCatching { resolver.update(destination, ContentValues().apply { put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, modified) }, null, null) }
    }
    private fun displayName(uri: Uri) = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else "recovered.bin" } ?: "recovered.bin"
    private fun entries(tree: Uri): List<FileEntry> { val out = mutableListOf<FileEntry>(); fun walk(parent: Uri, parts: List<String>) { val id = DocumentsContract.getDocumentId(parent); val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id); resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c -> while (c.moveToNext()) { val cid = c.getString(0); val name = c.getString(1); val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cid); if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(uri, parts + name) else out += FileEntry(uri, parts + name) } } }; walk(tree, emptyList()); return out }
    private fun child(parent: Uri, name: String): Uri? { val id = DocumentsContract.getDocumentId(parent); val u = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id); return resolver.query(u, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c -> while (c.moveToNext()) if (c.getString(1) == name) return@use DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0)); null } }
    private fun ensureDirectory(root: Uri, parts: List<String>): Uri { var current = root; parts.forEach { name -> current = child(current, name) ?: DocumentsContract.createDocument(resolver, current, DocumentsContract.Document.MIME_TYPE_DIR, name) ?: error("Could not create directory $name") }; return current }
}
