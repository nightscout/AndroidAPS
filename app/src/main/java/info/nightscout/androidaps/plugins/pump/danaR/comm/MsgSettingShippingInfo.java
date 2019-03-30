package info.nightscout.androidaps.plugins.pump.danaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingShippingInfo extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(L.PUMPCOMM);

    public MsgSettingShippingInfo() {
        SetCommand(0x3207);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("New message");
    }

    public void handleMessage(byte[] bytes) {
        DanaRPump pump = DanaRPump.getInstance();
        pump.serialNumber = stringFromBuff(bytes, 0, 10);
        pump.shippingDate = dateFromBuff(bytes, 10);
        pump.shippingCountry = asciiStringFromBuff(bytes, 13, 3);
        if (L.isEnabled(L.PUMPCOMM)) {
            log.debug("Serial number: " + pump.serialNumber);
            log.debug("Shipping date: " + pump.shippingDate);
            log.debug("Shipping country: " + pump.shippingCountry);
        }
    }
}

