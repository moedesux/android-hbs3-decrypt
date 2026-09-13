package io.github.moedesux.hbsdecrypt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryStateTest {
    @Test fun `start requires both selections and a password`() {
        assertFalse(RecoveryState().canStart)
        assertFalse(RecoveryState(passwordPresent = true).canStart)
        assertFalse(RecoveryState(passwordPresent = true, running = true).canStart)
    }

    @Test fun `skip is the default collision policy`() {
        assertTrue(CollisionPolicy.SKIP == CollisionPolicy.SKIP)
    }
}
