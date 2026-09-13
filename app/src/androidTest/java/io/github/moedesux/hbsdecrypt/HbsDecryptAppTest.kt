package io.github.moedesux.hbsdecrypt

import androidx.activity.ComponentActivity
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class HbsDecryptAppTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun startRequiresInputs() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test fun rendersProgress() {
        compose.setContent { HbsDecryptApp(RecoveryState(running = true, bytesRead = 42), "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Progress: 42 bytes read").assertIsDisplayed()
    }

    @Test fun rendersCompletion() {
        compose.setContent { HbsDecryptApp(RecoveryState(outcome = "Recovered: sample (4 bytes)"), "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Result: Recovered: sample (4 bytes)").assertIsDisplayed()
    }

    @Test fun collisionPolicyDefaultsToSkip() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Collision policy: Skip existing (default)").assertIsDisplayed()
    }

    @Test fun passwordInputEnablesPasswordRequirementButNotMissingSelections() {
        compose.setContent { HbsDecryptApp(RecoveryState(), "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Password").performTextInput("secret")
        compose.onNodeWithText("Start").assertIsNotEnabled()
    }

    @Test fun startEnablesWhenAllInputsArePresent() {
        val state = RecoveryState(Uri.parse("content://source"), Uri.parse("content://destination"), passwordPresent = true)
        compose.setContent { HbsDecryptApp(state, "", {}, {}, {}, {}, {}) }
        compose.onNodeWithText("Start").assertIsEnabled()
    }
}
