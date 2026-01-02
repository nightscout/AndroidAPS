package app.aaps.pump.medtronic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.actions.CustomAction
import app.aaps.core.interfaces.pump.actions.CustomActionType
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshButtonState
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventSWRLStatus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.DateTimeUtil
import app.aaps.core.validators.DefaultEditTextValidator
import app.aaps.core.validators.EditTextValidator
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveListIntPreference
import app.aaps.core.validators.preferences.AdaptiveListPreference
import app.aaps.core.validators.preferences.AdaptiveStringPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.common.PumpPluginAbstract
import app.aaps.pump.common.data.PumpStatus
import app.aaps.pump.common.defs.PumpDriverState
import app.aaps.pump.common.dialog.RileyLinkBLEConfigActivity
import app.aaps.pump.common.events.EventRileyLinkDeviceStatusChange
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkTargetFrequency
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpDevice
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkPumpInfo
import app.aaps.pump.common.hw.rileylink.defs.RileyLinkServiceState
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkDoubleKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkIntentPreferenceKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkLongKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringKey
import app.aaps.pump.common.hw.rileylink.keys.RileyLinkStringPreferenceKey
import app.aaps.pump.common.hw.rileylink.keys.RileylinkBooleanPreferenceKey
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask
import app.aaps.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor
import app.aaps.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask
import app.aaps.pump.common.sync.PumpDbEntryTBR
import app.aaps.pump.common.sync.PumpSyncEntriesCreator
import app.aaps.pump.common.sync.PumpSyncStorage
import app.aaps.pump.common.utils.ProfileUtil
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryEntry
import app.aaps.pump.medtronic.comm.history.pump.PumpHistoryResult
import app.aaps.pump.medtronic.data.MedtronicHistoryData
import app.aaps.pump.medtronic.data.dto.BasalProfile
import app.aaps.pump.medtronic.data.dto.BasalProfile.Companion.getProfilesByHourToString
import app.aaps.pump.medtronic.data.dto.BasalProfileEntry
import app.aaps.pump.medtronic.data.dto.TempBasalPair
import app.aaps.pump.medtronic.defs.BasalProfileStatus
import app.aaps.pump.medtronic.defs.BatteryType
import app.aaps.pump.medtronic.defs.MedtronicCommandType
import app.aaps.pump.medtronic.defs.MedtronicCommandType.Companion.getSettings
import app.aaps.pump.medtronic.defs.MedtronicCustomActionType
import app.aaps.pump.medtronic.defs.MedtronicNotificationType
import app.aaps.pump.medtronic.defs.MedtronicStatusRefreshType
import app.aaps.pump.medtronic.defs.MedtronicUIResponseType
import app.aaps.pump.medtronic.driver.MedtronicPumpStatus
import app.aaps.pump.medtronic.events.EventMedtronicPumpConfigurationChanged
import app.aaps.pump.medtronic.events.EventMedtronicPumpValuesChanged
import app.aaps.pump.medtronic.keys.MedtronicBooleanPreferenceKey
import app.aaps.pump.medtronic.keys.MedtronicIntPreferenceKey
import app.aaps.pump.medtronic.keys.MedtronicLongNonKey
import app.aaps.pump.medtronic.keys.MedtronicStringPreferenceKey
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService
import app.aaps.pump.medtronic.util.MedtronicUtil
import app.aaps.pump.medtronic.util.MedtronicUtil.Companion.isSame
import org.joda.time.LocalDateTime
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.floor

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
class MedtronicPumpPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    rxBus: RxBus,
    context: Context,
    activePlugin: ActivePlugin,
    fabricPrivacy: FabricPrivacy,
    private val medtronicUtil: MedtronicUtil,
    private val medtronicPumpStatus: MedtronicPumpStatus,
    private val medtronicHistoryData: MedtronicHistoryData,
    private val rileyLinkServiceData: RileyLinkServiceData,
    private val serviceTaskExecutor: ServiceTaskExecutor,
    private val uiInteraction: UiInteraction,
    aapsSchedulers: AapsSchedulers,
    pumpSync: PumpSync,
    pumpSyncStorage: PumpSyncStorage,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val wakeAndTuneTaskProvider: Provider<WakeAndTuneTask>,
    private val resetRileyLinkConfigurationTaskProvider: Provider<ResetRileyLinkConfigurationTask>
) : PumpPluginAbstract(
    pluginDescription = PluginDescription()
        .mainType(PluginType.PUMP)
        .fragmentClass(MedtronicFragment::class.java.name)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_veo_128)
        .pluginName(R.string.medtronic_name)
        .shortName(R.string.medtronic_name_short)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN)
        .description(R.string.description_pump_medtronic),
    ownPreferences = listOf(
        RileylinkBooleanPreferenceKey::class.java, RileyLinkDoubleKey::class.java, RileyLinkIntentPreferenceKey::class.java,
        RileyLinkLongKey::class.java, RileyLinkStringKey::class.java, RileyLinkStringPreferenceKey::class.java,
        MedtronicBooleanPreferenceKey::class.java, MedtronicIntPreferenceKey::class.java,
        MedtronicLongNonKey::class.java, MedtronicStringPreferenceKey::class.java
    ),
    PumpType.MEDTRONIC_522_722,  // we default to most basic model, correct model from config is loaded later
    rh, aapsLogger, preferences, commandQueue, rxBus, context, fabricPrivacy, aapsSchedulers, pumpSync, pumpSyncStorage, pumpEnactResultProvider
), Pump, RileyLinkPumpDevice, PumpSyncEntriesCreator {

    private var rileyLinkMedtronicService: RileyLinkMedtronicService? = null

    // variables for handling statuses and history
    private var firstRun = true
    private var isRefresh = false
    private val statusRefreshMap: MutableMap<MedtronicStatusRefreshType, Long> = mutableMapOf()
    private var isInitialized = false
    private var lastPumpHistoryEntry: PumpHistoryEntry? = null
    private val busyTimestamps: MutableList<Long> = ArrayList()
    private var hasTimeDateOrTimeZoneChanged = false
    private var isBusy = false

    override fun onStart() {
        aapsLogger.debug(LTag.PUMP, deviceID() + " started. (V2.0006)")
        serviceConnection = object : ServiceConnection {
            override fun onServiceDisconnected(name: ComponentName) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is disconnected")
                //rileyLinkMedtronicService = null
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is connected")
                val mLocalBinder = service as RileyLinkMedtronicService.LocalBinder
                rileyLinkMedtronicService = mLocalBinder.serviceInstance
                isServiceSet = true
                rileyLinkMedtronicService?.verifyConfiguration()
                Thread {
                    for (i in 0..19) {
                        SystemClock.sleep(5000)
                        aapsLogger.debug(LTag.PUMP, "Starting Medtronic-RileyLink service")
                        if (rileyLinkMedtronicService?.setNotInPreInit() == true) {
                            break
                        }
                    }
                }.start()
            }
        }
        // Pass only to setup wizard
        disposable.add(
            rxBus
                .toObservable(EventRileyLinkDeviceStatusChange::class.java)
                .observeOn(aapsSchedulers.io)
                .subscribe({ event: EventRileyLinkDeviceStatusChange -> rxBus.send(EventSWRLStatus(event.getStatus(context))) }, fabricPrivacy::logException)
        )
        super.onStart()
    }

    override fun initPumpStatusData() {
        medtronicPumpStatus.lastConnection = preferences.get(RileyLinkLongKey.LastGoodDeviceCommunicationTime)
        medtronicPumpStatus.lastDataTime = medtronicPumpStatus.lastConnection
        medtronicPumpStatus.previousConnection = medtronicPumpStatus.lastConnection

        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: $medtronicPumpStatus")

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = medtronicPumpStatus.maxBasal ?: MedtronicIntPreferenceKey.MaxBasal.defaultValue.toDouble()

        // set first Medtronic Pump Start
        if (preferences.getIfExists(MedtronicLongNonKey.FirstPumpUse) == null)
            preferences.put(MedtronicLongNonKey.FirstPumpUse, System.currentTimeMillis())
        migrateSettings()

        pumpSyncStorage.initStorage()

        this.displayConnectionMessages = false
    }

    override fun triggerPumpConfigurationChangedEvent() {
        rxBus.send(EventMedtronicPumpConfigurationChanged())
    }

    private fun migrateSettings() {
        if (preferences.get(MedtronicStringPreferenceKey.PumpFrequency) == "US (916 MHz)")
            preferences.put(MedtronicStringPreferenceKey.PumpFrequency, pumpFreqValues[0].toString())
        val encoding = preferences.get(RileyLinkStringPreferenceKey.Encoding)
        if (encoding == "RileyLink 4b6b Encoding")
            preferences.put(RileyLinkStringPreferenceKey.Encoding, encodingValues[1].toString())
        if (encoding == "Local 4b6b Encoding")
            preferences.put(RileyLinkStringPreferenceKey.Encoding, encodingValues[0].toString())
    }

    override fun onStartScheduledPumpActions() {

        // check status every minute (if any status needs refresh we send readStatus command)
        Thread {
            do {
                SystemClock.sleep(60000)
                if (this.isInitialized) {
                    val statusRefresh = synchronized(statusRefreshMap) { HashMap(statusRefreshMap) }
                    if (doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
                        if (!commandQueue.statusInQueue()) {
                            commandQueue.readStatus(rh.gs(R.string.scheduled_status_refresh), null)
                        }
                    }
                    clearBusyQueue()
                }
            } while (serviceRunning)
        }.start()
    }

    override val serviceClass: Class<*> = RileyLinkMedtronicService::class.java
    override val pumpStatusData: PumpStatus get() = medtronicPumpStatus
    override fun deviceID(): String = "Medtronic"
    override val isFakingTempsByExtendedBoluses: Boolean = false
    override fun canHandleDST(): Boolean = false
    private var isServiceSet: Boolean = false
    override val rileyLinkService: RileyLinkMedtronicService? get() = rileyLinkMedtronicService

    override val pumpInfo: RileyLinkPumpInfo
        get() = RileyLinkPumpInfo(
            rileyLinkServiceData.rileyLinkTargetFrequency.friendlyName?.let { rh.gs(it) } ?: "UNKNOWN",
            if (!medtronicUtil.isModelSet) "???" else "Medtronic " + medtronicPumpStatus.medtronicDeviceType.pumpModel,
            medtronicPumpStatus.serialNumber
        )

    override val lastConnectionTimeMillis: Long get() = medtronicPumpStatus.lastConnection

    override fun setLastCommunicationToNow() {
        medtronicPumpStatus.setLastCommunicationToNow()
    }

    override fun isInitialized(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isInitialized")
        return isServiceSet && isInitialized
    }

    override fun setBusy(busy: Boolean) {
        isBusy = busy
    }

    override fun isBusy(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isBusy")
        if (isServiceSet) {
            if (isBusy) return true
            if (this.busyTimestamps.isNotEmpty()) {
                clearBusyQueue()
                return this.busyTimestamps.isNotEmpty()
            }
        }
        return false
    }

    @Synchronized
    private fun clearBusyQueue() {
        if (busyTimestamps.isEmpty()) {
            return
        }
        val deleteFromQueue: MutableSet<Long> = HashSet()
        for (busyTimestamp in busyTimestamps) {
            if (System.currentTimeMillis() > busyTimestamp) {
                deleteFromQueue.add(busyTimestamp)
            }
        }
        if (deleteFromQueue.size == busyTimestamps.size) {
            busyTimestamps.clear()
            setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, false)
        }
        if (deleteFromQueue.isNotEmpty()) {
            busyTimestamps.removeAll(deleteFromQueue)
        }
    }

    override fun isConnected(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnected")
        return isServiceSet && rileyLinkMedtronicService?.isInitialized == true
    }

    override fun isConnecting(): Boolean {
        if (displayConnectionMessages) aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnecting")
        return !isServiceSet || rileyLinkMedtronicService?.isInitialized != true
    }

    override fun getPumpStatus(reason: String) {
        var needRefresh = true
        if (firstRun) {
            needRefresh = initializePump()  /*!isRefresh*/
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed()
        }
        if (needRefresh) rxBus.send(EventMedtronicPumpValuesChanged())
    }

    fun resetStatusState() {
        firstRun = true
        isRefresh = true
    }//

    private val isPumpNotReachable: Boolean
        get() {
            val rileyLinkServiceState = rileyLinkServiceData.rileyLinkServiceState
            if (rileyLinkServiceState != RileyLinkServiceState.PumpConnectorReady
                && rileyLinkServiceState != RileyLinkServiceState.RileyLinkReady
                && rileyLinkServiceState != RileyLinkServiceState.TuneUpDevice
            ) {
                aapsLogger.debug(LTag.PUMP, "RileyLink unreachable.")
                return false
            }
            return rileyLinkMedtronicService?.deviceCommunicationManager?.isDeviceReachable() != true
        }

    private fun refreshAnyStatusThatNeedsToBeRefreshed() {
        val statusRefresh = synchronized(statusRefreshMap) { HashMap(statusRefreshMap) }
        if (!doWeHaveAnyStatusNeededRefreshing(statusRefresh)) {
            return
        }
        var resetTime = false
        if (isPumpNotReachable) {
            aapsLogger.error("Pump unreachable.")
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, rh)
            return
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        if (hasTimeDateOrTimeZoneChanged) {
            checkTimeAndOptionallySetTime()

            // read time if changed, set new time
            hasTimeDateOrTimeZoneChanged = false
        }

        // execute
        val refreshTypesNeededToReschedule: MutableSet<MedtronicStatusRefreshType> = mutableSetOf()
        for ((key, value) in statusRefresh) {
            if (value > 0 && System.currentTimeMillis() > value) {
                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                when (key) {
                    MedtronicStatusRefreshType.PumpHistory                                                -> {
                        readPumpHistory()
                    }

                    MedtronicStatusRefreshType.PumpTime                                                   -> {
                        checkTimeAndOptionallySetTime()
                        refreshTypesNeededToReschedule.add(key)
                        resetTime = true
                    }

                    MedtronicStatusRefreshType.BatteryStatus, MedtronicStatusRefreshType.RemainingInsulin -> {
                        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(key.getCommandType(medtronicUtil.medtronicPumpModel)!!)
                        refreshTypesNeededToReschedule.add(key)
                        resetTime = true
                    }

                    MedtronicStatusRefreshType.Configuration                                              -> {
                        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(key.getCommandType(medtronicUtil.medtronicPumpModel)!!)
                        resetTime = true
                    }
                }
            }

            // reschedule
            for (refreshType2 in refreshTypesNeededToReschedule) {
                scheduleNextRefresh(refreshType2)
            }
        }

        if (resetTime) medtronicPumpStatus.setLastCommunicationToNow()
    }

    private fun doWeHaveAnyStatusNeededRefreshing(statusRefresh: Map<MedtronicStatusRefreshType, Long>): Boolean {
        for ((_, value) in statusRefresh) {
            if (value > 0 && System.currentTimeMillis() > value) {
                return true
            }
        }
        return hasTimeDateOrTimeZoneChanged
    }

    private fun setRefreshButtonEnabled(enabled: Boolean) {
        rxBus.send(EventRefreshButtonState(enabled))
    }

    private fun initializePump(): Boolean {
        if (!isServiceSet) return false
        aapsLogger.info(LTag.PUMP, "initializePump - start")
        rileyLinkMedtronicService?.deviceCommunicationManager?.setDoWakeUpBeforeCommand(false)
        setRefreshButtonEnabled(false)
        if (isRefresh) {
            if (isPumpNotReachable) {
                aapsLogger.error("initializePump::Pump unreachable.")
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, rh)
                setRefreshButtonEnabled(true)
                return true
            }
            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        }

        // model (once)
        if (!medtronicUtil.isModelSet) {
            rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.PumpModel)
        } else {
            if (medtronicPumpStatus.medtronicDeviceType !== medtronicUtil.medtronicPumpModel) {
                aapsLogger.warn(LTag.PUMP, "Configured pump is not the same as one detected.")
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, rh)
            }
        }
        pumpState = PumpDriverState.Connected

        // time (1h)
        checkTimeAndOptionallySetTime()
        readPumpHistory()

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetRemainingInsulin)
        scheduleNextRefresh(MedtronicStatusRefreshType.RemainingInsulin, 10)

        // remaining power (1h)
        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetBatteryStatus)
        scheduleNextRefresh(MedtronicStatusRefreshType.BatteryStatus, 20)

        // configuration (once and then if history shows config changes)
        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(getSettings(medtronicUtil.medtronicPumpModel))

        // read profile (once, later its controlled by isThisProfileSet method)
        basalProfiles
        val errorCount = rileyLinkMedtronicService?.medtronicUIComm?.invalidResponsesCount ?: 0
        if (errorCount >= 5) {
            aapsLogger.error("Number of error counts was 5 or more. Starting tuning.")
            setRefreshButtonEnabled(true)
            serviceTaskExecutor.startTask(wakeAndTuneTaskProvider.get())
            return true
        }
        medtronicPumpStatus.setLastCommunicationToNow()
        setRefreshButtonEnabled(true)
        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized
        }
        isInitialized = true
        // this.pumpState = PumpDriverState.Initialized;
        firstRun = false
        return true
    }

    private val basalProfiles: Unit
        get() {
            val medtronicUITask = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetBasalProfileSTD)
            if (medtronicUITask?.responseType === MedtronicUIResponseType.Error) {
                rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetBasalProfileSTD)
            }
        }

    @Synchronized
    override fun isThisProfileSet(profile: Profile): Boolean {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet: basalInitialized=" + medtronicPumpStatus.basalProfileStatus)
        if (!isInitialized) return true
        if (medtronicPumpStatus.basalProfileStatus === BasalProfileStatus.NotInitialized) {
            // this shouldn't happen, but if there was problem we try again
            basalProfiles
            return isProfileSame(profile)
        } else if (medtronicPumpStatus.basalProfileStatus === BasalProfileStatus.ProfileChanged) {
            return false
        }
        return medtronicPumpStatus.basalProfileStatus !== BasalProfileStatus.ProfileOK || isProfileSame(profile)
    }

    private fun isProfileSame(profile: Profile): Boolean {
        var invalid = false
        val basalsByHour: DoubleArray? = medtronicPumpStatus.basalsByHour
        aapsLogger.debug(
            LTag.PUMP, "Current Basals (h):   "
                + (basalsByHour?.let { getProfilesByHourToString(it) } ?: "null"))

        // int index = 0;
        if (basalsByHour == null) return true // we don't want to set profile again, unless we are sure
        val stringBuilder = StringBuilder("Requested Basals (h): ")
        stringBuilder.append(ProfileUtil.getBasalProfilesDisplayableAsStringOfArray(profile, this.pumpType))

        for (basalValue in profile.getBasalValues()) {
            val basalValueValue = pumpDescription.pumpType.determineCorrectBasalSize(basalValue.value)
            val hour = basalValue.timeAsSeconds / (60 * 60)
            if (!isSame(basalsByHour[hour], basalValueValue)) {
                invalid = true
            }
            // stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue))
            // stringBuilder.append(" ")
        }
        aapsLogger.debug(LTag.PUMP, stringBuilder.toString())
        if (!invalid) {
            aapsLogger.debug(LTag.PUMP, "Basal profile is same as AAPS one.")
        } else {
            aapsLogger.debug(LTag.PUMP, "Basal profile on Pump is different than the AAPS one.")
        }
        return !invalid
    }

    override val lastDataTime: Long get() = medtronicPumpStatus.lastConnection
    override val lastBolusTime: Long? get() = null
    override val lastBolusAmount: Double? get() = null
    override val baseBasalRate: Double get() = medtronicPumpStatus.basalProfileForHour
    override val reservoirLevel: Double get() = medtronicPumpStatus.reservoirRemainingUnits
    override val batteryLevel: Int? get() = medtronicPumpStatus.batteryRemaining

    override fun triggerUIChange() {
        rxBus.send(EventMedtronicPumpValuesChanged())
    }

    override fun generateTempId(objectA: Any): Long {
        val timestamp: Long = objectA as Long
        return DateTimeUtil.toATechDate(timestamp)
    }

    private var bolusDeliveryType = BolusDeliveryType.Idle

    private enum class BolusDeliveryType {
        Idle,  //
        DeliveryPrepared,  //
        Delivering,  //
        CancelDelivery
    }

    private fun checkTimeAndOptionallySetTime() {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start")
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.")
            setRefreshButtonEnabled(true)
            return
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetRealTimeClock)
        var clock = medtronicUtil.pumpTime
        if (clock == null) { // retry
            rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.GetRealTimeClock)
            clock = medtronicUtil.pumpTime
        }
        if (clock == null) return
        val timeDiff = abs(clock.timeDifference)
        if (timeDiff > 20) {
            if (clock.localDeviceTime.year <= 2015 || timeDiff <= 24 * 60 * 60) {
                aapsLogger.info(LTag.PUMP, String.format(Locale.ENGLISH, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is %d s. Set time on pump.", timeDiff))
                rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.SetRealTimeClock)
                if (clock.timeDifference == 0) {
                    uiInteraction.addNotificationValidFor(Notification.INSIGHT_DATE_TIME_UPDATED, rh.gs(R.string.pump_time_updated), Notification.INFO, 60)
                }
            } else {
                if (clock.localDeviceTime.year > 2015) {
                    aapsLogger.error(String.format(Locale.ENGLISH, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff=%d s]. Doing nothing.", timeDiff))
                    medtronicUtil.sendNotification(MedtronicNotificationType.TimeChangeOver24h, rh)
                }
            }
        } else {
            aapsLogger.info(LTag.PUMP, String.format(Locale.ENGLISH, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is %d s. Do nothing.", timeDiff))
        }
        scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, 0)
    }

    @Synchronized
    override fun deliverBolus(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared)
        setRefreshButtonEnabled(false)
        if (detailedBolusInfo.insulin > medtronicPumpStatus.reservoirRemainingUnits) {
            return pumpEnactResultProvider.get() //
                .success(false) //
                .enacted(false) //
                .comment(
                    rh.gs(
                        R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                        medtronicPumpStatus.reservoirRemainingUnits,
                        detailedBolusInfo.insulin
                    )
                )
        }
        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared
        if (isPumpNotReachable) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.")
            return setNotReachable(isBolus = true, success = false)
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            return setNotReachable(isBolus = true, success = true)
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");
        val sleepTime = preferences.get(MedtronicIntPreferenceKey.BolusDelay) * 1000
        SystemClock.sleep(sleepTime.toLong())
        return if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            setNotReachable(isBolus = true, success = true)
        } else try {
            bolusDeliveryType = BolusDeliveryType.Delivering

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");
            val responseTask = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(
                MedtronicCommandType.SetBolus,
                arrayListOf(detailedBolusInfo.insulin)
            )
            val response = responseTask?.result as Boolean?
            setRefreshButtonEnabled(true)

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);
            return if (response == null || !response) {
                pumpEnactResultProvider.get() //
                    .success(bolusDeliveryType == BolusDeliveryType.CancelDelivery) //
                    .enacted(false) //
                    .comment(R.string.medtronic_cmd_bolus_could_not_be_delivered)
            } else {
                if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");
                    Thread {
                        SystemClock.sleep(2000)
                        uiInteraction.runAlarm(rh.gs(R.string.medtronic_cmd_cancel_bolus_not_supported), rh.gs(R.string.medtronic_warning), app.aaps.core.ui.R.raw.boluserror)
                    }.start()
                }
                val now = System.currentTimeMillis()

                detailedBolusInfo.timestamp = now

                pumpSyncStorage.addBolusWithTempId(detailedBolusInfo, true, this)

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medtronicPumpStatus.reservoirRemainingUnits = medtronicPumpStatus.reservoirRemainingUnits - detailedBolusInfo.insulin
                preferences.inc(if (detailedBolusInfo.bolusType === BS.Type.SMB) MedtronicLongNonKey.SmbBoluses else MedtronicLongNonKey.StandardBoluses)

                // calculate time for bolus and set driver to busy for that time
                val bolusTime = (detailedBolusInfo.insulin * 42.0).toInt()
                val time = now + bolusTime * 1000
                busyTimestamps.add(time)
                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true)
                pumpEnactResultProvider.get().success(true).enacted(true).bolusDelivered(detailedBolusInfo.insulin)
            }
        } finally {
            finishAction("Bolus")
            bolusDeliveryType = BolusDeliveryType.Idle
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");
    }

    @Suppress("SameParameterValue")
    private fun setNotReachable(isBolus: Boolean, success: Boolean): PumpEnactResult {
        setRefreshButtonEnabled(true)
        if (isBolus) bolusDeliveryType = BolusDeliveryType.Idle
        return if (success) pumpEnactResultProvider.get().success(true).enacted(false)
        else pumpEnactResultProvider.get().success(false).enacted(false).comment(R.string.medtronic_pump_status_pump_unreachable)
    }

    override fun stopBolusDelivering() {
        bolusDeliveryType = BolusDeliveryType.CancelDelivery

        // if (isLoggingEnabled())
        // LOG.warn("MedtronicPumpPlugin::deliverBolus - Stop Bolus Delivery.");
    }

    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Synchronized
    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            return pumpEnactResultProvider.get() //
                .success(false) //
                .enacted(false) //
                .comment(R.string.medtronic_pump_status_pump_unreachable)
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: rate: " + absoluteRate + ", duration=" + durationInMinutes)

        // read current TBR
        val tbrCurrent = readTBR()
        if (tbrCurrent == null) {
            aapsLogger.warn(LTag.PUMP, "setTempBasalAbsolute - Could not read current TBR, canceling operation.")
            finishAction("TBR")
            return pumpEnactResultProvider.get().success(false).enacted(false)
                .comment(R.string.medtronic_cmd_cant_read_tbr)
        } else {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: Current Basal: duration: " + tbrCurrent.durationMinutes + " min, rate=" + tbrCurrent.insulinRate)
        }
        if (!enforceNew) {
            if (isSame(tbrCurrent.insulinRate, absoluteRate)) {
                var sameRate = true
                if (isSame(0.0, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false
                }
                if (sameRate) {
                    aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - No enforceNew and same rate. Exiting.")
                    finishAction("TBR")
                    return pumpEnactResultProvider.get().success(true).enacted(false)
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.insulinRate > 0.0 && tbrCurrent.durationMinutes > 0) {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - TBR running - so canceling it.")

            // CANCEL
            val responseTask2 = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.CancelTBR)
            val response = responseTask2?.result as Boolean?
            if (response == null || !response) {
                aapsLogger.error("setTempBasalAbsolute - Cancel TBR failed.")
                finishAction("TBR")
                return pumpEnactResultProvider.get().success(false).enacted(false)
                    .comment(R.string.medtronic_cmd_cant_cancel_tbr_stop_op)
            } else {
                //cancelTBRWithTemporaryId()
                aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - Current TBR cancelled.")
            }
        }

        // now start new TBR
        val responseTask = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(
            MedtronicCommandType.SetTemporaryBasal,
            arrayListOf(absoluteRate, durationInMinutes)
        )
        val response = responseTask?.result as Boolean?
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - setTBR. Response: " + response)
        return if (response == null || !response) {
            finishAction("TBR")
            pumpEnactResultProvider.get().success(false).enacted(false) //
                .comment(R.string.medtronic_cmd_tbr_could_not_be_delivered)
        } else {
            medtronicPumpStatus.tempBasalStart = System.currentTimeMillis()
            medtronicPumpStatus.tempBasalAmount = absoluteRate
            medtronicPumpStatus.tempBasalLength = durationInMinutes

            val tempData = PumpDbEntryTBR(absoluteRate, true, durationInMinutes * 60, tbrType)

            medtronicPumpStatus.runningTBRWithTemp = tempData
            pumpSyncStorage.addTemporaryBasalRateWithTempId(tempData, true, this)

            preferences.inc(MedtronicLongNonKey.TbrsSet)
            finishAction("TBR")
            pumpEnactResultProvider.get().success(true).enacted(true) //
                .absolute(absoluteRate).duration(durationInMinutes)
        }
    }

    @Deprecated("Not used, TBRs fixed in history, should be removed.")
    private fun cancelTBRWithTemporaryId() {
        val tbrs: MutableList<PumpDbEntryTBR> = pumpSyncStorage.getTBRs()
        if (tbrs.isNotEmpty() && medtronicPumpStatus.runningTBRWithTemp != null) {
            aapsLogger.info(LTag.PUMP, "cancelTBRWithTemporaryId - TBR items: ${tbrs.size}")

            var item: PumpDbEntryTBR? = null

            if (tbrs.size == 1) {
                item = tbrs[0]
            } else {
                for (tbr in tbrs) {
                    if (tbr.date == medtronicPumpStatus.runningTBRWithTemp!!.date) {
                        item = tbr
                        break
                    }
                }
            }

            if (item != null) {

                aapsLogger.debug(LTag.PUMP, "DD: cancelTBRWithTemporaryId: tempIdEntry=${item}")

                val differenceS = (System.currentTimeMillis() - item.date) / 1000

                aapsLogger.debug(
                    LTag.PUMP, "syncTemporaryBasalWithTempId " +
                        "[date=${item.date}, " +
                        "rate=${item.rate}, " +
                        "duration=${differenceS} s, " +
                        "isAbsolute=${!item.isAbsolute}, temporaryId=${item.temporaryId}, " +
                        "pumpId=NO, pumpType=${medtronicPumpStatus.pumpType}, " +
                        "pumpSerial=${medtronicPumpStatus.serialNumber}]"
                )

                val result = pumpSync.syncTemporaryBasalWithTempId(
                    timestamp = item.date,
                    rate = item.rate,
                    duration = differenceS * 1000L,
                    isAbsolute = item.isAbsolute,
                    temporaryId = item.temporaryId,
                    type = item.tbrType,
                    pumpId = null,
                    pumpType = medtronicPumpStatus.pumpType,
                    pumpSerial = medtronicPumpStatus.serialNumber
                )

                aapsLogger.debug(LTag.PUMP, "syncTemporaryBasalWithTempId - Result: $result")
            }

        } else {
            aapsLogger.info(LTag.PUMP, "cancelTBRWithTemporaryId - TBR items: ${tbrs.size}, runningTBRWithTemp=${medtronicPumpStatus.runningTBRWithTemp}")
        }

        if (medtronicPumpStatus.runningTBRWithTemp != null) {
            medtronicPumpStatus.runningTBRWithTemp = null
        }
    }

    @Synchronized
    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        return if (percent == 0) {
            setTempBasalAbsolute(0.0, durationInMinutes, profile, enforceNew, tbrType)
        } else {
            var absoluteValue = profile.getBasal() * (percent / 100.0)
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue)
            aapsLogger.warn(
                LTag.PUMP,
                "setTempBasalPercent [MedtronicPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% ($percent). This will start setTempBasalAbsolute, with calculated value ($absoluteValue). Result might not be 100% correct."
            )
            setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType)
        }
    }

    private fun finishAction(overviewKey: String?) {
        if (overviewKey != null) rxBus.send(EventRefreshOverview(overviewKey, false))
        triggerUIChange()
        setRefreshButtonEnabled(true)
    }

    private fun readPumpHistory() {

//        if (isLoggingEnabled())
//            LOG.error(getLogPrefix() + "readPumpHistory WIP.");
        readPumpHistoryLogic()
        scheduleNextRefresh(MedtronicStatusRefreshType.PumpHistory)
        if (medtronicHistoryData.hasRelevantConfigurationChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.Configuration, -1)
        }
        if (medtronicHistoryData.hasPumpTimeChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, -1)
        }
        if (medtronicPumpStatus.basalProfileStatus !== BasalProfileStatus.NotInitialized
            && medtronicHistoryData.hasBasalProfileChanged()
        ) {
            medtronicHistoryData.processLastBasalProfileChange(pumpDescription.pumpType, medtronicPumpStatus)
        }
        val previousState = pumpState
        if (medtronicHistoryData.isPumpSuspended()) {
            pumpState = PumpDriverState.Suspended
            aapsLogger.debug(LTag.PUMP, "isPumpSuspended: true")
        } else {
            if (previousState === PumpDriverState.Suspended) {
                pumpState = PumpDriverState.Ready
            }
            aapsLogger.debug(LTag.PUMP, "isPumpSuspended: false")
        }
        medtronicHistoryData.processNewHistoryData()
        medtronicHistoryData.finalizeNewHistoryRecords()
    }

    private fun readPumpHistoryLogic() {

        val debugHistory = false
        val targetDate: LocalDateTime?
        if (lastPumpHistoryEntry == null) {  // first read
            if (debugHistory) aapsLogger.debug(LTag.PUMP, "readPumpHistoryLogic(): lastPumpHistoryEntry: null")
            val lastPumpHistoryEntryTime = lastPumpEntryTime
            var timeMinus36h = LocalDateTime()
            timeMinus36h = timeMinus36h.minusHours(36)
            medtronicHistoryData.setIsInInit(true)
            if (lastPumpHistoryEntryTime == 0L) {
                if (debugHistory) aapsLogger.debug(LTag.PUMP, "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L")
                targetDate = timeMinus36h
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);
                if (debugHistory) aapsLogger.debug(LTag.PUMP, "readPumpHistoryLogic(): lastPumpHistoryEntryTime: " + lastPumpHistoryEntryTime)
                //medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntryTime)
                var lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime)
                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12) // we get last 12 hours of history to
                // determine pump state
                // (we don't process that data), we process only
                targetDate = if (timeMinus36h.isAfter(lastHistoryRecordTime)) timeMinus36h else lastHistoryRecordTime
                if (debugHistory) aapsLogger.debug(LTag.PUMP, "readPumpHistoryLogic(): targetDate: " + targetDate)
            }
        } else { // all other reads
            if (debugHistory) aapsLogger.debug(LTag.PUMP, "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - " + medtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry))
            medtronicHistoryData.setIsInInit(false)
            // we need to read 35 minutes in the past so that we can repair any TBR or Bolus values if needed
            targetDate = LocalDateTime(DateTimeUtil.getMillisFromATDWithAddedMinutes(lastPumpHistoryEntry!!.atechDateTime, -35))
        }

        //aapsLogger.debug(LTag.PUMP, "HST: Target Date: " + targetDate);
        @Suppress("UNCHECKED_CAST")
        val responseTask2 = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(
            MedtronicCommandType.GetHistoryData,
            arrayListOf(/*lastPumpHistoryEntry*/ null, targetDate) as? ArrayList<Any>?
        )
        if (debugHistory) aapsLogger.debug(LTag.PUMP, "HST: After task")
        val historyResult = responseTask2?.result as PumpHistoryResult?
        if (debugHistory) aapsLogger.debug(LTag.PUMP, "HST: History Result: $historyResult")
        val latestEntry = historyResult!!.latestEntry
        if (debugHistory) aapsLogger.debug(LTag.PUMP, "Last entry: " + latestEntry)
        if (latestEntry == null) // no new history to read
            return
        lastPumpHistoryEntry = latestEntry
        preferences.put(MedtronicLongNonKey.LastPumpHistoryEntry, latestEntry.atechDateTime)
        if (debugHistory) aapsLogger.debug(LTag.PUMP, "HST: History: valid=" + historyResult.validEntries.size + ", unprocessed=" + historyResult.unprocessedEntries.size)
        medtronicHistoryData.addNewHistory(historyResult)
        medtronicHistoryData.filterNewEntries()

        // determine if first run, if yes determine how much of update do we need
        // - first run:
        //   - get last history entry
        //      - if not there download 1.5 days of data
        //      - there: check if last entry is older than 1.5 days
        //          - yes: download 1.5 days
        //          - no: download with last entry  TODO 5min
        //   - not there: download 1.5 days
        //
        //    upload all new entries to NightScout (TBR, Bolus)
        //    determine pump status
        //    save last entry
        //
        // - not first run:
        //    - update to last entry TODO 5min
        //        - save
        //        - determine pump status
    }

    private val lastPumpEntryTime: Long
        get() {
            val lastPumpEntryTime = preferences.get(MedtronicLongNonKey.LastPumpHistoryEntry)
            return try {
                val localDateTime = DateTimeUtil.toLocalDateTime(lastPumpEntryTime)
                if (localDateTime.year != GregorianCalendar()[Calendar.YEAR]) {
                    aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid. Year was not the same.")
                    return 0L
                }
                lastPumpEntryTime
            } catch (_: Exception) {
                aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid.")
                0L
            }
        }

    private fun scheduleNextRefresh(refreshType: MedtronicStatusRefreshType, additionalTimeInMinutes: Int = 0) {
        when (refreshType) {
            MedtronicStatusRefreshType.RemainingInsulin                                                                                                                     -> {
                val remaining = medtronicPumpStatus.reservoirRemainingUnits
                val min: Int = if (remaining > 50) 4 * 60 else if (remaining > 20) 60 else 15
                synchronized(statusRefreshMap) { statusRefreshMap[refreshType] = getTimeInFutureFromMinutes(min) }
            }

            MedtronicStatusRefreshType.PumpTime, MedtronicStatusRefreshType.Configuration, MedtronicStatusRefreshType.BatteryStatus, MedtronicStatusRefreshType.PumpHistory -> {
                synchronized(statusRefreshMap) { statusRefreshMap[refreshType] = getTimeInFutureFromMinutes(refreshType.refreshTime + additionalTimeInMinutes) }
            }
        }
    }

    private fun getTimeInFutureFromMinutes(minutes: Int): Long {
        return System.currentTimeMillis() + getTimeInMs(minutes)
    }

    private fun getTimeInMs(minutes: Int): Long {
        return minutes * 60 * 1000L
    }

    private fun readTBR(): TempBasalPair? {
        val responseTask = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.ReadTemporaryBasal)
        return if (responseTask?.hasData() == true) {
            val tbr = responseTask.result as TempBasalPair?

            // we sometimes get rate returned even if TBR is no longer running
            if (tbr != null) {
                if (tbr.durationMinutes == 0) {
                    tbr.insulinRate = 0.0
                }
                tbr
            } else
                null
        } else {
            null
        }
    }

    @Synchronized
    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "cancelTempBasal - started")
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            return pumpEnactResultProvider.get() //
                .success(false) //
                .enacted(false) //
                .comment(R.string.medtronic_pump_status_pump_unreachable)
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        setRefreshButtonEnabled(false)
        val tbrCurrent = readTBR()
        if (tbrCurrent != null) {
            if (tbrCurrent.insulinRate > 0.0f && tbrCurrent.durationMinutes == 0) {
                aapsLogger.info(LTag.PUMP, "cancelTempBasal - TBR already canceled.")
                finishAction("TBR")
                return pumpEnactResultProvider.get().success(true).enacted(false)
            }
        } else {
            aapsLogger.warn(LTag.PUMP, "cancelTempBasal - Could not read current TBR, canceling operation.")
            finishAction("TBR")
            return pumpEnactResultProvider.get().success(false).enacted(false)
                .comment(R.string.medtronic_cmd_cant_read_tbr)
        }
        val responseTask2 = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(MedtronicCommandType.CancelTBR)
        val response = responseTask2?.result as Boolean?
        finishAction("TBR")
        return if (response == null || !response) {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - Cancel TBR failed.")
            pumpEnactResultProvider.get().success(false).enacted(false) //
                .comment(R.string.medtronic_cmd_cant_cancel_tbr)
        } else {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - Cancel TBR successful.")

            val runningTBR = medtronicPumpStatus.runningTBR
            // TODO
            if (runningTBR != null) {
                if (medtronicHistoryData.isTBRActive(runningTBR)) {

                    val differenceTime = System.currentTimeMillis() - runningTBR.date
                    //val tbrData = runningTBR

                    val result = pumpSync.syncTemporaryBasalWithPumpId(
                        runningTBR.date,
                        runningTBR.rate,
                        differenceTime,
                        runningTBR.isAbsolute,
                        runningTBR.tbrType,
                        runningTBR.pumpId!!,
                        runningTBR.pumpType,
                        runningTBR.serialNumber
                    )

                    val differenceTimeMin = floor(differenceTime / (60.0 * 1000.0))

                    aapsLogger.debug(
                        LTag.PUMP, "canceling running TBR - syncTemporaryBasalWithPumpId [date=${runningTBR.date}, " +
                            "pumpId=${runningTBR.pumpId}, rate=${runningTBR.rate} U, duration=${differenceTimeMin.toInt()}, " +
                            "pumpSerial=${medtronicPumpStatus.serialNumber}] - Result: $result"
                    )
                }
            }

            //cancelTBRWithTemporaryId()

            pumpEnactResultProvider.get().success(true).enacted(true) //
                .isTempCancel(true)
        }
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Medtronic
    }

    override fun model(): PumpType {
        return pumpDescription.pumpType
    }

    override fun serialNumber(): String {
        return medtronicPumpStatus.serialNumber
    }

    @Synchronized
    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        aapsLogger.info(LTag.PUMP, "setNewBasalProfile")

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
        if (isProfileSame(profile)) {
            return pumpEnactResultProvider.get() //
                .success(true) //
                .enacted(false) //
                .comment(R.string.medtronic_cmd_basal_profile_not_set_is_same)
        }
        setRefreshButtonEnabled(false)
        if (isPumpNotReachable) {
            setRefreshButtonEnabled(true)
            return pumpEnactResultProvider.get() //
                .success(false) //
                .enacted(false) //
                .comment(R.string.medtronic_pump_status_pump_unreachable)
        }
        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus)
        val basalProfile = convertProfileToMedtronicProfile(profile)
        aapsLogger.debug("Basal Profile: $basalProfile")
        val profileInvalid = isProfileValid(basalProfile)
        if (profileInvalid != null) {
            return pumpEnactResultProvider.get() //
                .success(false) //
                .enacted(false) //
                .comment(rh.gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid))
        }
        val responseTask = rileyLinkMedtronicService?.medtronicUIComm?.executeCommand(
            MedtronicCommandType.SetBasalProfileSTD,
            arrayListOf(basalProfile)
        )
        val response = responseTask?.result as Boolean?
        aapsLogger.info(LTag.PUMP, "Basal Profile was set: " + response)
        return if (response == null || !response) {
            pumpEnactResultProvider.get().success(false).enacted(false) //
                .comment(R.string.medtronic_cmd_basal_profile_could_not_be_set)
        } else {
            pumpEnactResultProvider.get().success(true).enacted(true)
        }
    }

    private fun isProfileValid(basalProfile: BasalProfile): String? {
        val stringBuilder = StringBuilder()
        if (medtronicPumpStatus.maxBasal == null) return null
        for (profileEntry in basalProfile.getEntries()) {
            if (profileEntry.rate > medtronicPumpStatus.maxBasal!!) {
                stringBuilder.append(profileEntry.startTime!!.toString("HH:mm"))
                stringBuilder.append("=")
                stringBuilder.append(profileEntry.rate)
            }
        }
        return if (stringBuilder.isEmpty()) null else stringBuilder.toString()
    }

    private fun convertProfileToMedtronicProfile(profile: Profile): BasalProfile {
        val basalProfile = BasalProfile(aapsLogger)
        for (i in 0..23) {
            val rate = profile.getBasalTimeFromMidnight(i * 60 * 60)
            val v = pumpType.determineCorrectBasalSize(rate)
            val basalEntry = BasalProfileEntry(v, i, 0)
            basalProfile.addEntry(basalEntry)
        }
        basalProfile.generateRawDataFromEntries()
        return basalProfile
    }

    // OPERATIONS not supported by Pump or Plugin
    private var customActions: List<CustomAction>? = null
    private val customActionWakeUpAndTune = CustomAction(
        R.string.medtronic_custom_action_wake_and_tune,
        MedtronicCustomActionType.WakeUpAndTune,
        app.aaps.core.ui.R.drawable.ic_actions_profileswitch
    )
    private val customActionClearBolusBlock = CustomAction(
        R.string.medtronic_custom_action_clear_bolus_block, MedtronicCustomActionType.ClearBolusBlock, app.aaps.core.ui.R.drawable.ic_actions_profileswitch, false
    )
    private val customActionResetRLConfig = CustomAction(
        R.string.medtronic_custom_action_reset_rileylink, MedtronicCustomActionType.ResetRileyLinkConfiguration, app.aaps.core.ui.R.drawable.ic_actions_profileswitch, true
    )

    override fun getCustomActions(): List<CustomAction>? {
        if (customActions == null) {
            customActions = listOf(
                customActionWakeUpAndTune,  //
                customActionClearBolusBlock,  //
                customActionResetRLConfig
            )
        }
        return customActions
    }

    override fun executeCustomAction(customActionType: CustomActionType) {
        when (customActionType as? MedtronicCustomActionType) {
            MedtronicCustomActionType.WakeUpAndTune               -> {
                if (rileyLinkMedtronicService?.verifyConfiguration() == true) {
                    serviceTaskExecutor.startTask(wakeAndTuneTaskProvider.get())
                } else {
                    uiInteraction.runAlarm(rh.gs(R.string.medtronic_error_operation_not_possible_no_configuration), rh.gs(R.string.medtronic_warning), app.aaps.core.ui.R.raw.boluserror)
                }
            }

            MedtronicCustomActionType.ClearBolusBlock             -> {
                busyTimestamps.clear()
                customActionClearBolusBlock.isEnabled = false
                refreshCustomActionsList()
            }

            MedtronicCustomActionType.ResetRileyLinkConfiguration -> {
                serviceTaskExecutor.startTask(resetRileyLinkConfigurationTaskProvider.get())
            }

            null                                                  -> { // do nothing

            }
        }
    }

    override fun timezoneOrDSTChanged(timeChangeType: TimeChangeType) {
        aapsLogger.warn(LTag.PUMP, "Time or TimeZone changed. ")
        hasTimeDateOrTimeZoneChanged = true
    }

    override fun setNeutralTempAtFullHour(): Boolean {
        return preferences.get(MedtronicBooleanPreferenceKey.SetNeutralTemp)
    }

    @Suppress("SameParameterValue")
    private fun setEnableCustomAction(customAction: MedtronicCustomActionType, isEnabled: Boolean) {
        if (customAction === MedtronicCustomActionType.ClearBolusBlock) {
            customActionClearBolusBlock.isEnabled = isEnabled
        } else if (customAction === MedtronicCustomActionType.ResetRileyLinkConfiguration) {
            customActionResetRLConfig.isEnabled = isEnabled
        }
        refreshCustomActionsList()
    }

    companion object {

        val pumpFreqValues = arrayOf<CharSequence>(RileyLinkTargetFrequency.MedtronicUS.key!!, RileyLinkTargetFrequency.MedtronicWorldWide.key!!)
        val encodingValues = arrayOf<CharSequence>(RileyLinkEncodingType.FourByteSixByteLocal.key!!, RileyLinkEncodingType.FourByteSixByteRileyLink.key!!)
        val batteryValues = mutableListOf<CharSequence>().also { list -> BatteryType.entries.forEach { list.add(it.key) } }.toTypedArray()
    }

    private val pumpFreqEntries = arrayOf<CharSequence>(rh.gs(RileyLinkTargetFrequency.MedtronicUS.friendlyName!!), rh.gs(RileyLinkTargetFrequency.MedtronicWorldWide.friendlyName!!))

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        val batteryEntries = mutableListOf<CharSequence>().also { list -> BatteryType.entries.forEach { list.add(rh.gs(it.friendlyName)) } }.toTypedArray()
        val encodingEntries = arrayOf<CharSequence>(rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!), rh.gs(RileyLinkEncodingType.FourByteSixByteLocal.friendlyName!!))

        val pumpTypeEntries = arrayOf<CharSequence>(
            "Other (unsupported)",
            "512",
            "712",
            "515",
            "715",
            "522",
            "722",
            "523 (Fw 2.4A or lower)",
            "723 (Fw 2.4A or lower)",
            "554 (EU Fw. <= 2.6A)",
            "754 (EU Fw. <= 2.6A)",
            "554 (CA Fw. <= 2.7A)",
            "754 (CA Fw. <= 2.7A)"
        )

        val bolusDelayEntries = arrayOf<CharSequence>("5", "10", "15")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "medtronic_settings"
            title = rh.gs(R.string.medtronic_name)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveStringPreference(
                    ctx = context, stringKey = MedtronicStringPreferenceKey.Serial, title = R.string.medtronic_serial_number,
                    validatorParams = DefaultEditTextValidator.Parameters(
                        testType = EditTextValidator.TEST_REGEXP,
                        customRegexp = rh.gs(R.string.sixdigitnumber),
                        testErrorString = rh.gs(app.aaps.core.validators.R.string.error_mustbe6digitnumber)
                    )
                )
            )
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = MedtronicStringPreferenceKey.PumpType,
                    title = R.string.medtronic_pump_type,
                    entries = pumpTypeEntries,
                    entryValues = pumpTypeEntries
                )
            )
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = MedtronicStringPreferenceKey.PumpFrequency,
                    title = R.string.medtronic_pump_frequency,
                    entries = pumpFreqEntries,
                    entryValues = pumpFreqValues
                )
            )
            addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtronicIntPreferenceKey.MaxBasal, title = R.string.medtronic_pump_max_basal))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = MedtronicIntPreferenceKey.MaxBolus, title = R.string.medtronic_pump_max_bolus))
            addPreference(
                AdaptiveListIntPreference(
                    ctx = context,
                    intKey = MedtronicIntPreferenceKey.BolusDelay,
                    title = R.string.medtronic_pump_bolus_delay,
                    entries = bolusDelayEntries,
                    entryValues = bolusDelayEntries
                )
            )
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = RileyLinkStringPreferenceKey.Encoding,
                    title = R.string.medtronic_pump_encoding,
                    entries = encodingEntries,
                    entryValues = encodingValues
                )
            )
            addPreference(
                AdaptiveListPreference(
                    ctx = context,
                    stringKey = MedtronicStringPreferenceKey.BatteryType,
                    title = R.string.medtronic_pump_battery_select,
                    entries = batteryEntries,
                    entryValues = batteryValues
                )
            )
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = RileyLinkIntentPreferenceKey.MacAddressSelector, title = app.aaps.pump.common.hw.rileylink.R.string.rileylink_configuration,
                    intent = Intent(context, RileyLinkBLEConfigActivity::class.java)
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = RileylinkBooleanPreferenceKey.OrangeUseScanning,
                    title = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level,
                    summary = app.aaps.pump.common.hw.rileylink.R.string.orange_use_scanning_level_summary
                )
            )
            addPreference(
                AdaptiveSwitchPreference(
                    ctx = context,
                    booleanKey = RileylinkBooleanPreferenceKey.ShowReportedBatteryLevel,
                    title = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level,
                    summary = app.aaps.pump.common.hw.rileylink.R.string.riley_link_show_battery_level_summary
                )
            )
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = MedtronicBooleanPreferenceKey.SetNeutralTemp, title = R.string.set_neutral_temps_title, summary = R.string.set_neutral_temps_summary))
        }
    }
}