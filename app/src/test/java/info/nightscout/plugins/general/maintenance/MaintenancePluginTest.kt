package info.nightscout.plugins.general.maintenance

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dagger.android.HasAndroidInjector
import info.nightscout.configuration.maintenance.MaintenancePlugin
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.logging.LoggerUtils
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.interfaces.nsclient.NSSettingsStatus
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import java.io.File

class MaintenancePluginTest : TestBase() {

    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var context: Context
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var sp: SP
    @Mock lateinit var nsSettingsStatus: NSSettingsStatus
    @Mock lateinit var config: Config
    @Mock lateinit var loggerUtils: LoggerUtils
    @Mock lateinit var fileListProvider: PrefFileListProvider

    private lateinit var sut: MaintenancePlugin

    @BeforeEach
    fun mock() {
        sut = MaintenancePlugin(injector, context, rh, sp, nsSettingsStatus, aapsLogger, config, fileListProvider, loggerUtils)
        `when`(loggerUtils.suffix).thenReturn(".log.zip")
        `when`(loggerUtils.logDirectory).thenReturn("src/test/res/logger")
        `when`(fileListProvider.ensureTempDirExists()).thenReturn(File("src/test/res/logger"))
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
