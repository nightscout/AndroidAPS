package info.nightscout.androidaps.danars.comm

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.Profile
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DanaRSMessageHashTable @Inject constructor(
    val injector: HasAndroidInjector
) {

    var messages: HashMap<Int, DanaRS_Packet> = HashMap()

    fun put(message: DanaRS_Packet) {
        messages[message.command] = message
    }

    fun findMessage(command: Int): DanaRS_Packet {
        return messages[command] ?: DanaRS_Packet(injector)
    }

    init {
        put(DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal(injector))
        put(DanaRS_Packet_Basal_Get_Basal_Rate(injector))
        put(DanaRS_Packet_Basal_Get_Profile_Basal_Rate(injector))
        put(DanaRS_Packet_Basal_Get_Profile_Number(injector))
        put(DanaRS_Packet_Basal_Set_Basal_Rate(injector, arrayOf()))
        put(DanaRS_Packet_Basal_Set_Profile_Basal_Rate(injector, 0, arrayOf()))
        put(DanaRS_Packet_Basal_Set_Profile_Number(injector))
        put(DanaRS_Packet_Basal_Set_Suspend_Off(injector))
        put(DanaRS_Packet_Basal_Set_Suspend_On(injector))
        put(DanaRS_Packet_Basal_Set_Temporary_Basal(injector))
        put(DanaRS_Packet_Basal_Get_Temporary_Basal_State(injector))
        put(DanaRS_Packet_Bolus_Get_Bolus_Option(injector))
        put(DanaRS_Packet_Bolus_Get_Initial_Bolus(injector))
        put(DanaRS_Packet_Bolus_Get_Calculation_Information(injector))
        put(DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information(injector))
        put(DanaRS_Packet_Bolus_Get_CIR_CF_Array(injector))
        put(DanaRS_Packet_Bolus_Get_24_CIR_CF_Array(injector))
        put(DanaRS_Packet_Bolus_Get_Dual_Bolus(injector))
        put(DanaRS_Packet_Bolus_Get_Extended_Bolus(injector))
        put(DanaRS_Packet_Bolus_Get_Extended_Bolus_State(injector))
        put(DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State(injector))
        put(DanaRS_Packet_Bolus_Get_Step_Bolus_Information(injector))
        put(DanaRS_Packet_Bolus_Set_Bolus_Option(injector))
        put(DanaRS_Packet_Bolus_Set_Initial_Bolus(injector))
        put(DanaRS_Packet_Bolus_Set_CIR_CF_Array(injector))
        put(DanaRS_Packet_Bolus_Set_24_CIR_CF_Array(injector, Profile(injector, null)))
        put(DanaRS_Packet_Bolus_Set_Dual_Bolus(injector))
        put(DanaRS_Packet_Bolus_Set_Extended_Bolus(injector))
        put(DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel(injector))
        put(DanaRS_Packet_Bolus_Set_Step_Bolus_Start(injector))
        put(DanaRS_Packet_Bolus_Set_Step_Bolus_Stop(injector))
        put(DanaRS_Packet_Etc_Keep_Connection(injector))
        put(DanaRS_Packet_Etc_Set_History_Save(injector))
        put(DanaRS_Packet_General_Delivery_Status(injector))
        put(DanaRS_Packet_General_Get_Password(injector))
        put(DanaRS_Packet_General_Initial_Screen_Information(injector))
        put(DanaRS_Packet_Notify_Alarm(injector))
        put(DanaRS_Packet_Notify_Delivery_Complete(injector))
        put(DanaRS_Packet_Notify_Delivery_Rate_Display(injector))
        put(DanaRS_Packet_Notify_Missed_Bolus_Alarm(injector))
        put(DanaRS_Packet_Option_Get_Pump_Time(injector))
        put(DanaRS_Packet_Option_Get_Pump_UTC_And_TimeZone(injector))
        put(DanaRS_Packet_Option_Get_User_Option(injector))
        put(DanaRS_Packet_Option_Set_Pump_Time(injector))
        put(DanaRS_Packet_Option_Set_Pump_UTC_And_TimeZone(injector))
        put(DanaRS_Packet_Option_Set_User_Option(injector))
        //put(new DanaRS_Packet_History_(injector));
        put(DanaRS_Packet_History_Alarm(injector))
        put(DanaRS_Packet_History_All_History(injector))
        put(DanaRS_Packet_History_Basal(injector))
        put(DanaRS_Packet_History_Blood_Glucose(injector))
        put(DanaRS_Packet_History_Bolus(injector))
        put(DanaRS_Packet_Review_Bolus_Avg(injector))
        put(DanaRS_Packet_History_Carbohydrate(injector))
        put(DanaRS_Packet_History_Daily(injector))
        put(DanaRS_Packet_General_Get_More_Information(injector))
        put(DanaRS_Packet_General_Get_Pump_Check(injector))
        put(DanaRS_Packet_General_Get_Shipping_Information(injector))
        put(DanaRS_Packet_General_Get_Today_Delivery_Total(injector))
        put(DanaRS_Packet_General_Get_User_Time_Change_Flag(injector))
        put(DanaRS_Packet_History_Prime(injector))
        put(DanaRS_Packet_History_Refill(injector))
        put(DanaRS_Packet_General_Set_History_Upload_Mode(injector))
        put(DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear(injector))
        put(DanaRS_Packet_History_Suspend(injector))
        put(DanaRS_Packet_History_Temporary(injector))
        // APS
        put(DanaRS_Packet_APS_Basal_Set_Temporary_Basal(injector, 0))
        put(DanaRS_Packet_APS_History_Events(injector, 0))
        put(DanaRS_Packet_APS_Set_Event_History(injector, 0, 0, 0, 0))
        // v3
        put(DanaRS_Packet_General_Get_Shipping_Version(injector))
        put(DanaRS_Packet_Review_Get_Pump_Dec_Ratio(injector))
    }
}