package app.aaps.pump.common.hw.rileylink.service.tasks

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkConst
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkError
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkDoubleKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import javax.inject.Inject
import kotlin.math.roundToLong

/**
 * This class is intended to be run by the Service, for the Service. Not intended for clients to run.
 */
class InitializePumpManagerTask @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val rileyLinkUtil: RileyLinkUtil,
    activePlugin: ActivePlugin
) : ServiceTask(activePlugin) {

    override fun run() {
        if (!isRileyLinkDevice) return

        var lastGoodFrequency: Double
        if (rileyLinkServiceData.lastGoodFrequency == null) {
            lastGoodFrequency = preferences.get(RileyLinkDoubleKey.LastGoodDeviceFrequency)
            lastGoodFrequency = (lastGoodFrequency * 1000.0).roundToLong() / 1000.0
            rileyLinkServiceData.lastGoodFrequency = lastGoodFrequency
        } else lastGoodFrequency = rileyLinkServiceData.lastGoodFrequency ?: 0.0

        val rileyLinkCommunicationManager = pumpDevice?.rileyLinkService?.deviceCommunicationManager
        if (activePlugin.activePump.manufacturer() === ManufacturerType.Medtronic) {
            if (lastGoodFrequency > 0.0 && rileyLinkCommunicationManager?.isValidFrequency(lastGoodFrequency) == true) {
                rileyLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkReady)
                aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to $lastGoodFrequency MHz")
                rileyLinkCommunicationManager.setRadioFrequencyForPump(lastGoodFrequency)
                if (rileyLinkCommunicationManager.tryToConnectToDevice()) rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorReady)
                else {
                    rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorError, RileyLinkError.NoContactWithDevice)
                    rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump)
                }
            } else rileyLinkUtil.sendBroadcastMessage(RileyLinkConst.IPC.MSG_PUMP_tunePump)
        } else {
            if (!Round.isSame(lastGoodFrequency, RileyLinkTargetFrequency.Omnipod.scanFrequencies[0])) {
                lastGoodFrequency = RileyLinkTargetFrequency.Omnipod.scanFrequencies[0]
                lastGoodFrequency = (lastGoodFrequency * 1000.0).roundToLong() / 1000.0
                rileyLinkServiceData.lastGoodFrequency = lastGoodFrequency
            }
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.RileyLinkReady)
            rileyLinkServiceData.rileyLinkTargetFrequency = RileyLinkTargetFrequency.Omnipod // TODO shouldn't be needed
            aapsLogger.info(LTag.PUMPBTCOMM, "Setting radio frequency to $lastGoodFrequency MHz")
            rileyLinkCommunicationManager?.setRadioFrequencyForPump(lastGoodFrequency)
            rileyLinkServiceData.setServiceState(RileyLinkServiceState.PumpConnectorReady)
        }
    }
}