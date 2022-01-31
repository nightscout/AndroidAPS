package info.nightscout.androidaps.plugins.pump.medtronic.defs

/**
 * Created by andy on 6/28/18.
 */
enum class MedtronicStatusRefreshType(val refreshTime: Int,
                                      private val commandType: MedtronicCommandType?) {

    PumpHistory(5, null),  //
    Configuration(0, null),  //
    RemainingInsulin(-1, MedtronicCommandType.GetRemainingInsulin),  //
    BatteryStatus(55, MedtronicCommandType.GetBatteryStatus),  //
    PumpTime(60, MedtronicCommandType.GetRealTimeClock //
    );

    fun getCommandType(medtronicDeviceType: MedtronicDeviceType): MedtronicCommandType? {
        return if (this == Configuration) {
            MedtronicCommandType.getSettings(medtronicDeviceType)
        } else
            commandType
    }

}