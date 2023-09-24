package info.nightscout.androidaps.plugins.pump.medtronic.comm.ui

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil
import info.nightscout.pump.common.defs.PumpDeviceState
import org.joda.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

/**
 * Created by andy on 6/14/18.
 */
class MedtronicUITask {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var medtronicPumpStatus: MedtronicPumpStatus
    @Inject lateinit var medtronicUtil: MedtronicUtil

    private val injector: HasAndroidInjector
    var commandType: MedtronicCommandType
    var result: Any? = null
    var errorDescription: String? = null
    var parameters: List<Any?>? = null
    var responseType: MedtronicUIResponseType? = null

    constructor(injector: HasAndroidInjector, commandType: MedtronicCommandType) {
        this.injector = injector
        this.injector.androidInjector().inject(this)
        this.commandType = commandType
    }

    constructor(injector: HasAndroidInjector, commandType: MedtronicCommandType, parameters: List<Any>?) {
        this.injector = injector
        this.injector.androidInjector().inject(this)
        this.commandType = commandType
        this.parameters = parameters //as Array<Any>
    }

    fun execute(communicationManager: MedtronicCommunicationManager) {
        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. $commandType")
        when (commandType) {
            MedtronicCommandType.PumpModel                                   -> {
                result = communicationManager.getPumpModel()
            }

            MedtronicCommandType.GetBasalProfileSTD                          -> {
                result = communicationManager.getBasalProfile()
            }

            MedtronicCommandType.GetRemainingInsulin                         -> {
                result = communicationManager.getRemainingInsulin()
            }

            MedtronicCommandType.GetRealTimeClock                            -> {
                result = communicationManager.getPumpTime()
                //medtronicUtil.pumpTime = null
            }

            MedtronicCommandType.SetRealTimeClock                            -> {
                result = communicationManager.setPumpTime()
            }

            MedtronicCommandType.GetBatteryStatus                            -> {
                result = communicationManager.getRemainingBattery()
            }

            MedtronicCommandType.SetTemporaryBasal                           -> {
                val tbr = getTbrSettings()
                result = communicationManager.setTemporaryBasal(tbr)
            }

            MedtronicCommandType.ReadTemporaryBasal                          -> {
                result = communicationManager.getTemporaryBasal()
            }

            MedtronicCommandType.Settings, MedtronicCommandType.Settings_512 -> {
                result = communicationManager.getPumpSettings()
            }

            MedtronicCommandType.SetBolus                                    -> {
                val amount = getDoubleFromParameters(0)
                if (amount != null) result = communicationManager.setBolus(amount)
            }

            MedtronicCommandType.CancelTBR                                   -> {
                result = communicationManager.cancelTBR()
            }

            MedtronicCommandType.SetBasalProfileSTD,
            MedtronicCommandType.SetBasalProfileA                            -> {
                val profile = parameters!![0] as BasalProfile
                result = communicationManager.setBasalProfile(profile)
            }

            MedtronicCommandType.GetHistoryData                              -> {
                result = communicationManager.getPumpHistory(
                    parameters!![0] as PumpHistoryEntry?,
                    parameters!![1] as LocalDateTime?
                )
            }

            else                                                             -> {
                aapsLogger.warn(LTag.PUMP, String.format(Locale.ENGLISH, "This commandType is not supported (yet) - %s.", commandType))
                // invalid = true;
                responseType = MedtronicUIResponseType.Invalid
            }
        }
        if (responseType == null) {
            if (result == null) {
                errorDescription = communicationManager.errorResponse
                responseType = MedtronicUIResponseType.Error
            } else {
                responseType = MedtronicUIResponseType.Data
            }
        }
    }

    private fun getTbrSettings(): TempBasalPair {
        return TempBasalPair(
            getDoubleFromParameters(0)!!,  //
            false,  //
            getIntegerFromParameters(1)
        )
    }

    private fun getFloatFromParameters(index: Int): Float {
        return parameters!![index] as Float
    }

    fun getDoubleFromParameters(index: Int): Double? {
        return parameters!![index] as Double?
    }

    private fun getIntegerFromParameters(index: Int): Int {
        return parameters!![index] as Int
    }

    val isReceived: Boolean
        get() = result != null || errorDescription != null

    fun postProcess(postprocessor: MedtronicUIPostprocessor) {
        aapsLogger.debug(LTag.PUMP, "MedtronicUITask: @@@ In execute. $commandType")
        if (responseType === MedtronicUIResponseType.Data) {
            postprocessor.postProcessData(this)
        }
        if (responseType === MedtronicUIResponseType.Invalid) {
            rxBus.send(
                EventRileyLinkDeviceStatusChange(
                    PumpDeviceState.ErrorWhenCommunicating,
                    "Unsupported command in MedtronicUITask"
                )
            )
        } else if (responseType === MedtronicUIResponseType.Error) {
            rxBus.send(
                EventRileyLinkDeviceStatusChange(
                    PumpDeviceState.ErrorWhenCommunicating,
                    errorDescription
                )
            )
        } else {
            rxBus.send(EventMedtronicPumpValuesChanged())
            medtronicPumpStatus.setLastCommunicationToNow()
        }
        medtronicUtil.setCurrentCommand(null)
    }

    fun hasData(): Boolean {
        return responseType === MedtronicUIResponseType.Data
    }

    fun getParameter(index: Int): Any? {
        return parameters!![index]
    }

}