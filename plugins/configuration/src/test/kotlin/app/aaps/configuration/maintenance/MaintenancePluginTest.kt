package app.aaps.configuration.maintenance

import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`

class MaintenancePluginTest : TestBaseWithProfile() {

    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var fileListProvider: FileListProvider
    @Mock lateinit var uel: UserEntryLogger

    private lateinit var sut: MaintenancePlugin

    @BeforeEach
    fun mock() {
        sut = MaintenancePlugin(context, rh, preferences, nsSettingsStatus, aapsLogger, config, fileListProvider, loggerUtils, uel)
        `when`(loggerUtils.suffix).thenReturn(".log.zip")
        `when`(loggerUtils.logDirectory).thenReturn("src/test/assets/logger")
        // Unknown solution after scoped access
        //`when`(fileListProvider.ensureTempDirExists()).thenReturn(File("src/test/assets/logger"))
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

    // Unknown solution after scoped access
    // @Test
    // fun zipLogsTest() {
    //     val logs = sut.getLogFiles(2)
    //     val name = "AndroidAPS.log.zip"
    //     var zipFile = File("build/$name")
    //     zipFile = sut.zipLogs(zipFile, logs)
    //     assertThat(zipFile.exists()).isTrue()
    //     assertThat(zipFile.isFile).isTrue()
    // }

    @Test
    fun preferenceScreenTest() {
        val screen = preferenceManager.createPreferenceScreen(context)
        sut.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}
