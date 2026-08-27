package ir.mtlink.client

import org.junit.Assert.assertEquals
import org.junit.Test

class TestSettingsDefaultsTest {
    @Test
    fun normalizesTestTimeoutToSupportedOptions() {
        assertEquals(3, MTLinkStore.normalizeTestTimeout(3))
        assertEquals(5, MTLinkStore.normalizeTestTimeout(5))
        assertEquals(8, MTLinkStore.normalizeTestTimeout(8))
        assertEquals(5, MTLinkStore.normalizeTestTimeout(6))
    }

    @Test
    fun normalizesConcurrencyToSupportedOptions() {
        assertEquals(4, MTLinkStore.normalizeTestConcurrency(4))
        assertEquals(8, MTLinkStore.normalizeTestConcurrency(8))
        assertEquals(12, MTLinkStore.normalizeTestConcurrency(12))
        assertEquals(8, MTLinkStore.normalizeTestConcurrency(10))
    }

    @Test
    fun usesRequestedDefaultsForNewPreferences() {
        val preferences = AppPreferences()
        assertEquals(AppLanguage.EN, preferences.language)
        assertEquals(AppTheme.SYSTEM, preferences.theme)
        assertEquals(5, preferences.testTimeoutSeconds)
        assertEquals(8, preferences.testConcurrency)
    }
}
