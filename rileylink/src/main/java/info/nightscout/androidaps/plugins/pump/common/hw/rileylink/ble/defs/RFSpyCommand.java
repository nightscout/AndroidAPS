package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

/**
 * Created by andy on 22/05/2018.
 */

public enum RFSpyCommand {

    GetState(1), //
    GetVersion(2, false), //
    GetPacket(3), // aka Listen, receive
    Send(4), //
    SendAndListen(5), //
    UpdateRegister(6), //
    Reset(7), //

    ;

    public byte code;
    private boolean encoded = true;


    RFSpyCommand(int code) {
        this.code = (byte)code;
    }


    RFSpyCommand(int code, boolean encoded) {
        this.code = (byte)code;
        this.encoded = encoded;
    }


    public boolean isEncoded() {
        return encoded;
    }


    public void setEncoded(boolean encoded) {
        this.encoded = encoded;
    }
}
