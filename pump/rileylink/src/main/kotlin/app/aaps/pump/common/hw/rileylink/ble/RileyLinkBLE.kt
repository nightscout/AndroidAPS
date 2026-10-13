package app.aaps.pump.common.hw.rileylink.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.core.utils.pump.ThreadUtil
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes
import app.aaps.pump.common.hw.rileylink.ble.device.OrangeLinkImpl
import app.aaps.pump.common.hw.rileylink.ble.operations.BLECommOperation
import app.aaps.pump.common.hw.rileylink.ble.operations.BLECommOperationResult
import app.aaps.pump.common.hw.rileylink.ble.operations.CharacteristicReadOperation
import app.aaps.pump.common.hw.rileylink.ble.operations.CharacteristicWriteOperation
import app.aaps.pump.common.hw.rileylink.ble.operations.DescriptorWriteOperation
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import org.apache.commons.lang3.StringUtils
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by geoff on 5/26/16.
 * Added: State handling, configuration of RF for different configuration ranges, connection handling
 */
@Singleton
class RileyLinkBLE @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val rileyLinkUtil: RileyLinkUtil,
    private val preferences: Preferences,
    private val orangeLink: OrangeLinkImpl,
    private val config: Config
) {

    private val gattDebugEnabled = true
    private var manualDisconnect = false

    //val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val bluetoothAdapter: BluetoothAdapter? get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private val bluetoothGattCallback: BluetoothGattCallback
    var rileyLinkDevice: BluetoothDevice? = null
    private var bluetoothConnectionGatt: BluetoothGatt? = null
    private var mCurrentOperation: BLECommOperation? = null
    private val gattOperationSema = Semaphore(1, true)
    private var radioResponseCountNotified: Runnable? = null
    var isConnected = false
        private set

    @Inject fun onInit() {
        //aapsLogger.debug(LTag.PUMPBTCOMM, "BT Adapter: " + this.bluetoothAdapter);
        orangeLink.rileyLinkBLE = this
    }

    private fun isAnyRileyLinkServiceFound(service: BluetoothGattService): Boolean {
        val found = GattAttributes.isRileyLink(service.uuid)
        if (found) return true
        else
            for (serviceI in service.includedServices) {
                if (isAnyRileyLinkServiceFound(serviceI)) return true
                orangeLink.checkIsOrange(serviceI.uuid)
            }
        return false
    }

    fun debugService(service: BluetoothGattService, indentCount: Int, stringBuilder: StringBuilder) {
        val indentString = StringUtils.repeat(' ', indentCount)
        if (gattDebugEnabled) {
            val uuidServiceString = service.uuid.toString()

            stringBuilder.append(indentString)
            stringBuilder.append(GattAttributes.lookup(uuidServiceString, "Unknown service"))
            stringBuilder.append(" ($uuidServiceString)")
            for (character in service.characteristics) {
                val uuidCharacteristicString = character.uuid.toString()
                stringBuilder.append("\n    ")
                stringBuilder.append(indentString)
                stringBuilder.append(" - " + GattAttributes.lookup(uuidCharacteristicString, "Unknown Characteristic"))
                stringBuilder.append(" ($uuidCharacteristicString)")
            }
            stringBuilder.append("\n\n")

            //aapsLogger.warn(LTag.PUMPBTCOMM, stringBuilder.toString());
            for (serviceI in service.includedServices) {
                debugService(serviceI, indentCount + 4, stringBuilder)
            }
        }
    }

    fun registerRadioResponseCountNotification(notifier: Runnable?) {
        radioResponseCountNotified = notifier
    }

    @SuppressLint("MissingPermission")
    fun discoverServices(): Boolean {
        // shouldn't happen, but if it does we exit
        bluetoothConnectionGatt ?: return false

        return if (bluetoothConnectionGatt?.discoverServices() == true) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Starting to discover GATT Services.")
            true
        } else {
            aapsLogger.error(LTag.PUMPBTCOMM, "Cannot discover GATT Services.")
            false
        }
    }

    fun enableNotifications(): Boolean {
        val result = setNotificationBlocking(UUID.fromString(GattAttributes.SERVICE_RADIO), UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))
        if (result.resultCode != BLECommOperationResult.RESULT_SUCCESS) {
            aapsLogger.error(LTag.PUMPBTCOMM, "Error setting response count notification")
            return false
        }
        return if (rileyLinkServiceData.isOrange) orangeLink.enableNotifications()
        else true
    }

    fun findRileyLink(rileyLinkAddress: String) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "RileyLink address: $rileyLinkAddress")
        // Must verify that this is a valid MAC, or crash.
        //macAddress = RileyLinkAddress;
        val useScanning = preferences.get(RileylinkBooleanPreferenceKey.OrangeUseScanning)
        if (useScanning) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Start scan for OrangeLink device.")
            orangeLink.startScan()
        } else {
            rileyLinkDevice = bluetoothAdapter?.getRemoteDevice(rileyLinkAddress)
            // if this succeeds, we get a connection state change callback?
            if (rileyLinkDevice != null) connectGattInternal()
            else aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device not found with address: $rileyLinkAddress")
        }
    }

    fun connectGatt() {
        val useScanning = preferences.get(RileylinkBooleanPreferenceKey.OrangeUseScanning)
        if (useScanning) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Start scan for OrangeLink device.")
            orangeLink.startScan()
        } else {
            connectGattInternal()
        }
    }

    // This function must be run on UI thread.
    @SuppressLint("HardwareIds")
    fun connectGattInternal() {
        if (rileyLinkDevice == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "RileyLink device is null, can't do connectGatt.")
            return
        }
        if (config.PUMPDRIVERS && ContextCompat.checkSelfPermission(context, "android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "no permission")
            return
        } else bluetoothConnectionGatt = rileyLinkDevice?.connectGatt(context, true, bluetoothGattCallback)
        // , BluetoothDevice.TRANSPORT_LE
        if (bluetoothConnectionGatt == null)
            aapsLogger.error(LTag.PUMPBTCOMM, "Failed to connect to Bluetooth Low Energy device at " + bluetoothAdapter?.address)
        else {
            if (gattDebugEnabled) aapsLogger.debug(LTag.PUMPBTCOMM, "Gatt Connected.")
            bluetoothConnectionGatt?.device?.name?.let { deviceName ->
                // Update stored name upon connecting (also for backwards compatibility for device where a name was not yet stored)
                if (StringUtils.isNotEmpty(deviceName)) preferences.put(RileyLinkStringKey.Name, deviceName)
                else preferences.remove(RileyLinkStringKey.Name)
                rileyLinkServiceData.rileyLinkName = deviceName
                rileyLinkServiceData.rileyLinkAddress = bluetoothConnectionGatt?.device?.address
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        isConnected = false
        aapsLogger.warn(LTag.PUMPBTCOMM, "Closing GATT connection")
        // Close old connection
        if (bluetoothConnectionGatt != null) {
            // Not sure if to disconnect or to close first..
            bluetoothConnectionGatt?.disconnect()
            manualDisconnect = true
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        bluetoothConnectionGatt?.close()
        bluetoothConnectionGatt = null
    }

    @SuppressLint("MissingPermission")
    fun setNotificationBlocking(serviceUUID: UUID?, charaUUID: UUID?): BLECommOperationResult {
        val retValue = BLECommOperationResult()
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "setNotification_blocking: not configured!")
            retValue.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
            return retValue
        }
        gattOperationSema.acquire()
        SystemClock.sleep(1) // attempting to yield thread, to make sequence of events easier to follow
        if (mCurrentOperation != null) retValue.resultCode = BLECommOperationResult.RESULT_BUSY
        else {
            if (bluetoothConnectionGatt?.getService(serviceUUID) == null) {
                // Catch if the service is not supported by the BLE device
                retValue.resultCode = BLECommOperationResult.RESULT_NONE
                aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported")
                // TODO: 11/07/2016 UI update for user
                // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
            } else {
                bluetoothConnectionGatt?.let { bluetoothConnectionGatt ->
                    val chara = bluetoothConnectionGatt.getService(serviceUUID)?.getCharacteristic(charaUUID) ?: return retValue.apply { resultCode = BLECommOperationResult.RESULT_NONE }
                    // Tell Android that we want the notifications
                    bluetoothConnectionGatt.setCharacteristicNotification(chara, true)
                    val list = chara.descriptors
                    if (list.isNotEmpty()) {
                        if (gattDebugEnabled) for (i in list.indices) aapsLogger.debug(LTag.PUMPBTCOMM, "Found descriptor: " + list[i].toString())
                        // Tell the remote device to send the notifications
                        mCurrentOperation = DescriptorWriteOperation(aapsLogger, bluetoothConnectionGatt, list[0], BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        mCurrentOperation?.execute(this)
                        when {
                            mCurrentOperation?.timedOut == true    -> retValue.resultCode = BLECommOperationResult.RESULT_TIMEOUT
                            mCurrentOperation?.interrupted == true -> retValue.resultCode = BLECommOperationResult.RESULT_INTERRUPTED
                            else                                   -> retValue.resultCode = BLECommOperationResult.RESULT_SUCCESS
                        }
                    } else return retValue.apply { resultCode = BLECommOperationResult.RESULT_NONE }
                }
            }
            mCurrentOperation = null
            gattOperationSema.release()
        }
        return retValue
    }

    // call from main
    fun writeCharacteristicBlocking(serviceUUID: UUID, charaUUID: UUID, value: ByteArray): BLECommOperationResult {
        val retValue = BLECommOperationResult()
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "writeCharacteristic_blocking: not configured!")
            retValue.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
            return retValue
        }
        retValue.value = value
        gattOperationSema.acquire()
        SystemClock.sleep(1) // attempting to yield thread, to make sequence of events easier to follow
        if (mCurrentOperation != null) retValue.resultCode = BLECommOperationResult.RESULT_BUSY
        else {
            if (bluetoothConnectionGatt?.getService(serviceUUID) == null) {
                // Catch if the service is not supported by the BLE device
                // GGW: Tue Jul 12 01:14:01 UTC 2016: This can also happen if the
                // app that created the bluetoothConnectionGatt has been destroyed/created,
                // e.g. when the user switches from portrait to landscape.
                retValue.resultCode = BLECommOperationResult.RESULT_NONE
                aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported")
                // TODO: 11/07/2016 UI update for user
                // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
            } else {
                bluetoothConnectionGatt?.let { bluetoothConnectionGatt ->
                    val chara = bluetoothConnectionGatt.getService(serviceUUID)?.getCharacteristic(charaUUID) ?: return retValue.apply { resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED }
                    mCurrentOperation = CharacteristicWriteOperation(aapsLogger, bluetoothConnectionGatt, chara, value)
                    mCurrentOperation?.execute(this)
                    when {
                        mCurrentOperation?.timedOut == true    -> retValue.resultCode = BLECommOperationResult.RESULT_TIMEOUT
                        mCurrentOperation?.interrupted == true -> retValue.resultCode = BLECommOperationResult.RESULT_INTERRUPTED
                        else                                   -> retValue.resultCode = BLECommOperationResult.RESULT_SUCCESS
                    }
                }
            }
            mCurrentOperation = null
            gattOperationSema.release()
        }
        return retValue
    }

    fun readCharacteristicBlocking(serviceUUID: UUID?, charaUUID: UUID?): BLECommOperationResult {
        val retValue = BLECommOperationResult()
        if (bluetoothConnectionGatt == null) {
            aapsLogger.error(LTag.PUMPBTCOMM, "readCharacteristic_blocking: not configured!")
            retValue.resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED
            return retValue
        }

        gattOperationSema.acquire()
        SystemClock.sleep(1) // attempting to yield thread, to make sequence of events easier to follow
        if (mCurrentOperation != null) retValue.resultCode = BLECommOperationResult.RESULT_BUSY
        else {
            if (bluetoothConnectionGatt?.getService(serviceUUID) == null) {
                // Catch if the service is not supported by the BLE device
                retValue.resultCode = BLECommOperationResult.RESULT_NONE
                aapsLogger.error(LTag.PUMPBTCOMM, "BT Device not supported")
                // TODO: 11/07/2016 UI update for user
                // xyz rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter);
            } else {
                val chara = bluetoothConnectionGatt?.getService(serviceUUID)?.getCharacteristic(charaUUID) ?: return retValue.apply { resultCode = BLECommOperationResult.RESULT_NOT_CONFIGURED }
                mCurrentOperation = CharacteristicReadOperation(aapsLogger, bluetoothConnectionGatt!!, chara)
                mCurrentOperation?.execute(this)
                when {
                    mCurrentOperation?.timedOut == true    -> retValue.resultCode = BLECommOperationResult.RESULT_TIMEOUT
                    mCurrentOperation?.interrupted == true -> retValue.resultCode = BLECommOperationResult.RESULT_INTERRUPTED

                    else                                   -> {
                        retValue.resultCode = BLECommOperationResult.RESULT_SUCCESS
                        retValue.value = mCurrentOperation?.value
                    }
                }
            }
        }
        mCurrentOperation = null
        gattOperationSema.release()

        return retValue
    }

    private fun getGattStatusMessage(status: Int): String =
        when (status) {
            BluetoothGatt.GATT_SUCCESS             -> "SUCCESS"
            BluetoothGatt.GATT_FAILURE             -> "FAILED"
            BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "NOT PERMITTED"
            133                                    -> "Found the strange 133 bug"
            else                                   -> "UNKNOWN ($status)"
        }

    init {
        //orangeLink.rileyLinkBLE = this;
        bluetoothGattCallback = object : BluetoothGattCallback() {
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)
                if (gattDebugEnabled) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}onCharacteristicChanged ${GattAttributes.lookup(characteristic.uuid)} ${ByteUtil.getHex(characteristic.value)}")
                    if (characteristic.uuid == UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))
                        aapsLogger.debug(LTag.PUMPBTCOMM, "Response Count is " + ByteUtil.shortHexString(characteristic.value))
                }
                if (characteristic.uuid == UUID.fromString(GattAttributes.CHARA_RADIO_RESPONSE_COUNT))
                    radioResponseCountNotified?.run()
                orangeLink.onCharacteristicChanged(characteristic, characteristic.value)
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicRead(gatt, characteristic, status)
                val statusMessage = getGattStatusMessage(status)
                if (gattDebugEnabled)
                    aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}onCharacteristicRead (${GattAttributes.lookup(characteristic.uuid)}) $statusMessage:${ByteUtil.getHex(characteristic.value)}")
                mCurrentOperation?.gattOperationCompletionCallback(characteristic.uuid, characteristic.value)
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                super.onCharacteristicWrite(gatt, characteristic, status)
                val uuidString = GattAttributes.lookup(characteristic.uuid)
                if (gattDebugEnabled)
                    aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}onCharacteristicWrite ${getGattStatusMessage(status)} $uuidString ${ByteUtil.shortHexString(characteristic.value)}")
                mCurrentOperation?.gattOperationCompletionCallback(characteristic.uuid, characteristic.value)
            }

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                // https://github.com/NordicSemiconductor/puck-central-android/blob/master/PuckCentral/app/src/main/java/no/nordicsemi/puckcentral/bluetooth/gatt/GattManager.java#L117
                if (status == 133) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Got the status 133 bug, closing gatt")
                    disconnect()
                    SystemClock.sleep(500)
                    return
                }
                if (gattDebugEnabled) {
                    val stateMessage: String = when (newState) {
                        BluetoothProfile.STATE_CONNECTED     -> "CONNECTED"
                        BluetoothProfile.STATE_CONNECTING    -> "CONNECTING"
                        BluetoothProfile.STATE_DISCONNECTED  -> "DISCONNECTED"
                        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                        else                                 -> "UNKNOWN newState ($newState)"
                    }

                    aapsLogger.warn(LTag.PUMPBTCOMM, "onConnectionStateChange " + getGattStatusMessage(status) + " " + stateMessage)
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (status == BluetoothGatt.GATT_SUCCESS) rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.BluetoothConnected)
                    else aapsLogger.debug(LTag.PUMPBTCOMM, "BT State connected, GATT status $status (${getGattStatusMessage(status)})")
                } else if (newState == BluetoothProfile.STATE_CONNECTING || newState == BluetoothProfile.STATE_DISCONNECTING) {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "We are in ${if (status == BluetoothProfile.STATE_CONNECTING) "Connecting" else "Disconnecting"} state.")
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkDisconnected)
                    if (manualDisconnect) close()
                    aapsLogger.warn(LTag.PUMPBTCOMM, "RileyLink Disconnected.")
                } else {
                    aapsLogger.warn(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Some other state: (status=%d, newState=%d)", status, newState))
                }
            }

            @Suppress("DEPRECATION")
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                super.onDescriptorWrite(gatt, descriptor, status)
                if (gattDebugEnabled)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorWrite ${GattAttributes.lookup(descriptor.uuid)} ${getGattStatusMessage(status)} written: ${ByteUtil.getHex(descriptor.value)}")
                mCurrentOperation?.gattOperationCompletionCallback(descriptor.uuid, descriptor.value)
            }

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                super.onDescriptorRead(gatt, descriptor, status)
                mCurrentOperation?.gattOperationCompletionCallback(descriptor.uuid, descriptor.value)
                if (gattDebugEnabled)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onDescriptorRead " + getGattStatusMessage(status) + " status " + descriptor)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                super.onMtuChanged(gatt, mtu, status)
                if (gattDebugEnabled)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onMtuChanged $mtu status $status")
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                super.onReadRemoteRssi(gatt, rssi, status)
                if (gattDebugEnabled)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi)
            }

            override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
                super.onReliableWriteCompleted(gatt, status)
                if (gattDebugEnabled)
                    aapsLogger.warn(LTag.PUMPBTCOMM, "onReliableWriteCompleted status $status")
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services
                    var rileyLinkFound = false
                    orangeLink.resetOrangeLinkData()
                    val stringBuilder = StringBuilder("RileyLink Device Debug\n")
                    for (service in services) {
                        val uuidService = service.uuid
                        if (isAnyRileyLinkServiceFound(service)) {
                            rileyLinkFound = true
                        }
                        if (gattDebugEnabled) {
                            debugService(service, 0, stringBuilder)
                        }
                        orangeLink.checkIsOrange(uuidService)
                    }
                    if (gattDebugEnabled) {
                        aapsLogger.warn(LTag.PUMPBTCOMM, stringBuilder.toString())
                        aapsLogger.warn(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status))
                    }
                    aapsLogger.info(LTag.PUMPBTCOMM, "Gatt device is RileyLink device: $rileyLinkFound")
                    if (rileyLinkFound) {
                        isConnected = true
                        rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkReady)
                    } else {
                        isConnected = false
                        rileyLinkServiceData.setServiceState(
                            RileyLinkServiceState.RileyLinkError,
                            RileyLinkError.DeviceIsNotRileyLink
                        )
                    }
                } else {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "onServicesDiscovered " + getGattStatusMessage(status))
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.Intents.RileyLinkGattFailed)
                }
            }
        }
    }
}