package info.nightscout.androidaps.plugins.pump.insight.app_layer.status

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class GetFirmwareVersionsMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.STATUS) {

    var firmwareVersions: FirmwareVersions? = null
        private set

    override fun parse(byteBuf: ByteBuf?) {
        firmwareVersions = FirmwareVersions()
        firmwareVersions?.let {
            byteBuf?.run {
                it.releaseSWVersion = readASCII(13)
                it.uiProcSWVersion = readASCII(11)
                it.pcProcSWVersion = readASCII(11)
                it.mdTelProcSWVersion = readASCII(11)
                it.btInfoPageVersion = readASCII(11)
                it.safetyProcSWVersion = readASCII(11)
                it.configIndex = readUInt16LE()
                it.historyIndex = readUInt16LE()
                it.stateIndex = readUInt16LE()
                it.vocabularyIndex = readUInt16LE()
            }
        }
    }
}