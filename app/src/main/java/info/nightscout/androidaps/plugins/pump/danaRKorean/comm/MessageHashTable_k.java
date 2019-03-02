package info.nightscout.androidaps.plugins.pump.danaRKorean.comm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import info.nightscout.androidaps.plugins.pump.danaR.comm.MessageBase;
import info.nightscout.androidaps.plugins.pump.danaR.comm.*;

/**
 * Created by mike on 28.05.2016.
 */
public class MessageHashTable_k {
    private static Logger log = LoggerFactory.getLogger(MessageHashTable_k.class);

    public static HashMap<Integer, MessageBase> messages = null;

    static {
        if (messages == null) {
            messages = new HashMap<Integer, MessageBase>();
            put(new MsgBolusStop());                 // 0x0101 CMD_MEALINS_STOP
            put(new MsgBolusStart());                // 0x0102 CMD_MEALINS_START_DATA
            put(new MsgBolusProgress());             // 0x0202 CMD_PUMP_THIS_REMAINDER_MEAL_INS
            put(new MsgStatusProfile());             // 0x0204 CMD_PUMP_CALCULATION_SETTING
            put(new MsgStatusTempBasal());           // 0x0205 CMD_PUMP_EXERCISE_MODE
            put(new MsgStatusBolusExtended());       // 0x0207 CMD_PUMP_EXPANS_INS_I
            put(new MsgStatusBasic_k());               // 0x020A CMD_PUMP_INITVIEW_I
            put(new MsgStatus_k());                    // 0x020B CMD_PUMP_STATUS
            put(new MsgInitConnStatusTime_k());        // 0x0301 CMD_PUMPINIT_TIME_INFO
            put(new MsgInitConnStatusBolus_k());       // 0x0302 CMD_PUMPINIT_BOLUS_INFO
            put(new MsgInitConnStatusBasic_k());       // 0x0303 CMD_PUMPINIT_INIT_INFO
            put(new MsgSetTempBasalStart());         // 0x0401 CMD_PUMPSET_EXERCISE_S
            put(new MsgSetCarbsEntry());             // 0x0402 CMD_PUMPSET_HIS_S
            put(new MsgSetTempBasalStop());          // 0x0403 CMD_PUMPSET_EXERCISE_STOP
            put(new MsgSetExtendedBolusStop());      // 0x0406 CMD_PUMPSET_EXPANS_INS_STOP
            put(new MsgSetExtendedBolusStart());     // 0x0407 CMD_PUMPSET_EXPANS_INS_S
            put(new MsgError());                     // 0x0601 CMD_PUMPOWAY_SYSTEM_STATUS
            put(new MsgPCCommStart());               // 0x3001 CMD_CONNECT
            put(new MsgPCCommStop());                // 0x3002 CMD_DISCONNECT
            put(new MsgHistoryBolus());              // 0x3101 CMD_HISTORY_MEAL_INS
            put(new MsgHistoryDailyInsulin());       // 0x3102 CMD_HISTORY_DAY_INS
            put(new MsgHistoryGlucose());            // 0x3104 CMD_HISTORY_GLUCOSE
            put(new MsgHistoryAlarm());              // 0x3105 CMD_HISTORY_ALARM
            put(new MsgHistoryCarbo());              // 0x3107 CMD_HISTORY_CARBOHY
            put(new MsgSettingBasal_k());              // 0x3202 CMD_SETTING_V_BASAL_INS_I
            put(new MsgSettingMeal());               // 0x3203 CMD_SETTING_V_MEAL_SETTING_I
            put(new MsgSettingProfileRatios());      // 0x3204 CMD_SETTING_V_CCC_I
            put(new MsgSettingMaxValues());          // 0x3205 CMD_SETTING_V_MAX_VALUE_I
            put(new MsgSettingBasalProfileAll_k());    // 0x3206 CMD_SETTING_V_BASAL_PROFILE_ALL
            put(new MsgSettingShippingInfo());       // 0x3207 CMD_SETTING_V_SHIPPING_I
            put(new MsgSettingGlucose());            // 0x3209 CMD_SETTING_V_GLUCOSEandEASY
            put(new MsgSettingPumpTime());           // 0x320A CMD_SETTING_V_TIME_I
            put(new MsgSetSingleBasalProfile());     // 0x3302 CMD_SETTING_BASAL_INS_S
            put(new MsgHistoryAll());                // 0x41F2 CMD_HISTORY_ALL
            put(new MsgHistoryNewDone());            // 0x42F1 CMD_HISTORY_NEW_DONE
            put(new MsgHistoryNew());                // 0x42F2 CMD_HISTORY_NEW
            put(new MsgCheckValue_k());                // 0xF0F1 CMD_PUMP_CHECK_VALUE
        }
    }

    public static void put(MessageBase message) {
        int command = message.getCommand();
        //String name = MessageOriginalNames.getName(command);
        messages.put(command, message);
        //log.debug(String.format("%04x ", command) + " " + name);
    }

    public static MessageBase findMessage(Integer command) {
        if (messages.containsKey(command)) {
            return messages.get(command);
        } else {
            return new MessageBase();
        }
    }
}
