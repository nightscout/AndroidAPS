package info.nightscout.androidaps.plugins.constraints.versionChecker

import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.junit.Assert
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

    @Test
    fun findByVersionTest() {
        val definition = AllowedVersions().generateSupportedVersions()
        assertNull(AllowedVersions().findByVersion(definition, "2.6.0"))
        assertTrue(AllowedVersions().findByVersion(definition, "2.9.0-beta1")?.has("endDate") ?: false)
        assertEquals("2021-11-07", AllowedVersions().findByVersion(definition, "2.9.0-beta1")?.getString("endDate"))
    }

    @Test
    fun endDateToMilliseconds() {
        val definition = AllowedVersions().generateSupportedVersions()
        val endDate = AllowedVersions().endDateToMilliseconds(AllowedVersions().findByVersion(definition, "2.9.0-beta1")?.getString("endDate") ?: "1000/01/01")
        val dateTime = LocalDate(endDate)
        assertEquals(2021, dateTime.year)
        assertEquals(11, dateTime.monthOfYear)
        assertEquals(7, dateTime.dayOfMonth)

        assertNull(AllowedVersions().endDateToMilliseconds("abdef"))
    }
}