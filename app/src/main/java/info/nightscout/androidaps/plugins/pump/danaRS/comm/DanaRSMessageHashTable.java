package info.nightscout.androidaps.plugins.pump.danaRS.comm;

import java.util.HashMap;

/**
 * Created by mike on 28.05.2016.
 */
public class DanaRSMessageHashTable {
    public static HashMap<Integer, DanaRS_Packet> messages = null;

    static {
        if (messages == null) {
            messages = new HashMap<>();
            put(new DanaRS_Packet_Basal_Set_Cancel_Temporary_Basal());
            put(new DanaRS_Packet_Basal_Get_Basal_Rate());
            put(new DanaRS_Packet_Basal_Get_Profile_Basal_Rate());
            put(new DanaRS_Packet_Basal_Get_Profile_Number());
            put(new DanaRS_Packet_Basal_Set_Basal_Rate());
            put(new DanaRS_Packet_Basal_Set_Profile_Basal_Rate());
            put(new DanaRS_Packet_Basal_Set_Profile_Number());
            put(new DanaRS_Packet_Basal_Set_Suspend_Off());
            put(new DanaRS_Packet_Basal_Set_Suspend_On());
            put(new DanaRS_Packet_Basal_Set_Temporary_Basal());
            put(new DanaRS_Packet_Basal_Get_Temporary_Basal_State());
            put(new DanaRS_Packet_Bolus_Get_Bolus_Option());
            put(new DanaRS_Packet_Bolus_Get_Initial_Bolus());
            put(new DanaRS_Packet_Bolus_Get_Calculation_Information());
            put(new DanaRS_Packet_Bolus_Get_Carbohydrate_Calculation_Information());
            put(new DanaRS_Packet_Bolus_Get_CIR_CF_Array());
            put(new DanaRS_Packet_Bolus_Get_Dual_Bolus());
            put(new DanaRS_Packet_Bolus_Get_Extended_Bolus());
            put(new DanaRS_Packet_Bolus_Get_Extended_Bolus_State());
            put(new DanaRS_Packet_Bolus_Get_Extended_Menu_Option_State());
            put(new DanaRS_Packet_Bolus_Get_Step_Bolus_Information());
            put(new DanaRS_Packet_Bolus_Set_Bolus_Option());
            put(new DanaRS_Packet_Bolus_Set_Initial_Bolus());
            put(new DanaRS_Packet_Bolus_Set_CIR_CF_Array());
            put(new DanaRS_Packet_Bolus_Set_Dual_Bolus());
            put(new DanaRS_Packet_Bolus_Set_Extended_Bolus());
            put(new DanaRS_Packet_Bolus_Set_Extended_Bolus_Cancel());
            put(new DanaRS_Packet_Bolus_Set_Step_Bolus_Start());
            put(new DanaRS_Packet_Bolus_Set_Step_Bolus_Stop());
            put(new DanaRS_Packet_Etc_Keep_Connection());
            put(new DanaRS_Packet_Etc_Set_History_Save());
            put(new DanaRS_Packet_General_Delivery_Status());
            put(new DanaRS_Packet_General_Get_Password());
            put(new DanaRS_Packet_General_Initial_Screen_Information());
            put(new DanaRS_Packet_Notify_Alarm());
            put(new DanaRS_Packet_Notify_Delivery_Complete());
            put(new DanaRS_Packet_Notify_Delivery_Rate_Display());
            put(new DanaRS_Packet_Notify_Missed_Bolus_Alarm());
            put(new DanaRS_Packet_Option_Get_Pump_Time());
            put(new DanaRS_Packet_Option_Get_User_Option());
            put(new DanaRS_Packet_Option_Set_Pump_Time());
            put(new DanaRS_Packet_Option_Set_User_Option());
            //put(new DanaRS_Packet_History_());
            put(new DanaRS_Packet_History_Alarm());
            put(new DanaRS_Packet_History_All_History());
            put(new DanaRS_Packet_History_Basal());
            put(new DanaRS_Packet_History_Blood_Glucose());
            put(new DanaRS_Packet_History_Bolus());
            put(new DanaRS_Packet_Review_Bolus_Avg());
            put(new DanaRS_Packet_History_Carbohydrate());
            put(new DanaRS_Packet_History_Daily());
            put(new DanaRS_Packet_General_Get_More_Information());
            put(new DanaRS_Packet_General_Get_Pump_Check());
            put(new DanaRS_Packet_General_Get_Shipping_Information());
            put(new DanaRS_Packet_General_Get_Today_Delivery_Total());
            put(new DanaRS_Packet_General_Get_User_Time_Change_Flag());
            put(new DanaRS_Packet_History_Prime());
            put(new DanaRS_Packet_History_Refill());
            put(new DanaRS_Packet_General_Set_History_Upload_Mode());
            put(new DanaRS_Packet_General_Set_User_Time_Change_Flag_Clear());
            put(new DanaRS_Packet_History_Suspend());
            put(new DanaRS_Packet_History_Temporary());

            // APS
            put(new DanaRS_Packet_APS_Basal_Set_Temporary_Basal());
            put(new DanaRS_Packet_APS_History_Events());
            put(new DanaRS_Packet_APS_Set_Event_History());

        }
    }

    public static void put(DanaRS_Packet message) {
        int command = message.getCommand();
        messages.put(command, message);
    }

    public static DanaRS_Packet findMessage(Integer command) {
        if (messages.containsKey(command)) {
            return messages.get(command);
        } else {
            return new DanaRS_Packet();
        }
    }
}
