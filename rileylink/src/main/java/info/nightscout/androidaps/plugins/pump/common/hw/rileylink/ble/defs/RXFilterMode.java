package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

/**
 * Created by andy on 21/05/2018.
 */

public enum RXFilterMode {

    Wide(0x50), //
    Narrow(0x90) //
    ;

    public byte value;


    RXFilterMode(int value) {
        this.value = (byte)value;
    }
}
