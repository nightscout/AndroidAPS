package app.aaps.implementation.protection

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.BooleanKey
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.shared.tests.AAPSLoggerTest
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.TestBaseWithProfile
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import dagger.android.HasAndroidInjector


import org.junit.BeforeClass
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class ExportPasswordDataStoreImplTest: TestBaseWithProfile() {

    private val somePassword = "somePassword"

    val sut: ExportPasswordDataStoreImpl by lazy {
        ExportPasswordDataStoreImpl(aapsLogger, sp, config)
    }

    @Test
    fun exportPasswordStoreEnabled() {
        // When disabled
        `when`(sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)).thenReturn(false)
        assertFalse(sut.exportPasswordStoreEnabled())

        // When enabled
        `when`(sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)).thenReturn(true)
        assertTrue(sut.exportPasswordStoreEnabled())
        assertTrue(sut.clearPasswordDataStore(context).isEmpty())

        // These will fail to run (can not instantiate secure encrypt?)
        // assertTrue(sut.putPasswordToDataStore(context, somePassword) == somePassword)
        // assertTrue(sut.getPasswordFromDataStore(context) == Triple ("", true, true))
    }

    @Test
    fun clearPasswordDataStore() {
        `when`(sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)).thenReturn(false)
        assertTrue(sut.clearPasswordDataStore(context).isEmpty())
    }

    @Test
    fun putPasswordToDataStore() {
        `when`(sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)).thenReturn(false)
        assertTrue(sut.putPasswordToDataStore(context, somePassword) == somePassword)
    }

    @Test
    fun getPasswordFromDataStore() {
        `when`(sp.getBoolean(BooleanKey.MaintenanceEnableExportSettingsAutomation.key, false)).thenReturn(false)
        assertTrue(sut.getPasswordFromDataStore(context) == Triple ("", true, true))
    }
}