package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.data;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RFSpyCommand;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpyResponse {

    // 0xaa == timeout
    // 0xbb == interrupted
    // 0xcc == zero-data
    protected byte[] raw;
    protected RadioResponse radioResponse;
    private RFSpyCommand command;


    public RFSpyResponse() {
        init(new byte[0]);
    }


    public RFSpyResponse(byte[] bytes) {
        init(bytes);
    }


    public RFSpyResponse(RFSpyCommand command, byte[] rawResponse) {

        this.command = command;
        init(rawResponse);
    }


    public void init(byte[] bytes) {
        if (bytes == null) {
            raw = new byte[0];
        } else {
            raw = bytes;
        }

        if (looksLikeRadioPacket()) {
            radioResponse = new RadioResponse(command, raw);
        } else {
            radioResponse = new RadioResponse();
        }
    }


    public RadioResponse getRadioResponse() {
        return radioResponse;
    }


    public boolean wasTimeout() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0xaa) {
                return true;
            }
        }
        return false;
    }


    public boolean wasInterrupted() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0xbb) {
                return true;
            }
        }
        return false;
    }


    public boolean isOK() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0x01) {
                return true;
            }
        }
        return false;
    }


    public boolean looksLikeRadioPacket() {
        if (raw.length > 2) {
            return true;
        }
        return false;
    }


    public byte[] getRaw() {
        return raw;
    }
}
