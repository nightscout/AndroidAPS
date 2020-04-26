package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBasalRate;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.ActiveBasalProfileIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetActiveBasalRateMessage extends AppLayerMessage {

    private ActiveBasalRate activeBasalRate;

    public GetActiveBasalRateMessage() {
        super(MessagePriority.NORMAL, true, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        ActiveBasalRate activeBasalRate = new ActiveBasalRate();
        activeBasalRate.setActiveBasalProfile(ActiveBasalProfileIDs.IDS.getType(byteBuf.readUInt16LE()));
        activeBasalRate.setActiveBasalProfileName(byteBuf.readUTF16(30));
        activeBasalRate.setActiveBasalRate(byteBuf.readUInt16Decimal());
        if (activeBasalRate.getActiveBasalProfile() != null) this.activeBasalRate = activeBasalRate;
    }

    public ActiveBasalRate getActiveBasalRate() {
        return this.activeBasalRate;
    }
}
