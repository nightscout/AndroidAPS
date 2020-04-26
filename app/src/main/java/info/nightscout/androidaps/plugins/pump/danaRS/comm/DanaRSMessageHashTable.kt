package info.nightscout.androidaps.plugins.pump.danaRS.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump
import info.nightscout.androidaps.plugins.pump.danaRS.DanaRSPlugin
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DanaRSMessageHashTable @Inject constructor(
    aapsLogger: AAPSLogger,
    rxBus: RxBusWrapper,
    resourceHelper: ResourceHelper,
    danaRPump: DanaRPump,
    danaRSPlugin: DanaRSPlugin,
    activePlugin: ActivePluginProvider,
    constraintChecker: ConstraintChecker,
    detailedBolusInfoStorage: DetailedBolusInfoStorage,
    injector: HasAndroidInjector
) {

    var messages: HashMap<Int, DanaRS_Packet> = HashMap()

    fun put(message: DanaRS_Packet) {
        messages[message.command] = message
    }

    fun findMessage(command: Int): DanaRS_Packet {
        return messages[command] ?: DanaRS_Packet()
    }

    init {
        put(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(aapsLogger))
        put(DanaRS_Packet_Basal_Get_Basal_Rate(aapsLogger, rxBus, resourceHelper, danaRPump))
        put(DanaRS_Packet_Basal_Get_Profile_Basal_Rate(aapsLogger, danaRPump))
        put(DanaRS_Packet_Basal_Get_Profile_Number(aapsLogger, danaRPump))
        put(DanaRS_Packet_Basal_Set_Basal_Rate(aapsLogger, arrayOf()))
        put(DanaRS_Packet_Basal_Set_Profile_Basal_Rate(aapsLogger, 0, arrayOf()))
        put(DanaRS_Packet_Basal_Set_Profile_Number(aapsLogger))
        put(DanaRS_Packet_Basal_Set_Suspend_Off(aapsLogger))
        put(DanaRS_Packet_Basal_Set_Suspend_On(aapsLogger))
        put(DanaRS_Packet_Basal_Set_Temporary_Basal(aapsLogger))
        put(DanaRS_Packet_Basal_Get_Temporary_Basal_State(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Bolus_Option(aapsLogger, rxBus, resourceHelper, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Initial_Bolus(aapsLogger))
        put(DanaRS_Packet_Bolus_Get_Calculation_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_CIR_CF_Array(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Dual_Bolus(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Extended_Bolus(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Extended_Bolus_State(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Get_Step_Bolus_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_Bolus_Set_Bolus_Option(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_Initial_Bolus(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_CIR_CF_Array(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_Dual_Bolus(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_Extended_Bolus(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel(aapsLogger))
        put(DanaRS_Packet_Bolus_Set_Step_Bolus_Start(aapsLogger, danaRSPlugin, constraintChecker))
        put(DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(aapsLogger, rxBus, resourceHelper, danaRSPlugin))
        put(DanaRS_Packet_Etc_Keep_Connection(aapsLogger))
        put(DanaRS_Packet_Etc_Set_History_Save(aapsLogger))
        put(DanaRS_Packet_General_Delivery_Status(aapsLogger))
        put(DanaRS_Packet_General_Get_Password(aapsLogger, danaRPump))
        put(DanaRS_Packet_General_Initial_Screen_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_Notify_Alarm(aapsLogger, resourceHelper))
        put(DanaRS_Packet_Notify_Delivery_Complete(aapsLogger, rxBus, resourceHelper, danaRSPlugin))
        put(DanaRS_Packet_Notify_Delivery_Rate_Display(aapsLogger, rxBus, resourceHelper, danaRSPlugin))
        put(DanaRS_Packet_Notify_Missed_Bolus_Alarm(aapsLogger))
        put(DanaRS_Packet_Option_Get_Pump_Time(aapsLogger, danaRPump))
        put(DanaRS_Packet_Option_Get_User_Option(aapsLogger, danaRPump))
        put(DanaRS_Packet_Option_Set_Pump_Time(aapsLogger))
        put(DanaRS_Packet_Option_Set_User_Option(aapsLogger, danaRPump))
        //put(new DanaRS_Packet_History_());
        put(DanaRS_Packet_History_Alarm(aapsLogger, rxBus))
        put(DanaRS_Packet_History_All_History(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Basal(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Blood_Glucose(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Bolus(aapsLogger, rxBus))
        put(DanaRS_Packet_Review_Bolus_Avg(aapsLogger))
        put(DanaRS_Packet_History_Carbohydrate(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Daily(aapsLogger, rxBus))
        put(DanaRS_Packet_General_Get_More_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_General_Get_Pump_Check(aapsLogger, danaRPump, rxBus, resourceHelper))
        put(DanaRS_Packet_General_Get_Shipping_Information(aapsLogger, danaRPump))
        put(DanaRS_Packet_General_Get_Today_Delivery_Total(aapsLogger, danaRPump))
        put(DanaRS_Packet_General_Get_User_Time_Change_Flag(aapsLogger))
        put(DanaRS_Packet_History_Prime(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Refill(aapsLogger, rxBus))
        put(DanaRS_Packet_General_Set_History_Upload_Mode(aapsLogger))
        put(DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear(aapsLogger))
        put(DanaRS_Packet_History_Suspend(aapsLogger, rxBus))
        put(DanaRS_Packet_History_Temporary(aapsLogger, rxBus))
        // APS
        put(DanaRS_Packet_APS_Basal_Set_Temporary_Basal(aapsLogger, 0))
        put(DanaRS_Packet_APS_History_Events(aapsLogger, rxBus, resourceHelper, activePlugin, danaRSPlugin, detailedBolusInfoStorage, injector,0))
        put(DanaRS_Packet_APS_Set_Event_History(aapsLogger, 0, 0, 0, 0))
    }
}