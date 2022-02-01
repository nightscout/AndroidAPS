package info.nightscout.androidaps.danaRv2.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.danar.comm.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHashTableRv2 @Inject constructor(
    private val injector: HasAndroidInjector
) : MessageHashTableBase {

    private var messages: HashMap<Int, MessageBase> = HashMap()

    init {
        put(MsgBolusStop(injector))                 // 0x0101 CMD_MEALINS_STOP
        put(MsgBolusStart(injector, 0.0))                // 0x0102 CMD_MEALINS_START_DATA
        put(MsgBolusStartWithSpeed(injector, 0.0, 0))       // 0x0104 CMD_MEALINS_START_DATA_SPEED
        put(MsgBolusProgress(injector))             // 0x0202 CMD_PUMP_THIS_REMAINDER_MEAL_INS
        put(MsgStatusProfile(injector))             // 0x0204 CMD_PUMP_CALCULATION_SETTING
        put(MsgStatusTempBasal(injector))           // 0x0205 CMD_PUMP_EXERCISE_MODE
        put(MsgStatusBolusExtended(injector))    // 0x0207 CMD_PUMP_EXPANS_INS_I
        put(MsgStatusBasic(injector))               // 0x020A CMD_PUMP_INITVIEW_I
        put(MsgStatus(injector))                    // 0x020B CMD_PUMP_STATUS
        put(MsgInitConnStatusTime(injector))        // 0x0301 CMD_PUMPINIT_TIME_INFO
        put(MsgInitConnStatusBolus(injector))       // 0x0302 CMD_PUMPINIT_BOLUS_INFO
        put(MsgInitConnStatusBasic(injector))       // 0x0303 CMD_PUMPINIT_INIT_INFO
        put(MsgInitConnStatusOption(injector))      // 0x0304 CMD_PUMPINIT_OPTION
        put(MsgSetTempBasalStart(injector, 0, 0))         // 0x0401 CMD_PUMPSET_EXERCISE_S
        put(MsgSetCarbsEntry(injector, 0, 0))             // 0x0402 CMD_PUMPSET_HIS_S
        put(MsgSetTempBasalStop(injector))          // 0x0403 CMD_PUMPSET_EXERCISE_STOP
        put(MsgSetExtendedBolusStop(injector))      // 0x0406 CMD_PUMPSET_EXPANS_INS_STOP
        put(MsgSetExtendedBolusStart(injector, 0.0, 0))     // 0x0407 CMD_PUMPSET_EXPANS_INS_S
        put(MsgError(injector))                     // 0x0601 CMD_PUMPOWAY_SYSTEM_STATUS
        put(MsgPCCommStart(injector))               // 0x3001 CMD_CONNECT
        put(MsgPCCommStop(injector))                // 0x3002 CMD_DISCONNECT
        put(MsgHistoryBolus(injector))              // 0x3101 CMD_HISTORY_MEAL_INS
        put(MsgHistoryDailyInsulin(injector))       // 0x3102 CMD_HISTORY_DAY_INS
        put(MsgHistoryGlucose(injector))            // 0x3104 CMD_HISTORY_GLUCOSE
        put(MsgHistoryAlarm(injector))              // 0x3105 CMD_HISTORY_ALARM
        put(MsgHistoryError(injector))              // 0x3106 CMD_HISTORY_ERROR
        put(MsgHistoryCarbo(injector))              // 0x3107 CMD_HISTORY_CARBOHY
        put(MsgHistoryRefill(injector))             // 0x3108 CMD_HISTORY_REFILL
        put(MsgHistorySuspend(injector))            // 0x3109 CMD_HISTORY_SUSPEND
        put(MsgHistoryBasalHour(injector))          // 0x310A CMD_HISTORY_BASAL_HOUR
        put(MsgHistoryDone(injector))               // 0x31F1 CMD_HISTORY_DONT_USED
        put(MsgSettingBasal(injector))        // 0x3202 CMD_SETTING_V_BASAL_INS_I
        put(MsgSettingMeal(injector))        // 0x3203 CMD_SETTING_V_MEAL_SETTING_I
        put(MsgSettingProfileRatios(injector))      // 0x3204 CMD_SETTING_V_CCC_I
        put(MsgSettingMaxValues(injector))          // 0x3205 CMD_SETTING_V_MAX_VALUE_I
        put(MsgSettingBasalProfileAll(injector))    // 0x3206 CMD_SETTING_V_BASAL_PROFILE_ALL
        put(MsgSettingShippingInfo(injector))       // 0x3207 CMD_SETTING_V_SHIPPING_I
        put(MsgSettingGlucose(injector))            // 0x3209 CMD_SETTING_V_GLUCOSEandEASY
        put(MsgSettingPumpTime(injector))           // 0x320A CMD_SETTING_V_TIME_I
        put(MsgSettingUserOptions(injector))        // 0x320B CMD_SETTING_V_USER_OPTIONS
        put(MsgSettingActiveProfile(injector))      // 0x320C CMD_SETTING_V_PROFILE_NUMBER
        put(MsgSettingProfileRatiosAll(injector))   // 0x320D CMD_SETTING_V_CIR_CF_VALUE
        put(MsgSetSingleBasalProfile(injector, Array(24) { 0.0 }))     // 0x3302 CMD_SETTING_BASAL_INS_S
        put(MsgSetBasalProfile(injector, 0.toByte(), Array(24) { 0.0 }))           // 0x3306 CMD_SETTING_BASAL_PROFILE_S
        put(MsgSetUserOptions(injector))            // 0x330B CMD_SETTING_USER_OPTIONS_S
        put(MsgSetActivateBasalProfile(injector, 0.toByte()))   // 0x330C CMD_SETTING_PROFILE_NUMBER_S
        put(MsgHistoryAllDone(injector))            // 0x41F1 CMD_HISTORY_ALL_DONE
        put(MsgHistoryAll(injector))                // 0x41F2 CMD_HISTORY_ALL
        put(MsgHistoryNewDone(injector))            // 0x42F1 CMD_HISTORY_NEW_DONE
        put(MsgHistoryNew(injector))                // 0x42F2 CMD_HISTORY_NEW
        put(MsgCheckValue_v2(injector))        // 0xF0F1 CMD_PUMP_CHECK_VALUE
        put(MsgStatusAPS_v2(injector))              // 0xE001 CMD_PUMPSTATUS_APS
        put(MsgSetAPSTempBasalStart_v2(injector, 0, false, false))   // 0xE002 CMD_PUMPSET_APSTEMP
        put(MsgHistoryEventsV2(injector))          // 0xE003 CMD_GET_HISTORY
        put(MsgSetHistoryEntry_v2(injector, 0, 0, 0, 0))        // 0xE004 CMD_SET_HISTORY_ENTRY
    }

    override fun put(message: MessageBase) {
        messages[message.command] = message
    }

    override fun findMessage(command: Int): MessageBase {
        return messages[command] ?: MessageBase(injector)
    }
}
