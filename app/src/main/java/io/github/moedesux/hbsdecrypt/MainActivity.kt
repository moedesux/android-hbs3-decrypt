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
    private val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::takeSource) }
    private val destinationPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(::takeDestination) }
    private var state by mutableStateOf(RecoveryState()); private var source: Uri? = null; private var destination: Uri? = null; private var password = ""
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { HbsDecryptApp(state, { sourcePicker.launch(arrayOf("*/*")) }, { destinationPicker.launch(null) }, { password = it; state = state.copy(passwordPresent = it.isNotEmpty()) }, ::start) } }
    private fun takeSource(uri: Uri) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); source = uri; state = state.copy(source = uri) }
    private fun takeDestination(uri: Uri) { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); destination = uri; state = state.copy(destination = uri) }
    private fun start() { val s = source ?: return; val d = destination ?: return; state = state.copy(running = true, outcome = null); window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); RecoveryRunner(contentResolver, Executors.newSingleThreadExecutor()).run(s, d, password.toCharArray(), onProgress = { runOnUiThread { state = state.copy(bytesRead = it) } }, onResult = { runOnUiThread { password = ""; state = state.copy(running = false, passwordPresent = false, outcome = it); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } }) }
}

@Composable fun HbsDecryptApp(state: RecoveryState, pickSource: () -> Unit, pickDestination: () -> Unit, setPassword: (String) -> Unit, start: () -> Unit) { MaterialTheme { Scaffold(topBar = { TopAppBar(title = { Text("HBS Decrypt") }) }) { padding -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Button(pickSource, enabled = !state.running) { Text(if (state.source == null) "Choose encrypted source" else "Source selected") }; Button(pickDestination, enabled = !state.running) { Text(if (state.destination == null) "Choose output folder" else "Destination selected") }; var entered by remember { mutableStateOf("") }; OutlinedTextField(entered, { entered = it; setPassword(it) }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = !state.running); Text("Collision policy: Skip existing (default)"); Button(start, enabled = state.canStart) { Text("Start") }; Text(if (state.running) "Progress: ${state.bytesRead} bytes read" else "Progress: waiting to start"); state.outcome?.let { Text("Result: $it") } } } } }
