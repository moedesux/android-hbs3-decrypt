package io.github.moedesux.hbsdecrypt

import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryTreeProviderTest {
    private val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private val executor = Executor { it.run() }
    private val destination = DocumentsContract.buildDocumentUriUsingTree(
        DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID), TestDocumentsProvider.ROOT_ID
    )

    @Before fun setUp() = TestDocumentsProvider.installTreeFixture(resolver)

    @Test fun recoversNestedUnicodeMixedAndEmptyFilesWithContinuationAndCounts() {
        val (result, counts) = runTree()
        assertEquals(2, counts.decrypted)
        assertEquals(0, counts.skipped)
        assertEquals(1, counts.unsupported)
        assertEquals(0, counts.cancelled)
        assertEquals(0, counts.failed)
        assertTrue(result.contains("2 recovered, 0 skipped, 1 unsupported, 0 cancelled, 0 failed"))
        assertEquals("test", readAt(listOf("nested", "éxample 文件.bin")))
        assertEquals("", readAt(listOf("empty.bin")))
        assertEquals(listOf("empty.bin", "nested"), childNames(destination))
        assertEquals(listOf("éxample 文件.bin"), childNames(child(destination, "nested")))
    }

    @Test fun skipsExistingNestedOutputAndLeavesTemporaryDocumentsOutOfTree() {
        TestDocumentsProvider.installExisting(resolver, "empty.bin", "keep")
        val (_, counts) = runTree()
        assertEquals(1, counts.decrypted)
        assertEquals(1, counts.skipped)
        assertEquals(1, counts.unsupported)
        assertEquals("keep", readAt(listOf("empty.bin")))
        assertEquals(listOf("empty.bin", "nested"), childNames(destination))
    }

    @Test fun cancellationBetweenFilesKeepsFinalizedOutputAndDoesNotStartLaterFiles() {
        var result: String? = null
        var counts: RecoveryCounts? = null
        RecoveryRunner(resolver, executor).runTree(
            TestDocumentsProvider.sourceTreeUri(), destination, "provider-password".toCharArray(),
            cancellation = DecryptionCancellation { TestDocumentsProvider.cancellationAfterFinalization },
            onProgress = {}, onResult = { result = it }, onComplete = { counts = it }
        )
        assertEquals(1, counts!!.decrypted)
        assertEquals(1, counts!!.cancelled)
        assertTrue(result!!.contains("1 recovered"))
        assertEquals("test", readAt(listOf("nested", "éxample 文件.bin")))
        assertEquals(listOf("nested"), childNames(destination))
    }

    private fun runTree(): Pair<String, RecoveryCounts> {
        var result: String? = null
        var counts: RecoveryCounts? = null
        RecoveryRunner(resolver, executor).runTree(
            TestDocumentsProvider.sourceTreeUri(), destination, "provider-password".toCharArray(),
            onProgress = {}, onResult = { result = it }, onComplete = { counts = it }
        )
        assertTrue(result != null)
        return result!! to counts!!
    }

    private fun child(parent: android.net.Uri, name: String): android.net.Uri {
        val id = resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent)), arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }.first { it.second == name }.first
        }
        return DocumentsContract.buildDocumentUriUsingTree(parent, id)
    }

    private fun childNames(parent: android.net.Uri): List<String> = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent)), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
    )!!.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) }.sorted() }

    private fun readAt(parts: List<String>): String {
        var current = destination
        parts.dropLast(1).forEach { current = child(current, it) }
        val file = child(current, parts.last())
        return resolver.openInputStream(file)!!.bufferedReader().use { it.readText() }
    }
}
