package info.nightscout.androidaps.plugins.DanaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.DanaRKorean.DanaRKoreanPump;

/**
 * Created by mike on 28.05.2016.
 */
public class MsgInitConnStatusBolus extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBolus.class);

    public MsgInitConnStatusBolus() {
        SetCommand(0x0302);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 < 13) {
            return;
        }
        DanaRKoreanPump pump = DanaRKoreanPlugin.getDanaRPump();
        int bolusConfig = intFromBuff(bytes, 0, 1);
        boolean deliveryPrime = (bolusConfig & DanaRKoreanPump.DELIVERY_PRIME) != 0;
        boolean deliveryStepBolus = (bolusConfig & DanaRKoreanPump.DELIVERY_STEP_BOLUS) != 0;
        boolean deliveryBasal = (bolusConfig & DanaRKoreanPump.DELIVERY_BASAL) != 0;
        boolean deliveryExtBolus = (bolusConfig & DanaRKoreanPump.DELIVERY_EXT_BOLUS) != 0;

        pump.bolusStep = intFromBuff(bytes, 1, 1) / 100d;
        pump.maxBolus = intFromBuff(bytes, 2, 2) / 100d;
        //int bolusRate = intFromBuff(bytes, 4, 8);
        int deliveryStatus = intFromBuff(bytes, 12, 1);

        if (Config.logDanaMessageDetail) {
            log.debug("Delivery prime: " + deliveryPrime);
            log.debug("Delivery step bolus: " + deliveryStepBolus);
            log.debug("Delivery basal: " + deliveryBasal);
            log.debug("Delivery ext bolus: " + deliveryExtBolus);
            log.debug("Bolus increment: " + pump.bolusStep);
            log.debug("Bolus max: " + pump.maxBolus);
            log.debug("Delivery status: " + deliveryStatus);
        }
    }
}
