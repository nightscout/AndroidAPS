package app.aaps.configuration.maintenance

import android.content.Context
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.LoggerUtils
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.Preferences
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File

class MaintenancePluginTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var config: Config
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var fileListProvider: FileListProvider

    private lateinit var sut: MaintenancePlugin

    @BeforeEach
    fun mock() {
        sut = MaintenancePlugin(context, rh, preferences, nsSettingsStatus, aapsLogger, config, fileListProvider, loggerUtils)
        `when`(loggerUtils.suffix).thenReturn(".log.zip")
        `when`(loggerUtils.logDirectory).thenReturn("src/test/assets/logger")
        `when`(fileListProvider.ensureTempDirExists()).thenReturn(File("src/test/assets/logger"))
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

    @Test
    fun zipLogsTest() {
        val logs = sut.getLogFiles(2)
        val name = "AndroidAPS.log.zip"
        var zipFile = File("build/$name")
        zipFile = sut.zipLogs(zipFile, logs)
        assertThat(zipFile.exists()).isTrue()
        assertThat(zipFile.isFile).isTrue()
    }
}
