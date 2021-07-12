package info.nightscout.androidaps.plugins.pump.insight.app_layer.status;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.pump.insight.ids.CartridgeTypeIDs;
import info.nightscout.androidaps.plugins.pump.insight.ids.SymbolStatusIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class GetCartridgeStatusMessage extends AppLayerMessage {

    private CartridgeStatus cartridgeStatus;

    public GetCartridgeStatusMessage() {
        super(MessagePriority.NORMAL, false, false, Service.STATUS);
    }

    @Override
    protected void parse(ByteBuf byteBuf) {
        cartridgeStatus = new CartridgeStatus();
        cartridgeStatus.setInserted(byteBuf.readBoolean());
        cartridgeStatus.setCartridgeType(CartridgeTypeIDs.IDS.getType(byteBuf.readUInt16LE()));
        cartridgeStatus.setSymbolStatus(SymbolStatusIDs.IDS.getType(byteBuf.readUInt16LE()));
        cartridgeStatus.setRemainingAmount(byteBuf.readUInt16Decimal());
    }

    public CartridgeStatus getCartridgeStatus() {
        return this.cartridgeStatus;
    }
}
