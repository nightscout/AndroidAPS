package app.aaps.pump.common.hw.rileylink.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.defs.PumpDeviceState
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkCommunicationManager
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkDoubleKey
import dagger.android.DaggerService
import java.util.Locale
import javax.inject.Inject

/**
 * Created by andy on 5/6/18.
 * Split from original file and renamed.
 */
abstract class RileyLinkService : DaggerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rileyLinkUtil: RileyLinkUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rileyLinkBLE: RileyLinkBLE     // android-bluetooth management
    @Inject lateinit var rfSpy: RFSpy // interface for RL xxx Mhz radio.

    private val bluetoothAdapter: BluetoothAdapter? get() = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter
    private var broadcastReceiver: RileyLinkBroadcastReceiver? = null
    private var bluetoothStateReceiver: RileyLinkBluetoothStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        rileyLinkUtil.encoding = encoding
        initRileyLinkServiceData()
        broadcastReceiver = RileyLinkBroadcastReceiver()
        broadcastReceiver?.registerBroadcasts(this)
        bluetoothStateReceiver = RileyLinkBluetoothStateReceiver()
        bluetoothStateReceiver?.registerBroadcasts(this)
    }

    /**
     * Get Encoding for RileyLink communication
     */
    abstract val encoding: RileyLinkEncodingType

    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    abstract fun initRileyLinkServiceData()

    override fun onDestroy() {
        super.onDestroy()

        rileyLinkBLE.disconnect() // dispose of Gatt (disconnect and close)
        broadcastReceiver?.unregisterBroadcasts(this)
        bluetoothStateReceiver?.unregisterBroadcasts(this)
    }

    abstract val deviceCommunicationManager: RileyLinkCommunicationManager<*>

    // Here is where the wake-lock begins:
    // We've received a service startCommand, we grab the lock.
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int = START_STICKY

    fun bluetoothInit(): Boolean {
        aapsLogger.debug(LTag.PUMPBTCOMM, "bluetoothInit: attempting to get an adapter")
        rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothInitializing)
        if (bluetoothAdapter == null) {
            aapsLogger.error("Unable to obtain a BluetoothAdapter.")
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.NoBluetoothAdapter)
        } else {
            if (bluetoothAdapter?.isEnabled != true) {
                aapsLogger.error("Bluetooth is not enabled.")
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothError, RileyLinkError.BluetoothDisabled)
            } else {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothReady)
                return true
            }
        }
        return false
    }

    // returns true if our Rileylink configuration changed
    fun reconfigureRileyLink(deviceAddress: String): Boolean {
        rileyLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkInitializing)
        return if (rileyLinkBLE.isConnected) {
            if (deviceAddress == rileyLinkServiceData.rileyLinkAddress) {
                aapsLogger.info(LTag.PUMPBTCOMM, "No change to RL address.  Not reconnecting.")
                false
            } else {
                aapsLogger.warn(LTag.PUMPBTCOMM, "Disconnecting from old RL (${rileyLinkServiceData.rileyLinkAddress}), reconnecting to new: $deviceAddress")
                rileyLinkBLE.disconnect()
                // need to shut down listening thread too?
                // preferences.put(MedtronicConst.Prefs.RileyLinkAddress, deviceAddress);
                rileyLinkServiceData.rileyLinkAddress = deviceAddress
                rileyLinkBLE.findRileyLink(deviceAddress)
                true
            }
        } else {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Using RL $deviceAddress")
            if (rileyLinkServiceData.rileyLinkServiceState == RileyLinkServiceState.NotStarted) {
                if (!bluetoothInit()) {
                    aapsLogger.error("RileyLink can't get activated, Bluetooth is not functioning correctly. ${rileyLinkServiceData.rileyLinkError?.name ?: "Unknown error (null)"}")
                    return false
                }
            }
            rileyLinkBLE.findRileyLink(deviceAddress)
            true
        }
    }

    // FIXME: This needs to be run in a session so that is incorruptible, has a separate thread, etc.
    fun doTuneUpDevice() {
        rileyLinkServiceData.setServiceState(RileyLinkServiceState.TuneUpDevice)
        setPumpDeviceState(PumpDeviceState.Sleeping)
        val lastGoodFrequency = rileyLinkServiceData.lastGoodFrequency ?: preferences.get(RileyLinkDoubleKey.LastGoodDeviceFrequency)
        val newFrequency = deviceCommunicationManager.tuneForDevice()
        if (newFrequency != 0.0 && newFrequency != lastGoodFrequency) {
            aapsLogger.info(LTag.PUMPBTCOMM, String.format(Locale.ENGLISH, "Saving new pump frequency of %.3f MHz", newFrequency))
            preferences.put(RileyLinkDoubleKey.LastGoodDeviceFrequency, newFrequency)
            rileyLinkServiceData.lastGoodFrequency = newFrequency
            rileyLinkServiceData.tuneUpDone = true
            rileyLinkServiceData.lastTuneUpTime = System.currentTimeMillis()
        }
        if (newFrequency == 0.0) {
            // error tuning pump, pump not present ??
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError, RileyLinkError.TuneUpOfDeviceFailed)
        } else {
            deviceCommunicationManager.clearNotConnectedCount()
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorReady)
        }
    }

    abstract fun setPumpDeviceState(pumpDeviceState: PumpDeviceState)
    fun disconnectRileyLink() {
        if (rileyLinkBLE.isConnected) {
            rileyLinkBLE.disconnect()
            rileyLinkServiceData.rileyLinkAddress = null
            rileyLinkServiceData.rileyLinkName = null
        }
        rileyLinkServiceData.setServiceState(RileyLinkServiceState.BluetoothReady)
    }

    fun changeRileyLinkEncoding(encodingType: RileyLinkEncodingType) {
        rfSpy.setRileyLinkEncoding(encodingType)
    }

    fun verifyConfiguration(): Boolean {
        return verifyConfiguration(false)
    }

    abstract fun verifyConfiguration(forceRileyLinkAddressRenewal: Boolean): Boolean
}