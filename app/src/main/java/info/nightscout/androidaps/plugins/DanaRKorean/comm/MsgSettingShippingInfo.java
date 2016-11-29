package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingShippingInfo extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingShippingInfo.class);

    public MsgSettingShippingInfo() {
        SetCommand(0x3207);
    }

    public void handleMessage(byte[] bytes) {
        DanaRKoreanPlugin.getDanaRPump().serialNumber = stringFromBuff(bytes, 0, 10);
        DanaRKoreanPlugin.getDanaRPump().shippingDate = dateFromBuff(bytes, 10);
        DanaRKoreanPlugin.getDanaRPump().shippingCountry = asciiStringFromBuff(bytes, 13, 3);
        if (DanaRKoreanPlugin.getDanaRPump().shippingDate.getTime() > new Date(116, 4, 1).getTime()) {
            DanaRKoreanPlugin.getDanaRPump().isNewPump = true;
        } else
            DanaRKoreanPlugin.getDanaRPump().isNewPump = false;
        if (Config.logDanaMessageDetail) {
            log.debug("Serial number: " + DanaRKoreanPlugin.getDanaRPump().serialNumber);
            log.debug("Shipping date: " + DanaRKoreanPlugin.getDanaRPump().shippingDate);
            log.debug("Shipping country: " + DanaRKoreanPlugin.getDanaRPump().shippingCountry);
        }
    }
}

