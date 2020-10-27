package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

/**
 * Created by andy on 22/05/2018.
 */

public enum RileyLinkCommandType {

    GetState(1), //
    GetVersion(2), //
    GetPacket(3), // aka Listen, receive
    Send(4), //
    SendAndListen(5), //
    UpdateRegister(6), //
    Reset(7), //
    Led(8),
    ReadRegister(9),
    SetModeRegisters(10),
    SetHardwareEncoding(11),
    SetPreamble(12),
    ResetRadioConfig(13),
    GetStatistics(14), ;

    public byte code;


    RileyLinkCommandType(int code) {
        this.code = (byte)code;
    }
}
