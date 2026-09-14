package io.github.moedesux.hbsdecrypt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val sourceFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { takeSource(it, SourceKind.FILE) } }
    private val sourceFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { takeSource(it, SourceKind.FOLDER) } }
    private val destinationPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(::takeDestination) }
    private var state by mutableStateOf(RecoveryState()); private var source: Uri? = null; private var destination: Uri? = null; private var password by mutableStateOf(""); private var cancelRequested = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { HbsDecryptApp(state, password, { sourceFilePicker.launch(arrayOf("*/*")) }, { sourceFolderPicker.launch(null) }, { destinationPicker.launch(null) }, { password = it; state = state.copy(passwordPresent = it.isNotEmpty()) }, { state = state.copy(collisionPolicy = it) }, ::start, ::cancel) } }
    private fun takeSource(uri: Uri, kind: SourceKind) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); source = uri; state = state.copy(source = uri, sourceKind = kind, sourceName = displayName(uri)) }
    private fun takeDestination(uri: Uri) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); destination = uri; state = state.copy(destination = uri, destinationName = displayName(uri)) }
    private fun displayName(uri: Uri): String? { val document = if (uri.pathSegments.contains("document")) uri else DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri)); return contentResolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }
    private fun start() { val s = source ?: return; val d = destination ?: return; cancelRequested = false; state = state.copy(running = true, outcome = null, bytesRead = 0, counts = RecoveryCounts()); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); val runner = RecoveryRunner(contentResolver, Executors.newSingleThreadExecutor()); val complete: (String) -> Unit = { result -> runOnUiThread { password = ""; state = state.copy(running = false, passwordPresent = false, outcome = result); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }; val updateCounts: (RecoveryCounts) -> Unit = { counts -> runOnUiThread { state = state.copy(counts = counts) } }; if (state.sourceKind == SourceKind.FILE) runner.run(s, d, password.toCharArray(), policy = state.collisionPolicy, cancellation = DecryptionCancellation { cancelRequested }, onProgress = { runOnUiThread { state = state.copy(bytesRead = it) } }, onResult = complete, onComplete = updateCounts) else runner.runTree(s, d, password.toCharArray(), policy = state.collisionPolicy, cancellation = DecryptionCancellation { cancelRequested }, onProgress = { runOnUiThread { state = state.copy(bytesRead = it) } }, onResult = complete, onComplete = updateCounts) }
    private fun cancel() { cancelRequested = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HbsDecryptApp(state: RecoveryState, password: String, pickSourceFile: () -> Unit, pickSourceFolder: () -> Unit, pickDestination: () -> Unit, setPassword: (String) -> Unit, setPolicy: (CollisionPolicy) -> Unit, start: () -> Unit, cancel: () -> Unit) { MaterialTheme { Scaffold(topBar = { TopAppBar(title = { Text("HBS Decrypt") }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Button(pickSourceFile, enabled = !state.running) { Text("Choose encrypted file") }; if (state.sourceKind == SourceKind.FILE) state.sourceName?.let { Text("Encrypted file: $it") }; Button(pickSourceFolder, enabled = !state.running) { Text("Choose encrypted folder") }; if (state.sourceKind == SourceKind.FOLDER) state.sourceName?.let { Text("Encrypted folder: $it") }; Button(pickDestination, enabled = !state.running) { Text("Choose output folder") }; state.destinationName?.let { Text("Output folder: $it") }; OutlinedTextField(password, setPassword, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = !state.running); Text(if (state.collisionPolicy == CollisionPolicy.SKIP) "Collision policy: Skip existing (default)" else "Collision policy: Replace existing"); CollisionPolicy.values().forEach { policy -> Row { RadioButton(policy == state.collisionPolicy, { setPolicy(policy) }, enabled = !state.running); Text(if (policy == CollisionPolicy.SKIP) "Skip existing" else "Replace existing") } }; Button(start, enabled = state.canStart) { Text("Start") }; if (state.running) Button(cancel) { Text("Cancel") }; Text(if (state.running) "Progress: ${state.bytesRead} bytes read" else "Progress: waiting to start"); Text("Recovered: ${state.counts.decrypted}, skipped: ${state.counts.skipped}, unsupported: ${state.counts.unsupported}, cancelled: ${state.counts.cancelled}, failed: ${state.counts.failed}"); Text("Valid padding is not proof that the password or recovered data is correct."); state.outcome?.let { Text("Result: $it") } } } } }

@Composable
fun HbsDecryptApp(state: RecoveryState, password: String, pickSourceFile: () -> Unit, pickSourceFolder: () -> Unit, pickDestination: () -> Unit, setPassword: (String) -> Unit, start: () -> Unit, cancel: () -> Unit) =
    HbsDecryptApp(state, password, pickSourceFile, pickSourceFolder, pickDestination, setPassword, {}, start, cancel)
