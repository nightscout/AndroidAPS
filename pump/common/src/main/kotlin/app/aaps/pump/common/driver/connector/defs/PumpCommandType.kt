package app.aaps.pump.common.driver.connector.defs

import androidx.annotation.StringRes
import app.aaps.pump.common.R

enum class PumpCommandType(@param:StringRes val resourceId: Int) {

    SetBolus(R.string.pump_cmd_desc_set_bolus),
    GetBolus(R.string.pump_cmd_desc_get_bolus),
    CancelBolus(R.string.pump_cmd_desc_cancel_bolus),

    GetFirmwareVersion(R.string.pump_cmd_desc_get_firmware),

    SetTemporaryBasal(R.string.pump_cmd_desc_set_tbr),
    GetTemporaryBasal(R.string.pump_cmd_desc_get_tbr),
    CancelTemporaryBasal(R.string.pump_cmd_desc_cancel_tbr),

    GetBasalProfile(R.string.pump_cmd_desc_get_basal_profile),
    SetBasalProfile(R.string.pump_cmd_desc_set_basal_profile),

    GetPumpStatus(R.string.pump_cmd_desc_get_pump_status),

    GetRemainingInsulin(R.string.pump_cmd_desc_get_remaining_insulin),
    GetSettings(R.string.pump_cmd_desc_get_settings),
    GetBatteryStatus(R.string.pump_cmd_desc_get_battery_status),

    GetTime(R.string.pump_cmd_desc_get_time),
    SetTime(R.string.pump_cmd_desc_set_time),

    CustomCommand(R.string.pump_cmd_desc_custom),

    GetHistory(R.string.pump_cmd_desc_get_history),
    GetHistoryWithParameters(R.string.pump_cmd_desc_get_history_params),
    ;

}