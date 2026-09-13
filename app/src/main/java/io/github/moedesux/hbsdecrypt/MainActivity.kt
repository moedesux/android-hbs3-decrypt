package io.github.moedesux.hbsdecrypt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HbsDecryptApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HbsDecryptApp() {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("HBS Decrypt") }) }) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SelectionPlaceholder("Source", "Choose encrypted source")
                SelectionPlaceholder("Destination", "Choose output folder")
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    placeholder = { Text("Password will be requested here") },
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = false,
                )
                Text("Collision policy")
                Text("Keep both (placeholder)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false) { Text("Start") }
                    Button(onClick = {}, enabled = false) { Text("Cancel") }
                }
                Text("Progress: waiting to start")
                Text("Results: no recovery has run")
            }
        }
    }
}

@Composable
private fun SelectionPlaceholder(label: String, action: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Button(onClick = {}, enabled = false) { Text(action) }
    }
}
