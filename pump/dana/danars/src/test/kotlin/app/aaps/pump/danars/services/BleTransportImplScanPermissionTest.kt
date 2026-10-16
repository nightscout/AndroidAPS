package app.aaps.pump.danars.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import app.aaps.core.interfaces.logging.AAPSLogger
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The pair wizard scan must not run without BLUETOOTH_SCAN. Android then throws a SecurityException
 * from `BluetoothLeScanner.startScan`, and the app ends (Crashlytics, 4.0.0-beta1).
 */
internal class BleTransportImplScanPermissionTest {

    private val context: Context = mock()
    private val aapsLogger: AAPSLogger = mock()
    private val bluetoothAdapter: BluetoothAdapter = mock()
    private val sut = BleTransportImpl(context, aapsLogger, bluetoothAdapter)

    @Test
    fun `startScan does not touch the scanner without the scan permission`() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_DENIED)

        sut.scanner.startScan()

        verify(bluetoothAdapter, never()).bluetoothLeScanner
    }

    @Test
    fun `startScan uses the scanner when the scan permission is granted`() {
        whenever(context.checkPermission(eq(Manifest.permission.BLUETOOTH_SCAN), any(), any())).thenReturn(PackageManager.PERMISSION_GRANTED)

        sut.scanner.startScan()

        verify(bluetoothAdapter).bluetoothLeScanner
    }
}
