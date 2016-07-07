package info.nightscout.androidaps.plugins.DanaR.comm;

public class MsgPCCommStop extends DanaRMessage {
    public MsgPCCommStop() {
        SetCommand(0x3002);
    }
}
