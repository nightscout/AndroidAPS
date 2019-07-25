package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.data;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.RileyLinkCommunicationException;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command.RileyLinkCommand;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RFSpyRLResponse;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpyResponse {

    // 0xaa == timeout
    // 0xbb == interrupted
    // 0xcc == zero-data
    // 0xdd == success
    // 0x11 == invalidParam
    // 0x22 == unknownCommand

    protected byte[] raw;
    protected RadioResponse radioResponse;
    private RileyLinkCommand command;


    public RFSpyResponse() {
        init(new byte[0]);
    }


    public RFSpyResponse(byte[] bytes) {
        init(bytes);
    }


    public RFSpyResponse(RileyLinkCommand command, byte[] rawResponse) {

        this.command = command;
        init(rawResponse);
    }


    public void init(byte[] bytes) {
        if (bytes == null) {
            raw = new byte[0];
        } else {
            raw = bytes;
        }

    }


    public RadioResponse getRadioResponse() throws RileyLinkCommunicationException {
        if (looksLikeRadioPacket()) {
            radioResponse = new RadioResponse(command);
            radioResponse.init(raw);
        } else {
            radioResponse = new RadioResponse();
        }
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


    public boolean isInvalidParam() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0x11) {
                return true;
            }
        }
        return false;
    }


    public boolean isUnknownCommand() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0x22) {
                return true;
            }
        }
        return false;
    }


    public boolean isOK() {
        if ((raw.length == 1) || (raw.length == 2)) {
            if (raw[0] == (byte)0x01 || raw[0] == (byte)0xDD) {
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


    @Override
    public String toString() {
        if (raw.length > 2) {
            return "Radio packet";
        } else {
            RFSpyRLResponse r = RFSpyRLResponse.fromByte(raw[0]);
            return r.toString();
        }
    }


    public byte[] getRaw() {
        return raw;
    }
}
