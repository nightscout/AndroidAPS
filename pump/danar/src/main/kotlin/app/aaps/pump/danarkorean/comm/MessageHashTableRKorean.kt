package app.aaps.pump.danarkorean.comm

import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.danar.comm.MsgBolusProgress
import app.aaps.pump.danar.comm.MsgBolusStart
import app.aaps.pump.danar.comm.MsgBolusStop
import app.aaps.pump.danar.comm.MsgError
import app.aaps.pump.danar.comm.MsgHistoryAlarm
import app.aaps.pump.danar.comm.MsgHistoryAll
import app.aaps.pump.danar.comm.MsgHistoryBolus
import app.aaps.pump.danar.comm.MsgHistoryCarbo
import app.aaps.pump.danar.comm.MsgHistoryDailyInsulin
import app.aaps.pump.danar.comm.MsgHistoryGlucose
import app.aaps.pump.danar.comm.MsgHistoryNew
import app.aaps.pump.danar.comm.MsgHistoryNewDone
import app.aaps.pump.danar.comm.MsgPCCommStart
import app.aaps.pump.danar.comm.MsgPCCommStop
import app.aaps.pump.danar.comm.MsgSetCarbsEntry
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStart
import app.aaps.pump.danar.comm.MsgSetExtendedBolusStop
import app.aaps.pump.danar.comm.MsgSetSingleBasalProfile
import app.aaps.pump.danar.comm.MsgSetTempBasalStart
import app.aaps.pump.danar.comm.MsgSetTempBasalStop
import app.aaps.pump.danar.comm.MsgSettingGlucose
import app.aaps.pump.danar.comm.MsgSettingMaxValues
import app.aaps.pump.danar.comm.MsgSettingMeal
import app.aaps.pump.danar.comm.MsgSettingProfileRatios
import app.aaps.pump.danar.comm.MsgSettingPumpTime
import app.aaps.pump.danar.comm.MsgSettingShippingInfo
import app.aaps.pump.danar.comm.MsgStatusBolusExtended
import app.aaps.pump.danar.comm.MsgStatusProfile
import app.aaps.pump.danar.comm.MsgStatusTempBasal
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHashTableRKorean @Inject constructor(
    private val injector: HasAndroidInjector
) : MessageHashTableBase {

    private var messages: HashMap<Int, MessageBase> = HashMap()

    init {
        put(MsgBolusStop(injector))                 // 0x0101 CMD_MEALINS_STOP
        put(MsgBolusStart(injector, 0.0))                // 0x0102 CMD_MEALINS_START_DATA
        put(MsgBolusProgress(injector))             // 0x0202 CMD_PUMP_THIS_REMAINDER_MEAL_INS
        put(MsgStatusProfile(injector))             // 0x0204 CMD_PUMP_CALCULATION_SETTING
        put(MsgStatusTempBasal(injector))           // 0x0205 CMD_PUMP_EXERCISE_MODE
        put(MsgStatusBolusExtended(injector))       // 0x0207 CMD_PUMP_EXPANS_INS_I
        put(MsgStatusBasic_k(injector))               // 0x020A CMD_PUMP_INITVIEW_I
        put(MsgStatus_k(injector))                    // 0x020B CMD_PUMP_STATUS
        put(MsgInitConnStatusTimeK(injector))        // 0x0301 CMD_PUMPINIT_TIME_INFO
        put(MsgInitConnStatusBolusK(injector))       // 0x0302 CMD_PUMPINIT_BOLUS_INFO
        put(MsgInitConnStatusBasicK(injector))       // 0x0303 CMD_PUMPINIT_INIT_INFO
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
        put(MsgHistoryCarbo(injector))              // 0x3107 CMD_HISTORY_CARBOHY
        put(MsgSettingBasal_k(injector))        // 0x3202 CMD_SETTING_V_BASAL_INS_I
        put(MsgSettingMeal(injector))        // 0x3203 CMD_SETTING_V_MEAL_SETTING_I
        put(MsgSettingProfileRatios(injector))      // 0x3204 CMD_SETTING_V_CCC_I
        put(MsgSettingMaxValues(injector))          // 0x3205 CMD_SETTING_V_MAX_VALUE_I
        put(MsgSettingBasalProfileAllK(injector))    // 0x3206 CMD_SETTING_V_BASAL_PROFILE_ALL
        put(MsgSettingShippingInfo(injector))       // 0x3207 CMD_SETTING_V_SHIPPING_I
        put(MsgSettingGlucose(injector))            // 0x3209 CMD_SETTING_V_GLUCOSEandEASY
        put(MsgSettingPumpTime(injector))           // 0x320A CMD_SETTING_V_TIME_I
        put(MsgSetSingleBasalProfile(injector, Array(24) { 0.0 }))     // 0x3302 CMD_SETTING_BASAL_INS_S
        put(MsgHistoryAll(injector))                // 0x41F2 CMD_HISTORY_ALL
        put(MsgHistoryNewDone(injector))            // 0x42F1 CMD_HISTORY_NEW_DONE
        put(MsgHistoryNew(injector))                // 0x42F2 CMD_HISTORY_NEW
        put(MsgCheckValueK(injector))        // 0xF0F1 CMD_PUMP_CHECK_VALUE
    }

    override fun put(message: MessageBase) {
        messages[message.command] = message
    }

    override fun findMessage(command: Int): MessageBase {
        return messages[command] ?: MessageBase(injector)
    }
}
