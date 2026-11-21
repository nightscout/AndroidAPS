package app.aaps.pump.aaruy

import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.aaruy.database.HistoryUnitInfoDao
import app.aaps.pump.aaruy.emulator.AaruyEmulatorPeripheral
import app.aaps.pump.aaruy.keys.AaruyStringKey
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/** The Bluetooth v2 central of the driver tests: emulated, with [peripheral] bonded, as a paired pump is. */
fun centralFor(peripheral: AaruyEmulatorPeripheral): EmulatedBleCentral = EmulatedBleCentral().also { it.add(peripheral, bonded = true) }

/**
 * A mock of the pump log. The DAO is suspend now, and Mockito answers null for a suspend function that returns a number:
 * the driver then failed in its history job. A test can still stub a call for its own arguments.
 */
fun historyDaoMock(): HistoryUnitInfoDao = mock(HistoryUnitInfoDao::class.java).also {
    runBlocking {
        whenever(it.count(any(), any(), any())).thenReturn(0)
        whenever(it.maxId()).thenReturn(0)
        whenever(it.querySince(any())).thenReturn(emptyList())
    }
}

/** The pump's name is set when it is paired. The connect reads it for the PIN of the bond. */
fun Preferences.withEmulatedPumpName(): Preferences = also { whenever(it.get(AaruyStringKey.PumpName)).thenReturn(AaruyEmulatorPeripheral.DEVICE_NAME) }
