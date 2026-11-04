package app.aaps.pump.aaruy.services

import android.content.Context
import android.os.Looper
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.pump.DetailedBolusInfoStorage
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.TemporaryBasalStorage
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.AaruyPump
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.database.HistoryDailyUnitInfoDao
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorBleTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The driver's `AaruyBLE` on the Kotlin client and the emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AaruyBleReconnectTest {

    private fun waitFor(what: String, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 20_000
        while (!condition()) {
            assertTrue("Timeout waiting for: $what", System.currentTimeMillis() < end)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(20)
        }
    }

    /**
     * The client is still connected, because `disconnectDevice` does not drop the link. It reports CONNECTED at once
     * when asked to connect, and that must not be overwritten by CONNECTING.
     */
    @Test
    fun connectWhileTheLinkIsStillUpEndsConnected() {
        val preferences = mock(Preferences::class.java)
        val logger = mock(AAPSLogger::class.java)
        val pump = AaruyPump(
            logger,
            mock(ResourceHelper::class.java),
            preferences,
            mock(DateUtil::class.java),
            mock(PumpSync::class.java),
            mock(TemporaryBasalStorage::class.java),
            mock(DecimalFormatter::class.java)
        )
        val extraDir = mock(DocumentFile::class.java)
        `when`(extraDir.findFile("emulate_aaruy")).thenReturn(mock(DocumentFile::class.java))
        val files = mock(FileListProvider::class.java)
        `when`(files.ensureExtraDirExists()).thenReturn(extraDir)
        val ble = AaruyBLE(
            logger,
            RuntimeEnvironment.getApplication() as Context,
            pump,
            mock(DateUtil::class.java),
            mock(RxBus::class.java),
            preferences,
            mock(HistoryUnitInfoDao::class.java),
            mock(HistoryDailyUnitInfoDao::class.java),
            mock(DetailedBolusInfoStorage::class.java),
            mock(PumpSync::class.java),
            files
        )
        ble.startEmulatorIfRequested()

        ble.connect("first", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("first connection") { pump.connectionState == ConnectionState.CONNECTED && ble.isConnected }

        // This is what `disconnect` leaves behind: the driver thinks it is off, the client is still on.
        ble.isConnected = false

        ble.connect("retrying connection", AaruyEmulatorBleTransport.DEVICE_ADDRESS)
        waitFor("callbacks") { true }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertEquals(ConnectionState.CONNECTED, pump.connectionState)
        assertTrue("driver must know it is connected", ble.isConnected)
        assertEquals("not connecting", false, ble.isConnecting)
        ble.emulatorTransport?.shutdown()
    }
}
