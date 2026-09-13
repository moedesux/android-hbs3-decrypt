package io.github.moedesux.hbsdecrypt

import android.content.ContentResolver
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecoveryProviderTest {
    private val resolver: ContentResolver = InstrumentationRegistry.getInstrumentation().context.contentResolver
    private val executor = Executor { it.run() }
    private val tree = DocumentsContract.buildDocumentUriUsingTree(
        DocumentsContract.buildTreeDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID),
        TestDocumentsProvider.ROOT_ID
    )
    private val source = DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.SOURCE_ID)

    @Before fun setUp() {
        check(Looper.myLooper() == null || Looper.myLooper() != Looper.getMainLooper())
        TestDocumentsProvider.installFixture(resolver)
    }

    @Test fun recoversThroughProviderAndCleansUpTemporaryDocument() {
        val result = runRecovery()
        assertEquals("Recovered: recovered.bin (4 bytes)", result)
        assertEquals("test", read("recovered.bin"))
        assertEquals(listOf("recovered.bin"), childNames())
    }

    @Test fun skipsExistingDestinationWithoutTouchingIt() {
        TestDocumentsProvider.installExisting(resolver, "recovered.bin", "keep me")
        assertEquals("Skipped: recovered.bin", runRecovery())
        assertEquals("keep me", read("recovered.bin"))
        assertEquals(listOf("recovered.bin"), childNames())
    }

    @Test fun failedRecoveryRemovesTemporaryOutput() {
        TestDocumentsProvider.installInvalidSource(resolver)
        assertTrue(runRecovery().startsWith("Failed:"))
        assertTrue(childNames().isEmpty())
    }

    @Test fun replacesExistingDestinationOnlyAfterSuccessfulRecovery() {
        TestDocumentsProvider.installExisting(resolver, "recovered.bin", "replace me")
        assertEquals("Recovered: recovered.bin (4 bytes)", runRecovery(CollisionPolicy.REPLACE))
        assertEquals("test", read("recovered.bin"))
        assertEquals(listOf("recovered.bin"), childNames())
    }

    @Test fun failedReplacementPreservesExistingDestination() {
        TestDocumentsProvider.installExisting(resolver, "recovered.bin", "keep me")
        TestDocumentsProvider.installInvalidSource(resolver)
        assertTrue(runRecovery(CollisionPolicy.REPLACE).startsWith("Failed:"))
        assertEquals("keep me", read("recovered.bin"))
        assertEquals(listOf("recovered.bin"), childNames())
    }

    private fun runRecovery(policy: CollisionPolicy = CollisionPolicy.SKIP): String {
        var outcome: String? = null
        val latch = CountDownLatch(1)
        RecoveryRunner(resolver, executor).run(source, tree, "provider-password".toCharArray(), policy = policy, onProgress = {}, onResult = { outcome = it; latch.countDown() })
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        return outcome!!
    }

    private fun childNames(): List<String> = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, TestDocumentsProvider.ROOT_ID),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
    )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun read(name: String): String {
        val id = resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )!!.use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) to cursor.getString(1) else null }
                .first { it.second == name }
                .first
        }
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))!!
            .bufferedReader().use { it.readText() }
    }
}
