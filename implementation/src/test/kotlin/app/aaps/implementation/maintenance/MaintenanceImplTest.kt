package app.aaps.implementation.maintenance

import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.implementation.maintenance.MaintenanceImpl
import app.aaps.implementation.maintenance.cloud.CloudStorageManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class MaintenanceImplTest : TestBaseWithProfile() {

    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var fileListProvider: FileListProvider
    @Mock lateinit var cloudStorageManager: CloudStorageManager
    @Mock lateinit var sp: SP

    private lateinit var sut: MaintenanceImpl

    @BeforeEach
    fun mock() {
        sut = MaintenanceImpl(context, rh, preferences, nsSettingsStatus, aapsLogger, config, fileListProvider, loggerUtils, cloudStorageManager, sp)
        whenever(loggerUtils.suffix).thenReturn(".log.zip")
        whenever(loggerUtils.logDirectory).thenReturn("src/test/assets/logger")
    }

    @Test fun logFilesTest() {
        var logs = sut.getLogFiles(2)
        assertThat(logs.map { it.name }).containsExactly(
            "AndroidAPS.log",
            "AndroidAPS.2018-01-03_01-01-00.1.zip",
        ).inOrder()
        logs = sut.getLogFiles(10)
        assertThat(logs).hasSize(4)
    }
}
