package io.github.moedesux.hbsdecrypt

import androidx.activity.ComponentActivity
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HbsDecryptAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun startRequiresInputs() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test fun rendersProgress() {
        compose.setContent { HbsDecryptApp(RecoveryState(running = true, bytesRead = 42), "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Progress: 42 bytes read").assertIsDisplayed()
    }

    @Test fun rendersCompletion() {
        compose.setContent { HbsDecryptApp(RecoveryState(outcome = "Recovered: sample (4 bytes)"), "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Result: Recovered: sample (4 bytes)").assertIsDisplayed()
    }

    @Test fun collisionPolicyDefaultsToSkip() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Collision policy: Skip existing (default)").assertIsDisplayed()
    }

    @Test fun passwordInputPreservesEveryTypedCharacter() {
        var password by mutableStateOf("")
        compose.setContent { HbsDecryptApp(RecoveryState(), password, {}, {}, {}, { password = it }, {}, {}) }
        compose.onNodeWithText("Password").performTextInput("secret")
        compose.runOnIdle { assertEquals("secret", password) }
    }

    @Test fun startEnablesWhenAllInputsArePresent() {
        val state = RecoveryState(Uri.parse("content://source"), Uri.parse("content://destination"), passwordPresent = true)
        compose.setContent { HbsDecryptApp(state, "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Start").assertIsEnabled()
    }

    @Test fun offersBothFileAndFolderSourceSelection() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Choose encrypted file").assertIsDisplayed()
        compose.onNodeWithText("Choose encrypted folder").assertIsDisplayed()
    }

    @Test fun showsNamesOfSelectedInputAndOutput() {
        val state = RecoveryState(
            source = Uri.parse("content://source"), destination = Uri.parse("content://destination"),
            sourceKind = SourceKind.FILE, sourceName = "backup.hbs", destinationName = "Recovered"
        )
        compose.setContent { HbsDecryptApp(state, "", {}, {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Encrypted file: backup.hbs").assertIsDisplayed()
        compose.onNodeWithText("Output folder: Recovered").assertIsDisplayed()
    }
}

class MainActivityPasswordTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun passwordFieldRendersEveryTypedCharacter() {
        compose.onNodeWithText("Password").performTextInput("secret")
        compose.onNodeWithText("Password").assertTextEquals("secret")
    }
}
