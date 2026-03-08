package app.aaps.pump.common.driver.refresh

import app.aaps.pump.common.driver.connector.defs.PumpCommandType

/**
 * Created by andy on 04.07.2022.
 */
enum class PumpDataRefreshType(val commandType: PumpCommandType?) {
    PumpHistory(PumpCommandType.GetHistory),
    Configuration(PumpCommandType.GetSettings),
    RemainingInsulin(PumpCommandType.GetRemainingInsulin),
    BatteryStatus(PumpCommandType.GetBatteryStatus),
    PumpTime(PumpCommandType.GetTime),
    PumpStatus(PumpCommandType.GetPumpStatus),
    GetTemporaryBasal(PumpCommandType.GetTemporaryBasal),
    Custom_1(null),
    Custom_2(null),
    Custom_3(null),
    Custom_4(null),
}
