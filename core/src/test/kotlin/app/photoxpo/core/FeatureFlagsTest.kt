package app.photoxpo.core

import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureFlagsTest {
    @Test
    fun deleteEnabled_isFalse() {
        assertFalse(DELETE_ENABLED)
    }
}
