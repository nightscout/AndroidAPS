package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;

/**
 * Created by mike on 05.07.2016.
 */
public class MsgSettingShippingInfo extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgSettingShippingInfo.class);

    public MsgSettingShippingInfo() {
        SetCommand(0x3207);
    }

    public void handleMessage(byte[] bytes) {
        DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();
        pump.serialNumber = stringFromBuff(bytes, 0, 10);
        pump.shippingDate = dateFromBuff(bytes, 10);
        pump.shippingCountry = asciiStringFromBuff(bytes, 13, 3);
        if (pump.shippingDate.getTime() > new Date(116, 4, 1).getTime()) {
            pump.isNewPump = true;
        } else
            pump.isNewPump = false;
        if (Config.logDanaMessageDetail) {
            log.debug("Serial number: " + pump.serialNumber);
            log.debug("Shipping date: " + pump.shippingDate);
            log.debug("Shipping country: " + pump.shippingCountry);
        }
    }
}

