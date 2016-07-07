package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRFragment;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingShippingInfo extends DanaRMessage {
    private static Logger log = LoggerFactory.getLogger(MsgSettingShippingInfo.class);

    public MsgSettingShippingInfo() {
        SetCommand(0x3207);
    }

    public void handleMessage(byte[] bytes) {
        DanaRFragment.getDanaRPump().serialNumber = stringFromBuff(bytes, 0, 10);
        DanaRFragment.getDanaRPump().shippingDate = dateFromBuff(bytes, 10);
        DanaRFragment.getDanaRPump().shippingCountry = asciiStringFromBuff(bytes, 13, 3);
        if (Config.logDanaMessageDetail) {
            log.debug("Serial number: " + DanaRFragment.getDanaRPump().serialNumber);
            log.debug("Shipping date: " + DanaRFragment.getDanaRPump().shippingDate);
            log.debug("Shipping country: " + DanaRFragment.getDanaRPump().shippingCountry);
        }
    }
}

