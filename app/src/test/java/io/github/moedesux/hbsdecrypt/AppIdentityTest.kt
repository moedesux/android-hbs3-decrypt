package io.github.moedesux.hbsdecrypt

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun `build contract exposes the required application id`() {
        assertEquals("io.github.moedesux.hbsdecrypt", BuildConfig.APPLICATION_ID)
    }
}
