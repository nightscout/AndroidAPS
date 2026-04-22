package app.aaps.pump.omnipod.common.bledriver.comm.legacy

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.ConnectException
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.device.BleDeviceManager
import app.aaps.pump.omnipod.common.bledriver.comm.interfaces.scan.PodScanner
import app.aaps.pump.omnipod.common.bledriver.comm.legacy.scan.PodScanner as LegacyPodScanner
import app.aaps.pump.omnipod.common.keys.DashBooleanPreferenceKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyBleDeviceManager @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : BleDeviceManager {

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    override fun ensureBondedIfRequired(podAddress: String): Boolean {
        if (!preferences.get(DashBooleanPreferenceKey.UseBonding)) return true
        val adapter = bluetoothAdapter ?: return false
        val device = adapter.getRemoteDevice(podAddress)
        if (device.bondState != android.bluetooth.BluetoothDevice.BOND_NONE) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) {
            val result = device.createBond()
            aapsLogger.debug(LTag.PUMPBTCOMM, "Bonding with pod resulted $result")
            Thread.sleep(10000)
        }
        return true
    }

    override fun removeBond(podAddress: String) {
        try {
            if (!preferences.get(DashBooleanPreferenceKey.UseBonding) ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val adapter = bluetoothAdapter
            if (adapter == null) {
                aapsLogger.error(LTag.PUMPBTCOMM, "removeBond: Bluetooth not available, MAC address not found")
                return
            }
            val device = adapter.getRemoteDevice(podAddress)
            val removeBondMethod = device.javaClass.getMethod("removeBond")
            val result = removeBondMethod.invoke(device)
            aapsLogger.debug(LTag.PUMPBTCOMM, "Remove bond resulted $result")
        } catch (t: Throwable) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Unpairing device with address $podAddress failed with error $t")
        }
    }

    override fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    override fun createPodScanner(): PodScanner {
        val adapter = bluetoothAdapter ?: throw ConnectException("Bluetooth not available")
        return LegacyPodScanner(aapsLogger, adapter)
    }
}
