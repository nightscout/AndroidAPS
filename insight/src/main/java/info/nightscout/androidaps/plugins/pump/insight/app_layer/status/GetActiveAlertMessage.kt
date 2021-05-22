package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.Alert;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertCategory;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetActiveAlertMessage extends AppLayerMessage {

    private Alert alert;

    public GetActiveAlertMessage() {
        super(MessagePriority.NORMAL, true, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        Alert alert = new Alert();
        alert.setAlertId(byteBuf.readUInt16LE());
        alert.setAlertCategory(AlertCategory.Companion.fromId(byteBuf.readUInt16LE()));
        alert.setAlertType(AlertType.Companion.fromId(byteBuf.readUInt16LE()));
        alert.setAlertStatus(AlertStatus.Companion.fromId(byteBuf.readUInt16LE()));
        if (alert.getAlertType() != null) {
            switch (alert.getAlertType()) {
                case WARNING_38:
                    byteBuf.shift(4);
                    alert.setProgrammedBolusAmount(byteBuf.readUInt16Decimal());
                    alert.setDeliveredBolusAmount(byteBuf.readUInt16Decimal());
                    break;
                case REMINDER_07:
                case WARNING_36:
                    byteBuf.shift(2);
                    alert.setTBRAmount(byteBuf.readUInt16LE());
                    alert.setTBRDuration(byteBuf.readUInt16LE());
                    break;
                case WARNING_31:
                    alert.setCartridgeAmount(byteBuf.readUInt16Decimal());
                    break;
            }
        }
        if (alert.getAlertCategory() != null
                && alert.getAlertType() != null
                && alert.getAlertStatus() != null)
            this.alert = alert;
    }

    public Alert getAlert() {
        return this.alert;
    }
}
