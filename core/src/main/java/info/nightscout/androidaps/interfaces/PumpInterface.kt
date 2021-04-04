package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.utils.TimeChangeType
import org.json.JSONObject

/**
 * This interface defines the communication from AAPS-core to pump drivers.
 * Pump drivers communicate data changes back to AAPS-core using [info.nightscout.androidaps.interfaces.PumpSync].
 *
 * Created by mike on 04.06.2016.
 */
interface PumpInterface {

    fun isInitialized(): Boolean      // true if pump status has been read and is ready to accept commands
    fun isSuspended(): Boolean        // true if suspended (not delivering insulin)
    fun isBusy(): Boolean             // if true pump is not ready to accept commands right now
    fun isConnected(): Boolean        // true if BT connection is established
    fun isConnecting(): Boolean       // true if BT connection is in progress
    fun isHandshakeInProgress(): Boolean      // true if BT is connected but initial handshake is still in progress
    @JvmDefault fun finishHandshaking() {}  // set initial handshake completed
    fun connect(reason: String)
    fun disconnect(reason: String)
    @JvmDefault fun waitForDisconnectionInSeconds(): Int = 5 // wait [x] second after last command before sending disconnect
    fun stopConnecting()
    fun getPumpStatus(reason: String)

    // Upload to pump new basal profile
    fun setNewBasalProfile(profile: Profile): PumpEnactResult
    fun isThisProfileSet(profile: Profile): Boolean
    fun lastDataTime(): Long

    val baseBasalRate: Double  // base basal rate, not temp basal
    val reservoirLevel: Double
    val batteryLevel: Int  // in percent as integer

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
     * Status to be passed to NS
     *
     * This info is displayed when user hover over pump pill in NS
     *
     * @return                      JSON with information
     */
    fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject

    /**
     *  Manufacturer type. Usually defined by used plugin
     *
     *  @return ManufacturerType
     */
    fun manufacturer(): ManufacturerType

    /**
     * Pump model
     *
     * If new model is covered by driver, model and it's capabilities must be added to [info.nightscout.androidaps.plugins.pump.common.defs.PumpType]
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

    // Pump capabilities
    val pumpDescription: PumpDescription

    // Short info for SMS, Wear etc
    fun shortStatus(veryShort: Boolean): String
    val isFakingTempsByExtendedBoluses: Boolean
    fun loadTDDs(): PumpEnactResult
    fun canHandleDST(): Boolean

    /**
     * Provides a list of custom actions to be displayed in the Actions tab.
     * Please note that these actions will not be queued upon execution
     *
     * @return list of custom actions
     */
    @JvmDefault fun getCustomActions(): List<CustomAction>? = null

    /**
     * Executes a custom action. Please note that these actions will not be queued
     *
     * @param customActionType action to be executed
     */
    @JvmDefault fun executeCustomAction(customActionType: CustomActionType) {}

    /**
     * Executes a custom queued command
     * See [CommandQueueProvider.customCommand] for queuing a custom command.
     *
     * @param customCommand the custom command to be executed
     * @return PumpEnactResult that represents the command execution result
     */
    @JvmDefault fun executeCustomCommand(customCommand: CustomCommand): PumpEnactResult? = null

    /**
     * This method will be called when time or Timezone changes, and pump driver can then do a specific action (for
     * example update clock on pump).
     */
    @JvmDefault fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {}

    /* Only used for pump types where hasCustomUnreachableAlertCheck=true */
    @JvmDefault
    fun isUnreachableAlertTimeoutExceeded(alertTimeoutMilliseconds: Long): Boolean = false

    @JvmDefault fun setNeutralTempAtFullHour(): Boolean = false
}