package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetFirmwareVersionsMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    var firmwareVersions: FirmwareVersions? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        firmwareVersions = FirmwareVersions().apply {
            byteBuf.let {
                releaseSWVersion = it.readASCII(13)
                uiProcSWVersion = it.readASCII(11)
                pcProcSWVersion = it.readASCII(11)
                mdTelProcSWVersion = it.readASCII(11)
                btInfoPageVersion = it.readASCII(11)
                safetyProcSWVersion = it.readASCII(11)
                configIndex = it.readUInt16LE()
                historyIndex = it.readUInt16LE()
                stateIndex = it.readUInt16LE()
                vocabularyIndex = it.readUInt16LE()
            }
        }
    }
}