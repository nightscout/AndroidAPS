package info.nightscout.androidaps.plugins.constraints.versionChecker

import org.junit.Assert.*
import org.junit.Test

class AllowedVersionsTest {

    @Test
    fun generateSupportedVersionsTest() {
        val definition = AllowedVersions().generateSupportedVersions()
        assertNull(AllowedVersions().findByApi(definition, 0))
        assertFalse(AllowedVersions().findByApi(definition, 1)?.has("supported") ?: true)
        assertFalse(AllowedVersions().findByApi(definition, 23)?.has("supported") ?: true)
        assertEquals("2.6.2", AllowedVersions().findByApi(definition, 24)?.getString("supported"))
        assertEquals("2.6.2", AllowedVersions().findByApi(definition, 25)?.getString("supported"))
        assertEquals("2.8.2", AllowedVersions().findByApi(definition, 26)?.getString("supported"))
        assertEquals("2.8.2", AllowedVersions().findByApi(definition, 27)?.getString("supported"))
        assertEquals("2.8.2", AllowedVersions().findByApi(definition, 28)?.getString("supported"))
    }
}