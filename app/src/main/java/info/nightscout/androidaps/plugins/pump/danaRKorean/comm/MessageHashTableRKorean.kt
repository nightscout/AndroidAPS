package info.nightscout.androidaps.plugins.pump.danaRKorean.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPlugin
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaR.comm.*
import info.nightscout.androidaps.plugins.pump.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageHashTableRKorean @Inject constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    constraintChecker: ConstraintChecker,
    danaRPump: DanaRPump,
    danaRPlugin: DanaRPlugin,
    danaRKoreanPlugin: DanaRKoreanPlugin,
    configBuilderPlugin: ConfigBuilderPlugin,
    commandQueue: CommandQueueProvider,
    activePlugin: ActivePluginProvider,
    injector: HasAndroidInjector
) : MessageHashTableBase {

    var messages: HashMap<Int, MessageBase> = HashMap()

    init {
        put(MsgBolusStop(aapsLogger, rxBus, resourceHelper, danaRPump))                 // 0x0101 CMD_MEALINS_STOP
        put(MsgBolusStart(aapsLogger, constraintChecker, danaRPump, 0.0))                // 0x0102 CMD_MEALINS_START_DATA
        put(MsgBolusProgress(aapsLogger, resourceHelper, rxBus, danaRPump))             // 0x0202 CMD_PUMP_THIS_REMAINDER_MEAL_INS
        put(MsgStatusProfile(aapsLogger, danaRPump))             // 0x0204 CMD_PUMP_CALCULATION_SETTING
        put(MsgStatusTempBasal(aapsLogger, danaRPump, activePlugin, injector))           // 0x0205 CMD_PUMP_EXERCISE_MODE
        put(MsgStatusBolusExtended(injector, aapsLogger, danaRPump, activePlugin))       // 0x0207 CMD_PUMP_EXPANS_INS_I
        put(MsgStatusBasic_k(aapsLogger, danaRPump))               // 0x020A CMD_PUMP_INITVIEW_I
        put(MsgStatus_k(aapsLogger, danaRPump))                    // 0x020B CMD_PUMP_STATUS
        put(MsgInitConnStatusTime_k(aapsLogger, rxBus, resourceHelper, danaRPump, danaRPlugin, danaRKoreanPlugin, configBuilderPlugin, commandQueue))        // 0x0301 CMD_PUMPINIT_TIME_INFO
        put(MsgInitConnStatusBolus_k(aapsLogger, rxBus, resourceHelper, danaRPump, activePlugin))       // 0x0302 CMD_PUMPINIT_BOLUS_INFO
        put(MsgInitConnStatusBasic_k(aapsLogger, rxBus, resourceHelper, danaRPump))       // 0x0303 CMD_PUMPINIT_INIT_INFO
        put(MsgSetTempBasalStart(aapsLogger, 0, 0))         // 0x0401 CMD_PUMPSET_EXERCISE_S
        put(MsgSetCarbsEntry(aapsLogger, 0, 0))             // 0x0402 CMD_PUMPSET_HIS_S
        put(MsgSetTempBasalStop(aapsLogger))          // 0x0403 CMD_PUMPSET_EXERCISE_STOP
        put(MsgSetExtendedBolusStop(aapsLogger))      // 0x0406 CMD_PUMPSET_EXPANS_INS_STOP
        put(MsgSetExtendedBolusStart(aapsLogger, constraintChecker, 0.0, 0))     // 0x0407 CMD_PUMPSET_EXPANS_INS_S
        put(MsgError(aapsLogger, rxBus, resourceHelper, danaRPump))                     // 0x0601 CMD_PUMPOWAY_SYSTEM_STATUS
        put(MsgPCCommStart(aapsLogger))               // 0x3001 CMD_CONNECT
        put(MsgPCCommStop(aapsLogger))                // 0x3002 CMD_DISCONNECT
        put(MsgHistoryBolus(aapsLogger, rxBus))              // 0x3101 CMD_HISTORY_MEAL_INS
        put(MsgHistoryDailyInsulin(aapsLogger, rxBus))       // 0x3102 CMD_HISTORY_DAY_INS
        put(MsgHistoryGlucose(aapsLogger, rxBus))            // 0x3104 CMD_HISTORY_GLUCOSE
        put(MsgHistoryAlarm(aapsLogger, rxBus))              // 0x3105 CMD_HISTORY_ALARM
        put(MsgHistoryCarbo(aapsLogger, rxBus))              // 0x3107 CMD_HISTORY_CARBOHY
        put(MsgSettingBasal_k(aapsLogger, danaRPump, danaRKoreanPlugin))        // 0x3202 CMD_SETTING_V_BASAL_INS_I
        put(MsgSettingMeal(aapsLogger, rxBus, resourceHelper, danaRPump, danaRKoreanPlugin))        // 0x3203 CMD_SETTING_V_MEAL_SETTING_I
        put(MsgSettingProfileRatios(aapsLogger, danaRPump))      // 0x3204 CMD_SETTING_V_CCC_I
        put(MsgSettingMaxValues(aapsLogger, danaRPump))          // 0x3205 CMD_SETTING_V_MAX_VALUE_I
        put(MsgSettingBasalProfileAll_k(aapsLogger, danaRPump))    // 0x3206 CMD_SETTING_V_BASAL_PROFILE_ALL
        put(MsgSettingShippingInfo(aapsLogger, danaRPump))       // 0x3207 CMD_SETTING_V_SHIPPING_I
        put(MsgSettingGlucose(aapsLogger, danaRPump))            // 0x3209 CMD_SETTING_V_GLUCOSEandEASY
        put(MsgSettingPumpTime(aapsLogger, danaRPump))           // 0x320A CMD_SETTING_V_TIME_I
        put(MsgSetSingleBasalProfile(aapsLogger, rxBus, resourceHelper, Array(24) { 0.0 }))     // 0x3302 CMD_SETTING_BASAL_INS_S
        put(MsgHistoryAll(aapsLogger, rxBus))                // 0x41F2 CMD_HISTORY_ALL
        put(MsgHistoryNewDone(aapsLogger, danaRPump))            // 0x42F1 CMD_HISTORY_NEW_DONE
        put(MsgHistoryNew(aapsLogger, rxBus))                // 0x42F2 CMD_HISTORY_NEW
        put(MsgCheckValue_k(aapsLogger, danaRPump, danaRKoreanPlugin))        // 0xF0F1 CMD_PUMP_CHECK_VALUE
    }

    override fun put(message: MessageBase) {
        messages[message.command] = message
    }

    override fun findMessage(command: Int): MessageBase {
        return messages[command] ?: MessageBase()
    }
}
