package info.nightscout.aaps.pump.common.driver.connector

import dagger.android.HasAndroidInjector
import info.nightscout.aaps.pump.common.data.BasalProfileDto
import info.nightscout.aaps.pump.common.driver.connector.commands.parameters.PumpHistoryFilterInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.response.ResultCommandResponse
import info.nightscout.aaps.pump.common.driver.connector.commands.response.DataCommandResponse
import info.nightscout.aaps.pump.common.driver.connector.defs.PumpCommandType
import info.nightscout.aaps.pump.common.defs.PumpConfigurationTypeInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.data.AdditionalResponseDataInterface
import info.nightscout.aaps.pump.common.driver.connector.commands.data.CustomCommandTypeInterface
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.aaps.pump.common.data.PumpStatus
import info.nightscout.aaps.pump.common.data.PumpTimeDifferenceDto
import info.nightscout.aaps.pump.common.defs.TempBasalPair
import info.nightscout.aaps.pump.common.utils.PumpUtil
import info.nightscout.rx.logging.AAPSLogger
import org.joda.time.DateTime
import javax.inject.Singleton

@Singleton
open class PumpDummyConnector(var pumpStatus: PumpStatus,
                              var pumpUtil: PumpUtil,
                              injector: HasAndroidInjector,
                              aapsLogger: AAPSLogger
) : PumpConnectorAbstract(injector, aapsLogger) {

    // var pumpStatus: YpsopumpPumpStatus? = null // ???

    var successfulResponse =
        ResultCommandResponse(PumpCommandType.GetBolus, true, null)

    var successfulResponseForSet =
        DataCommandResponse<AdditionalResponseDataInterface?>(PumpCommandType.SetBolus, true, null, null)


    var supportedCommandsList: Set<PumpCommandType>



    override fun getSupportedCommands(): Set<PumpCommandType> {
        return supportedCommandsList
    }


    override fun connectToPump(): Boolean {
        pumpUtil.sleepSeconds(10)
        return true
    }

    override fun disconnectFromPump(): Boolean {
        pumpUtil.sleepSeconds(10)
        return true
    }

    // override fun retrieveFirmwareVersion(): DataCommandResponse<FirmwareVersionInterface?> {
    //     return DataCommandResponse(
    //         PumpCommandType.GetFirmwareVersion, true, null, TandemPumpApiVersion.VERSION_2_1)
    // }

    override fun sendBolus(detailedBolusInfo: DetailedBolusInfo): DataCommandResponse<AdditionalResponseDataInterface?> {
        pumpUtil.sleepSeconds(10)
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.SetBolus)
    }

    override fun retrieveTemporaryBasal(): DataCommandResponse<TempBasalPair?> {
        pumpUtil.sleepSeconds(10)
            // TODO legacyMode
        return if (pumpStatus.tempBasalStart == null ||
            pumpStatus.tempBasalEnd == null ||
            System.currentTimeMillis() > pumpStatus.tempBasalEnd!!) {
            DataCommandResponse(
                PumpCommandType.GetTemporaryBasal, true, null, TempBasalPair(0.0, true, 0)
            )
        } else {
            val tempBasalPair = TempBasalPair()
            tempBasalPair.insulinRate = pumpStatus.tempBasalAmount!!
            val diff = pumpStatus.tempBasalStart!! - System.currentTimeMillis()
            val diffMin = (diff / (1000 * 60)).toInt()
            tempBasalPair.durationMinutes = (pumpStatus.tempBasalDuration!! - diffMin)

            DataCommandResponse(
                PumpCommandType.GetTemporaryBasal, true, null, tempBasalPair)
        }
    }

    override fun sendTemporaryBasal(value: Int, duration: Int): DataCommandResponse<AdditionalResponseDataInterface?> {
        pumpUtil.sleepSeconds(10)
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.SetTemporaryBasal)
    }

    override fun cancelTemporaryBasal(): DataCommandResponse<AdditionalResponseDataInterface?> {
        pumpUtil.sleepSeconds(10)
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.CancelTemporaryBasal)
    }

    override fun retrieveBasalProfile(): DataCommandResponse<BasalProfileDto?> {
        pumpUtil.sleepSeconds(10)
        return if (pumpStatus.basalsByHour==null) {
            DataCommandResponse(
                PumpCommandType.GetBasalProfile, true, null, null)
        } else {
            DataCommandResponse(
                PumpCommandType.GetBasalProfile, true, null, BasalProfileDto(pumpStatus.basalsByHour)
            )
        }
    }

    override fun sendBasalProfile(profile: Profile): DataCommandResponse<AdditionalResponseDataInterface?> {
        pumpUtil.sleepSeconds(10)
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.SetBasalProfile)
    }

    override fun retrieveRemainingInsulin(): DataCommandResponse<Double?> {
        return DataCommandResponse(
            PumpCommandType.GetRemainingInsulin, true, null, 100.0)
    }

    override fun retrieveConfiguration(): DataCommandResponse<MutableMap<PumpConfigurationTypeInterface, Any>?> {
        return DataCommandResponse(
            PumpCommandType.GetSettings, true, null, mutableMapOf())
    }

    override fun retrieveBatteryStatus(): DataCommandResponse<Int?> {
        return DataCommandResponse(
            PumpCommandType.GetBatteryStatus, true, null, 75)
    }

    override fun getPumpHistory(): DataCommandResponse<List<Any>?> {
        return DataCommandResponse(
            PumpCommandType.GetHistory, true, null, listOf())
    }

    override fun cancelBolus(): DataCommandResponse<AdditionalResponseDataInterface?> {
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.CancelBolus)
    }

    override fun getTime(): DataCommandResponse<PumpTimeDifferenceDto?> {
        return DataCommandResponse(
            PumpCommandType.GetTime, true, null, PumpTimeDifferenceDto(DateTime.now(), DateTime.now())
        )
    }

    override fun setTime(): DataCommandResponse<AdditionalResponseDataInterface?> {
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.SetTime)
    }

    override fun getFilteredPumpHistory(filter: PumpHistoryFilterInterface): DataCommandResponse<List<Any>?> {
        return DataCommandResponse(
            PumpCommandType.GetHistory, true, null, listOf())
    }

    override fun executeCustomCommand(commandType: CustomCommandTypeInterface): DataCommandResponse<AdditionalResponseDataInterface?> {
        return successfulResponseForSet.cloneWithNewCommandType(PumpCommandType.CustomCommand)
    }

    init {
        supportedCommandsList = setOf()
    }
}