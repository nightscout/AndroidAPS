package app.aaps.core.interfaces.pump

import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.queue.CustomCommand
import org.json.JSONObject

/**
 * This interface defines the communication from AAPS-core to pump drivers.
 * Pump drivers communicate data changes back to AAPS-core using [app.aaps.core.interfaces.pump.PumpSync].
 *
 * Created by mike on 04.06.2016.
 */
interface Pump {

    /**
     * @return true if pump status has been read and is ready to accept commands
     */
    fun isInitialized(): Boolean

    /**
     * @return true if suspended (not delivering insulin)
     */
    fun isSuspended(): Boolean

    /**
     * @return true if pump is not ready to accept commands right now
     */
    fun isBusy(): Boolean

    /**
     * @return true if BT connection is established
     */
    fun isConnected(): Boolean

    /**
     * @return true if BT connection is in progress
     */
    fun isConnecting(): Boolean

    /**
     * @return true if BT is connected but initial handshake is still in progress
     */
    fun isHandshakeInProgress(): Boolean

    /**
     * set initial handshake completed (moved to connected state)
     */
    fun finishHandshaking() {}

    /**
     * Perform BT connect, there is new command waiting in queue
     * @param reason originator identification
     */
    fun connect(reason: String)

    /**
     * Perform BT disconnect, there is NO command waiting in queue
     * @param reason originator identification
     */
    fun disconnect(reason: String)

    /**
     * @return # of second to wait before [disconnect] is send after last command
     */
    fun waitForDisconnectionInSeconds(): Int = 5

    /**
     * Stop connection process
     */
    fun stopConnecting()

    /**
     * Force reading of full pump status
     * @param reason originator identification
     */
    fun getPumpStatus(reason: String)

    /**
     *  Upload to pump new basal profile (and IC/ISF if supported by pump)
     *
     *  @param profile new profile
     */
    fun setNewBasalProfile(profile: Profile): PumpEnactResult

    /**
     * @param profile profile to check
     *
     * @return true if pump is running the same profile as in param
     */
    fun isThisProfileSet(profile: Profile): Boolean

    /**
     * timestamp of last connection to the pump in milliseconds
     */
    val lastDataTime: Long

    /**
     * timestamp of last bolus delivered in milliseconds
     */
    val lastBolusTime: Long?

    /**
     * amount of last bolus delivered in units of insulin
     */
    val lastBolusAmount: Double?

    /**
     * Currently running base basal rate [U/h]
     *
     * This _must not_ be affected by current pump states
     * (TBRs, pump suspended/running etc.)
     */
    val baseBasalRate: Double

    /**
     * Reservoir level at time of last connection [Units of insulin]
     */
    val reservoirLevel: Double

    /**
     * Battery level at time of last connection [%]
     */
    val batteryLevel: Int?

    /**
     * Request a bolus to be delivered, carbs to be stored on pump or both.
     *
     * @param detailedBolusInfo it's the caller's responsibility to ensure the request can be satisfied by the pump,
     *                          e.g. DBI will not contain carbs if the pump can't store carbs.
     * @return PumpEnactResult
     */
    fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult

    /**
     *  Stopping of performed bolus requested by user
     */
    fun stopBolusDelivering()

    /**
     * Request a TRB in absolute units [U/h]
     *
     * Driver is responsible for conversion to % if absolute rate is not supported by pump
     *
     * @param absoluteRate          rate in U/h
     * @param durationInMinutes     duration
     * @param profile               only help for for U/h -> % transformation
     * @param enforceNew            if true drive should force new TBR (ie. stop current,
     *                              and set new even if the same rate is requested
     * @param tbrType               tbrType for storing to DB [NORMAL,EMULATED_PUMP_SUSPEND,PUMP_SUSPEND,SUPERBOLUS]
     * @return                      PumpEnactResult.success if TBR set,
     *                              PumpEnactResult.enacted if new TBR set
     *                              (if the same TBR rate is requested && enforceNew == false driver can keep
     *                              running TBR. In this case return will be success = true, enacted = false)
     */
    fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult

