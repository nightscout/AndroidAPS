package app.aaps.pump.common.hw.rileylink.service

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersionBase
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import app.aaps.pump.common.hw.rileylink.data.RLHistoryItem
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkTargetDevice
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by andy on 16/05/2018.
 */
@Singleton
class RileyLinkServiceData @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rileyLinkUtil: RileyLinkUtil,
    private val rxBus: RxBus
) {

    var tuneUpDone = false
    var rileyLinkError: RileyLinkError? = null
    var rileyLinkServiceState: RileyLinkServiceState = RileyLinkServiceState.NotStarted
        private set
    var lastServiceStateChange = 0L
        private set

    // here we have "compatibility level" version
    var firmwareVersion: RileyLinkFirmwareVersionBase? = null
    var rileyLinkTargetFrequency: RileyLinkTargetFrequency = RileyLinkTargetFrequency.NotSet
    var rileyLinkAddress: String? = null
    var rileyLinkName: String? = null
    var batteryLevel: Int? = null
    var showBatteryLevel = false
    var lastTuneUpTime = 0L
    var lastGoodFrequency: Double? = null

    // bt version
    var versionBLE113: String? = null

    // radio version
    var versionCC110: String? = null

    // orangeLink
    var isOrange = false
    var versionOrangeFirmware: String? = null
    var versionOrangeHardware: String? = null
    lateinit var targetDevice: RileyLinkTargetDevice

    // Medtronic Pump
    var pumpID: String? = null
    var pumpIDBytes: ByteArray = byteArrayOf(0, 0, 0)

    fun setPumpID(pumpId: String?, pumpIdBytes: ByteArray) {
        pumpID = pumpId
        pumpIDBytes = pumpIdBytes
    }

    @Synchronized
    fun setServiceState(newState: RileyLinkServiceState, errorCode: RileyLinkError? = null) {
        rileyLinkServiceState = newState
        lastServiceStateChange = System.currentTimeMillis()
        rileyLinkError = errorCode
        aapsLogger.info(LTag.PUMP, String.format(Locale.ENGLISH, "RileyLink State Changed: $newState - Error State: ${errorCode?.name}"))
        rileyLinkUtil.rileyLinkHistory.add(RLHistoryItem(rileyLinkServiceState, errorCode, targetDevice))
        rxBus.send(EventRileyLinkDeviceStatusChange(targetDevice, newState, errorCode))
    }
}