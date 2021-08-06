package info.nightscout.androidaps.plugins.pump.insight.ids;

import info.nightscout.androidaps.plugins.pump.insight.satl.SatlMessage;
import info.nightscout.androidaps.plugins.pump.insight.utils.IDStorage;
import info.nightscout.androidaps.plugins.pump.insight.satl.ConnectionRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.ConnectionResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.DataMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.DisconnectMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.ErrorMessage;
import info.nightscout.androidaps.plugins.pump.insight.satl.KeyRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.KeyResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.SynAckResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.SynRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyConfirmRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyConfirmResponse;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyDisplayRequest;
import info.nightscout.androidaps.plugins.pump.insight.satl.VerifyDisplayResponse;

public class SatlCommandIDs {

    public static final IDStorage<Class<? extends SatlMessage>, Byte> IDS = new IDStorage<>();

    static {
        IDS.put(DataMessage.class, (byte) 3);
        IDS.put(ErrorMessage.class, (byte) 6);
        IDS.put(ConnectionRequest.class, (byte) 9);
        IDS.put(ConnectionResponse.class, (byte) 10);
        IDS.put(KeyRequest.class, (byte) 12);
        IDS.put(VerifyConfirmRequest.class, (byte) 14);
        IDS.put(KeyResponse.class, (byte) 17);
        IDS.put(VerifyDisplayRequest.class, (byte) 18);
        IDS.put(VerifyDisplayResponse.class, (byte) 20);
        IDS.put(SynRequest.class, (byte) 23);
        IDS.put(SynAckResponse.class, (byte) 24);
        IDS.put(DisconnectMessage.class, (byte) 27);
        IDS.put(VerifyConfirmResponse.class, (byte) 30);
    }

}
