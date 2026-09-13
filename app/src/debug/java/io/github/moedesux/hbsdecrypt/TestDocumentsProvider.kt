package io.github.moedesux.hbsdecrypt

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

class TestDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean {
        val dir = requireNotNull(context).filesDir
        files.clear()
        files[ROOT_ID] = Document("", "Test provider", DocumentsContract.Document.MIME_TYPE_DIR, dir)
        files[SOURCE_ID] = Document("source-bucket", "recovered.bin", "application/octet-stream", File(dir, SOURCE_ID).apply { createNewFile() })
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION).apply {
        newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            add(DocumentsContract.Root.COLUMN_TITLE, "Test provider")
            add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        documentCursor(documentId, projection?.map { it }?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION)

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(projection?.map { it }?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION).apply {
            files.filterValues { it.parent == parentDocumentId }.forEach { (id, document) -> addDocumentRow(this, id, document) }
        }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (parentDocumentId == documentId) return true
        if (parentDocumentId == ROOT_ID && documentId == SOURCE_ID) return true
        var current = documentId
        while (true) {
            val parent = files[current]?.parent ?: return false
            if (parent == parentDocumentId) return true
            current = parent
        }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ParcelFileDescriptor.open(files.getValue(documentId).file, ParcelFileDescriptor.parseMode(mode))

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val id = "document-${nextId++}"
        val file = File(requireNotNull(context).filesDir, id).apply { createNewFile() }
        files[id] = Document(parentDocumentId, displayName, mimeType, file)
        return id
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        files.getValue(documentId).name = displayName
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentId).toString()
    }

    override fun deleteDocument(documentId: String) {
        files.remove(documentId)?.file?.delete()
    }

    private fun documentCursor(documentId: String, projection: Array<String>): Cursor = MatrixCursor(projection).apply {
        addDocumentRow(this, documentId, files.getValue(documentId))
    }

    private fun addDocumentRow(cursor: MatrixCursor, id: String, document: Document) {
        val row = cursor.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, document.name)
        row.add(DocumentsContract.Document.COLUMN_SIZE, document.file.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, document.mimeType)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, document.file.lastModified())
    }

    private data class Document(val parent: String, var name: String, val mimeType: String, val file: File)

    companion object {
        const val AUTHORITY = "io.github.moedesux.hbsdecrypt.test.documents"
        const val ROOT_ID = "root"
        const val SOURCE_ID = "source"
        private var nextId = 0
        private val files = linkedMapOf<String, Document>()
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        fun installFixture(resolver: android.content.ContentResolver) {
            files.keys.toList().filter { it != ROOT_ID && it != SOURCE_ID }.forEach { deleteDocumentForTest(it) }
            writeSource(resolver, FIXTURE)
        }
        fun installInvalidSource(resolver: android.content.ContentResolver) = writeSource(resolver, byteArrayOf(1, 2, 3))
        fun installExisting(resolver: android.content.ContentResolver, name: String, contents: String) {
            val document = DocumentsContract.createDocument(resolver, treeUri(), "text/plain", name)!!
            resolver.openOutputStream(document)!!.use { it.write(contents.toByteArray()) }
        }
        fun idFor(name: String): String = files.entries.first { it.value.name == name }.key

        private fun writeSource(resolver: android.content.ContentResolver, bytes: ByteArray) {
            resolver.openOutputStream(DocumentsContract.buildDocumentUri(AUTHORITY, SOURCE_ID), "wt")!!.use { it.write(bytes) }
        }
        private fun treeUri(): Uri = DocumentsContract.buildDocumentUriUsingTree(
            DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID), ROOT_ID
        )
        private fun deleteDocumentForTest(id: String) { files.remove(id)?.file?.delete() }
        private val FIXTURE = android.util.Base64.decode(
            "U2FsdGVkX1/pG8/UPo+1NBpGiQBUf7GAYQW0iwFAwdU=", android.util.Base64.DEFAULT
        )
    }
}
