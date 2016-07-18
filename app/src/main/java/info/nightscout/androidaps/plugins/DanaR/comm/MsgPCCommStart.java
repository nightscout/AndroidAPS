package info.nightscout.androidaps.plugins.DanaR.comm;

public class MsgPCCommStart extends MessageBase {
    public MsgPCCommStart() {
        SetCommand(0x3001);
    }
}
