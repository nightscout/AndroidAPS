package info.nightscout.androidaps.plugins.DanaR.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.plugins.DanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.DanaR.DanaRPump;

public class MsgInitConnStatusBasic extends MessageBase {
    private static Logger log = LoggerFactory.getLogger(MsgInitConnStatusBasic.class);

    public MsgInitConnStatusBasic() {
        SetCommand(0x0303);
    }

    @Override
    public void handleMessage(byte[] bytes) {
        if (bytes.length - 10 < 21) {
            return;
        }
        DanaRPump pump = DanaRPlugin.getDanaRPump();
        pump.pumpSuspended = intFromBuff(bytes, 0, 1) == 1;
        pump.calculatorEnabled = intFromBuff(bytes, 1, 1) == 1;
        pump.dailyTotalUnits = intFromBuff(bytes, 2, 3) / 750d;
        pump.maxDailyTotalUnits = intFromBuff(bytes, 5, 2) / 100;
        pump.reservoirRemainingUnits = intFromBuff(bytes, 7, 3) / 750d;
        pump.bolusBlocked = intFromBuff(bytes, 10, 1) == 1;
        pump.currentBasal = intFromBuff(bytes, 11, 2) / 100d;
        pump.tempBasalPercent = intFromBuff(bytes, 13, 1);
        pump.isExtendedInProgress = intFromBuff(bytes, 14, 1) == 1;
        pump.isTempBasalInProgress = intFromBuff(bytes, 15, 1) == 1;
        int statusBasalUDOption = intFromBuff(bytes, 16, 1);
        pump.isDualBolusInProgress = intFromBuff(bytes, 17, 1) == 1;
        double extendedBolusRate = intFromBuff(bytes, 18, 2) / 100d;
        pump.batteryRemaining = intFromBuff(bytes, 20, 1);
        try {
            int bolusConfig = intFromBuff(bytes, 21, 1);
            boolean deliveryPrime = (bolusConfig & DanaRPump.DELIVERY_PRIME) != 0;
            boolean deliveryStepBolus = (bolusConfig & DanaRPump.DELIVERY_STEP_BOLUS) != 0;
            boolean deliveryBasal = (bolusConfig & DanaRPump.DELIVERY_BASAL) != 0;
            boolean deliveryExtBolus = (bolusConfig & DanaRPump.DELIVERY_EXT_BOLUS) != 0;
            log.debug("Delivery prime: " + deliveryPrime);
            log.debug("Delivery step bolus: " + deliveryStepBolus);
            log.debug("Delivery basal: " + deliveryBasal);
            log.debug("Delivery ext bolus: " + deliveryExtBolus);
        } catch (Exception e) {
        }

        if (Config.logDanaMessageDetail) {
            log.debug("Pump suspended: " + pump.pumpSuspended);
            log.debug("Calculator enabled: " + pump.calculatorEnabled);
            log.debug("Daily total units: " + pump.dailyTotalUnits);
            log.debug("Max daily total units: " + pump.maxDailyTotalUnits);
            log.debug("Reservoir remaining units: " + pump.reservoirRemainingUnits);
            log.debug("Bolus blocked: " + pump.bolusBlocked);
            log.debug("Current basal: " + pump.currentBasal);
            log.debug("Current temp basal percent: " + pump.tempBasalPercent);
            log.debug("Is extended bolus running: " + pump.isExtendedInProgress);
            log.debug("statusBasalUDOption: " + statusBasalUDOption);
            log.debug("Is dual bolus running: " + pump.isDualBolusInProgress);
            log.debug("Extended bolus rate: " + extendedBolusRate);
            log.debug("Battery remaining: " + pump.batteryRemaining);
        }
    }
}