    /**
     * Request a TRB in %
     *
     * Driver is responsible for conversion to u/h if % is not supported by pump
     *
     * @param percent               rate in % (100% is equal to not running TBR, 0% is zero temping)
     * @param durationInMinutes     duration
     * @param profile               only help for for U/h -> % transformation
     * @param enforceNew            if true drive should force new TBR (ie. stop current,
     *                              and set new even if the same rate is requested
     * @param tbrType               tbrType for storing to DB [NORMAL,EMULATED_PUMP_SUSPEND,PUMP_SUSPEND,SUPERBOLUS]
     * @return                      PumpEnactResult.success if TBR set,
     *                              PumpEnactResult.enacted if new TBR set
     *                              (if the same TBR rate is requested && enforceNew == false driver can keep
     *                              running TBR. In this case return will be success = true, enacted = false)
     */
    fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: PumpSync.TemporaryBasalType): PumpEnactResult

    /**
     * Cancel current TBR if a TBR is running
     *
     * some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
     * when the cancel request is requested by the user (forced), the pump should always do a real cancel
     *
     * @param enforceNew            if true disable workaround above
     * @return                      PumpEnactResult.success if TBR is canceled
     */
    fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult

    fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult
    fun cancelExtendedBolus(): PumpEnactResult

    /**
     * Status to be passed to NS.
     *
     * This info is displayed when user hover over pump pill in NS.
     * Except common information every driver can add own info here.
     */
    fun updateExtendedJsonStatus(extendedStatus: JSONObject) {}

    /**
     *  Manufacturer type. Usually defined by used plugin
     *
     *  @return ManufacturerType
     */
    fun manufacturer(): ManufacturerType

    /**
     * Pump model
     *
     * If new model is covered by driver, model and it's capabilities must be added to [app.aaps.core.data.pump.defs.PumpType]
     *
     * @return PumpType
     */
    fun model(): PumpType

    /**
     * Serial number
     *
     * Real serial number from device or "unique" generated for paired pump if not possible
     */
    fun serialNumber(): String

    /**
     * Pump capabilities
     */
    val pumpDescription: PumpDescription

    /**
     * Short info for SMS, Wear etc. specific to the pump driver.
     * It will be added to the general part generated by AAPS.
     * Expected format: "XXX: YYY\nZZZ: AAA"
     */
    fun pumpSpecificShortStatus(veryShort: Boolean): String = ""

    /**
     * @return true if pump is currently emulating temporary basals by extended boluses (usually to bypass 200% limit)
     */
    val isFakingTempsByExtendedBoluses: Boolean

    /**
     * Load TDDs and store them to the database
     */
    fun loadTDDs(): PumpEnactResult

    /**
     * @return true if pump handles DST changes by it self. In this case it's not necessary stop the loop
     *                  after DST change
     */
    fun canHandleDST(): Boolean

    /**
     * Provides a list of custom actions to be displayed in the Actions tab.
     * Please note that these actions will not be queued upon execution
     *
     * @return list of custom actions
     */
    fun getCustomActions(): List<CustomAction>? = null

    /**
     * Executes a custom action. Please note that these actions will not be queued
     *
     * @param customActionType action to be executed
     */
    fun executeCustomAction(customActionType: CustomActionType) {}

    /**
     * Executes a custom queued command
     * See [app.aaps.core.interfaces.queue.CustomCommand] for queuing a custom command.
     *
     * @param customCommand the custom command to be executed
     * @return PumpEnactResult that represents the command execution result
     */
    fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? = null

    /**
     * This method will be called when time or Timezone changes, and pump driver can then do a specific action (for
     * example update clock on pump).
     */
    fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}

    /**
     * Only used for pump types where hasCustomUnreachableAlertCheck=true
     */
    fun isUnreachableAlertTimeoutExceeded(unreachableTimeoutMilliseconds: Long): Boolean = false

    /**
     * if true APS set 100% basal before full hour to avoid pump beeping
     */
    fun setNeutralTempAtFullHour(): Boolean = false

    /**
     * Pumps with a hardware link can set via config "Battery Change Logging"
     */
    fun isBatteryChangeLoggingEnabled(): Boolean = false

    /**
     * Pump is connected via RileyLink
     */
    fun isUseRileyLinkBatteryLevel(): Boolean = false
}
