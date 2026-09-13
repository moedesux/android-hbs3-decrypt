package io.github.moedesux.hbsdecrypt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(::takeSource) }
    private val destinationPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(::takeDestination) }
    private var state by mutableStateOf(RecoveryState()); private var source: Uri? = null; private var destination: Uri? = null; private var password = ""; private var cancelRequested = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { HbsDecryptApp(state, password, { sourcePicker.launch(null) }, { destinationPicker.launch(null) }, { password = it; state = state.copy(passwordPresent = it.isNotEmpty()) }, { state = state.copy(collisionPolicy = it) }, ::start, ::cancel) } }
    private fun takeSource(uri: Uri) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); source = uri; state = state.copy(source = uri) }
    private fun takeDestination(uri: Uri) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); destination = uri; state = state.copy(destination = uri) }
    private fun start() { val s = source ?: return; val d = destination ?: return; cancelRequested = false; state = state.copy(running = true, outcome = null, bytesRead = 0); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); RecoveryRunner(contentResolver, Executors.newSingleThreadExecutor()).runTree(s, d, password.toCharArray(), policy = state.collisionPolicy, cancellation = DecryptionCancellation { cancelRequested }, onProgress = { runOnUiThread { state = state.copy(bytesRead = it) } }, onResult = { runOnUiThread { password = ""; state = state.copy(running = false, passwordPresent = false, outcome = it); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }) }
    private fun cancel() { cancelRequested = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HbsDecryptApp(state: RecoveryState, password: String, pickSource: () -> Unit, pickDestination: () -> Unit, setPassword: (String) -> Unit, setPolicy: (CollisionPolicy) -> Unit, start: () -> Unit, cancel: () -> Unit) { MaterialTheme { Scaffold(topBar = { TopAppBar(title = { Text("HBS Decrypt") }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Button(pickSource, enabled = !state.running) { Text(if (state.source == null) "Choose encrypted source" else "Source selected") }; Button(pickDestination, enabled = !state.running) { Text(if (state.destination == null) "Choose output folder" else "Destination selected") }; OutlinedTextField(password, setPassword, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = !state.running); Text("When output exists:"); CollisionPolicy.values().forEach { policy -> Row { RadioButton(policy == state.collisionPolicy, { setPolicy(policy) }, enabled = !state.running); Text(if (policy == CollisionPolicy.SKIP) "Skip existing" else "Replace existing") } }; Button(start, enabled = state.canStart) { Text("Start") }; if (state.running) Button(cancel) { Text("Cancel") }; Text(if (state.running) "Progress: ${state.bytesRead} bytes read" else "Progress: waiting to start"); Text("Valid padding is not proof that the password or recovered data is correct."); state.outcome?.let { Text("Result: $it") } } } } }

@Composable
fun HbsDecryptApp(state: RecoveryState, password: String, pickSource: () -> Unit, pickDestination: () -> Unit, setPassword: (String) -> Unit, start: () -> Unit, cancel: () -> Unit) =
    HbsDecryptApp(state, password, pickSource, pickDestination, setPassword, {}, start, cancel)
