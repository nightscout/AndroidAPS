package info.nightscout.androidaps.plugins.DanaR.comm;

public class MsgHistoryAllDone extends DanaRMessage {

    public MsgHistoryAllDone() {
        SetCommand(0x41F1);
    }

    @Override
    public void handleMessage(byte[] bytes) {
    }

}
