package info.nightscout.androidaps.plugins.DanaR.comm;

public class MsgPCCommStart extends DanaRMessage {
    public MsgPCCommStart() {
        SetCommand(0x3001);
    }
}
