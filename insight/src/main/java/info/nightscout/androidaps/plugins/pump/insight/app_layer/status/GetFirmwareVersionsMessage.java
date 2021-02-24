package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.FirmwareVersions;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetFirmwareVersionsMessage extends AppLayerMessage {

    private FirmwareVersions firmwareVersions;

    public GetFirmwareVersionsMessage() {
        super(MessagePriority.NORMAL, false, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        firmwareVersions = new FirmwareVersions();
        firmwareVersions.setReleaseSWVersion(byteBuf.readASCII(13));
        firmwareVersions.setUiProcSWVersion(byteBuf.readASCII(11));
        firmwareVersions.setPcProcSWVersion(byteBuf.readASCII(11));
        firmwareVersions.setMdTelProcSWVersion(byteBuf.readASCII(11));
        firmwareVersions.setBtInfoPageVersion(byteBuf.readASCII(11));
        firmwareVersions.setSafetyProcSWVersion(byteBuf.readASCII(11));
        firmwareVersions.setConfigIndex(byteBuf.readUInt16LE());
        firmwareVersions.setHistoryIndex(byteBuf.readUInt16LE());
        firmwareVersions.setStateIndex(byteBuf.readUInt16LE());
        firmwareVersions.setVocabularyIndex(byteBuf.readUInt16LE());
    }

    public FirmwareVersions getFirmwareVersions() {
        return this.firmwareVersions;
    }
}
