package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError;

/**
 * Created by andy on 11/23/18.
 */

public class RileyLinkCommunicationException extends Exception {

    String extendedErrorText;
    private RileyLinkBLEError errorCode;


    public RileyLinkCommunicationException(RileyLinkBLEError errorCode, String extendedErrorText) {
        super(errorCode.getDescription());

        this.errorCode = errorCode;
        this.extendedErrorText = extendedErrorText;
    }


    public RileyLinkCommunicationException(RileyLinkBLEError errorCode) {
        super(errorCode.getDescription());

        this.errorCode = errorCode;
        // this.extendedErrorText = extendedErrorText;
    }

}
