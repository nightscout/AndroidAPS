package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingShippingInfo extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingShippingInfo.class);

    public MsgSettingShippingInfo() {
        SetCommand(0x3207);
    }

    public void handleMessage(byte[] bytes) {
        DanaRPlugin.getDanaRPump().serialNumber = stringFromBuff(bytes, 0, 10);
        DanaRPlugin.getDanaRPump().shippingDate = dateFromBuff(bytes, 10);
        DanaRPlugin.getDanaRPump().shippingCountry = asciiStringFromBuff(bytes, 13, 3);
        if (DanaRPlugin.getDanaRPump().shippingDate.getTime() > new Date(116, 4, 1).getTime()) {
            DanaRPlugin.getDanaRPump().isNewPump = true;
        } else
            DanaRPlugin.getDanaRPump().isNewPump = false;
        if (Config.logDanaMessageDetail) {
            log.debug("Serial number: " + DanaRPlugin.getDanaRPump().serialNumber);
            log.debug("Shipping date: " + DanaRPlugin.getDanaRPump().shippingDate);
            log.debug("Shipping country: " + DanaRPlugin.getDanaRPump().shippingCountry);
        }
    }
}

