package app.aaps.pump.equil.driver.definition

import app.aaps.pump.equil.R
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class EquilHistoryEntryGroupTest : TestBaseWithProfile() {

    @Test
    fun `all enum values should be accessible`() {
        val allGroups = EquilHistoryEntryGroup.entries
        assertEquals(5, allGroups.size)
        assert(allGroups.contains(EquilHistoryEntryGroup.All))
        assert(allGroups.contains(EquilHistoryEntryGroup.Pair))
        assert(allGroups.contains(EquilHistoryEntryGroup.Bolus))
        assert(allGroups.contains(EquilHistoryEntryGroup.Basal))
        assert(allGroups.contains(EquilHistoryEntryGroup.Configuration))
    }

    @Test
    fun `valueOf should return correct enum`() {
        assertEquals(EquilHistoryEntryGroup.All, EquilHistoryEntryGroup.valueOf("All"))
        assertEquals(EquilHistoryEntryGroup.Pair, EquilHistoryEntryGroup.valueOf("Pair"))
        assertEquals(EquilHistoryEntryGroup.Bolus, EquilHistoryEntryGroup.valueOf("Bolus"))
        assertEquals(EquilHistoryEntryGroup.Basal, EquilHistoryEntryGroup.valueOf("Basal"))
        assertEquals(EquilHistoryEntryGroup.Configuration, EquilHistoryEntryGroup.valueOf("Configuration"))
    }

    @Test
    fun `all groups should have resource IDs`() {
        assertEquals(R.string.equil_history_group_all, EquilHistoryEntryGroup.All.resourceId)
        assertEquals(R.string.equil_history_group_pair, EquilHistoryEntryGroup.Pair.resourceId)
        assertEquals(R.string.equil_history_group_bolus, EquilHistoryEntryGroup.Bolus.resourceId)
        assertEquals(R.string.equil_history_group_basal, EquilHistoryEntryGroup.Basal.resourceId)
        assertEquals(R.string.equil_history_group_configuration, EquilHistoryEntryGroup.Configuration.resourceId)
    }

    @Test
    fun `getTranslatedList should return all groups`() {
        // Mock resource helper to return translated strings
        whenever(rh.gs(R.string.equil_history_group_all)).thenReturn("All")
        whenever(rh.gs(R.string.equil_history_group_pair)).thenReturn("Pairing")
        whenever(rh.gs(R.string.equil_history_group_bolus)).thenReturn("Boluses")
        whenever(rh.gs(R.string.equil_history_group_basal)).thenReturn("Basal Rates")
        whenever(rh.gs(R.string.equil_history_group_configuration)).thenReturn("Config")

        val translatedList = EquilHistoryEntryGroup.getTranslatedList(rh)

        assertEquals(5, translatedList.size)
        assert(translatedList.contains(EquilHistoryEntryGroup.All))
        assert(translatedList.contains(EquilHistoryEntryGroup.Pair))
        assert(translatedList.contains(EquilHistoryEntryGroup.Bolus))
        assert(translatedList.contains(EquilHistoryEntryGroup.Basal))
        assert(translatedList.contains(EquilHistoryEntryGroup.Configuration))
    }

    @Test
    fun `toString should return translated string`() {
        whenever(rh.gs(R.string.equil_history_group_all)).thenReturn("All")
        whenever(rh.gs(R.string.equil_history_group_pair)).thenReturn("Pairing")
        whenever(rh.gs(R.string.equil_history_group_bolus)).thenReturn("Boluses")
        whenever(rh.gs(R.string.equil_history_group_basal)).thenReturn("Basal Rates")
        whenever(rh.gs(R.string.equil_history_group_configuration)).thenReturn("Config")

        EquilHistoryEntryGroup.getTranslatedList(rh)

        assertEquals("All", EquilHistoryEntryGroup.All.toString())
        assertEquals("Pairing", EquilHistoryEntryGroup.Pair.toString())
        assertEquals("Boluses", EquilHistoryEntryGroup.Bolus.toString())
        assertEquals("Basal Rates", EquilHistoryEntryGroup.Basal.toString())
        assertEquals("Config", EquilHistoryEntryGroup.Configuration.toString())
    }

    @Test
    fun `enum ordinal values should be stable`() {
        assertEquals(0, EquilHistoryEntryGroup.All.ordinal)
        assertEquals(1, EquilHistoryEntryGroup.Pair.ordinal)
        assertEquals(2, EquilHistoryEntryGroup.Bolus.ordinal)
        assertEquals(3, EquilHistoryEntryGroup.Basal.ordinal)
        assertEquals(4, EquilHistoryEntryGroup.Configuration.ordinal)
    }

    @Test
    fun `resource IDs should be unique`() {
        val resourceIds = EquilHistoryEntryGroup.entries.map { it.resourceId }.toSet()
        assertEquals(5, resourceIds.size, "All resource IDs should be unique")
    }
}
