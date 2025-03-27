package app.aaps.implementation.protection

import app.aaps.core.keys.BooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`

class ExportPasswordDataStoreImplTest : TestBaseWithProfile() {

    private val somePassword = "somePassword"

    val sut: ExportPasswordDataStoreImpl by lazy {
        ExportPasswordDataStoreImpl(aapsLogger, preferences, config)
    }

    @Test
    fun exportPasswordStoreEnabled() {
        // When disabled
        `when`(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(false)
        assertFalse(sut.exportPasswordStoreEnabled())

        // When enabled
        `when`(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(true)
        assertTrue(sut.exportPasswordStoreEnabled())
        assertTrue(sut.clearPasswordDataStore(context).isEmpty())

        // These will fail to run (can not instantiate secure encrypt?)
        // assertTrue(sut.putPasswordToDataStore(context, somePassword) == somePassword)
        // assertTrue(sut.getPasswordFromDataStore(context) == Triple ("", true, true))
    }

    @Test
    fun clearPasswordDataStore() {
        `when`(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(false)
        assertTrue(sut.clearPasswordDataStore(context).isEmpty())
    }

    @Test
    fun putPasswordToDataStore() {
        `when`(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(false)
        assertTrue(sut.putPasswordToDataStore(context, somePassword) == somePassword)
    }

    @Test
    fun getPasswordFromDataStore() {
        `when`(preferences.get(BooleanKey.MaintenanceEnableExportSettingsAutomation)).thenReturn(false)
        assertTrue(sut.getPasswordFromDataStore(context) == Triple("", true, true))
    }
}