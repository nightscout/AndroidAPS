package app.aaps.plugins.sync.xdrip

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.sync.xdrip.events.EventXdripNewLog
import app.aaps.plugins.sync.xdrip.keys.XdripLongKey
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
@Singleton
class DataSyncSelectorXdripImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val xdripBroadcast: Lazy<XDripBroadcast>,
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus,
    private val preferences: Preferences,
) : DataSyncSelectorXdrip {

    class QueueCounter(
        var bolusesRemaining: Long = -1L,
        var carbsRemaining: Long = -1L,
        var bcrRemaining: Long = -1L,
        var ttsRemaining: Long = -1L,
        var foodsRemaining: Long = -1L,
        var gvsRemaining: Long = -1L,
        var tesRemaining: Long = -1L,
        var dssRemaining: Long = -1L,
        var tbrsRemaining: Long = -1L,
        var ebsRemaining: Long = -1L,
        var pssRemaining: Long = -1L,
        var epssRemaining: Long = -1L,
        var oesRemaining: Long = -1L
    ) {

        fun size(): Long =
            bolusesRemaining +
                carbsRemaining +
                bcrRemaining +
                ttsRemaining +
                foodsRemaining +
                gvsRemaining +
                tesRemaining +
                dssRemaining +
                tbrsRemaining +
                ebsRemaining +
                pssRemaining +
                epssRemaining +
                oesRemaining
    }

    private val queueCounter = QueueCounter()
    private val isEnabled get() = xdripPlugin.isEnabled()
    private val xdripPlugin get() = xdripBroadcast.get()

    private val maxAge get() = T.days(1).msecs()
    private fun isOld(timestamp: Long) = timestamp < dateUtil.now() - maxAge
    private val preparedEntries = mutableListOf<DataSyncSelector.DataPair>()
    private val preparedTreatments = mutableListOf<DataSyncSelector.DataPair>()
    private val preparedFoods = mutableListOf<DataSyncSelector.DataPair>()

    override fun queueSize(): Long = queueCounter.size()

    private var running = false
    private val sync = Any()

    override suspend fun doUpload() {
        synchronized(sync) {
            if (running) {
                rxBus.send(EventXdripNewLog("RUN", "Already running"))
                return
            }
            running = true
        }
        if (isEnabled) {
            processChangedGlucoseValues()
            processChangedBoluses()
            processChangedCarbs()
            // not supported at the moment
            //processChangedBolusCalculatorResults()
            // not supported at the moment
            //processChangedTemporaryBasals()
            processChangedExtendedBoluses()
            processChangedProfileSwitches()
            // not supported at the moment
            //processChangedEffectiveProfileSwitches()
            processChangedTempTargets()
            // not supported at the moment
            //processChangedFoods()
            processChangedTherapyEvents()
            // not supported at the moment
            //processChangedDeviceStatuses()
            // not supported at the moment
            //processChangedOfflineEvents()
            // not supported at the moment
            //processChangedProfileStore()
        }
        running = false
    }

    override fun resetToNextFullSync() {
        preferences.remove(XdripLongKey.GlucoseValueLastSyncedId)
        preferences.remove(XdripLongKey.TemporaryBasalLastSyncedId)
        preferences.remove(XdripLongKey.TemporaryTargetLastSyncedId)
        preferences.remove(XdripLongKey.ExtendedBolusLastSyncedId)
        preferences.remove(XdripLongKey.FoodLastSyncedId)
        preferences.remove(XdripLongKey.BolusLastSyncedId)
        preferences.remove(XdripLongKey.CarbsLastSyncedId)
        preferences.remove(XdripLongKey.BolusCalculatorLastSyncedId)
        preferences.remove(XdripLongKey.TherapyEventLastSyncedId)
        preferences.remove(XdripLongKey.ProfileSwitchLastSyncedId)
        preferences.remove(XdripLongKey.EffectiveProfileSwitchLastSyncedId)
        preferences.remove(XdripLongKey.RunningModeLastSyncedId)
        preferences.remove(XdripLongKey.ProfileStoreLastSyncedId)

        val lastDeviceStatusDbId = persistenceLayer.getLastDeviceStatusId()
        if (lastDeviceStatusDbId != null) preferences.put(XdripLongKey.DeviceStatusLastSyncedId, lastDeviceStatusDbId)
        else preferences.remove(XdripLongKey.DeviceStatusLastSyncedId)
    }

    private fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.GlucoseValueLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting GlucoseValue data sync from $lastSynced")
            preferences.put(XdripLongKey.GlucoseValueLastSyncedId, lastSynced)
        }
    }

    private fun processChangedGlucoseValues() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastGlucoseValueId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.GlucoseValueLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.GlucoseValueLastSyncedId, 0)
                startId = 0
            }
            queueCounter.gvsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
                aapsLogger.info(LTag.XDRIP, "Loading GlucoseValue data Start: $startId ${gv.first} forID: ${gv.second.id} ")
                if (!isOld(gv.first.timestamp))
                    preparedEntries.add(DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id))
                sendEntries(force = false, progress)
                confirmLastGlucoseValueIdIfGreater(gv.second.id)
            } ?: break
        }
        sendEntries(force = true, progress)
    }

    private fun sendEntries(force: Boolean, progress: String) {
        if (preparedEntries.isNotEmpty() && (preparedEntries.size >= 100 || force)) {
            xdripPlugin.sendToXdrip("entries", preparedEntries.toList(), progress)
            preparedEntries.clear()
        }
    }

    private fun sendTreatments(force: Boolean, progress: String) {
        if (preparedTreatments.isNotEmpty() && (preparedTreatments.size >= 100 || force)) {
            xdripPlugin.sendToXdrip("treatments", preparedTreatments.toList(), progress)
            preparedTreatments.clear()
        }
    }

    private fun sendFoods(force: Boolean, progress: String) {
        if (preparedFoods.isNotEmpty() && (preparedFoods.size >= 100 || force)) {
            xdripPlugin.sendToXdrip("food", preparedFoods.toList(), progress)
            preparedFoods.clear()
        }
    }

    private fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.BolusLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting Bolus data sync from $lastSynced")
            preferences.put(XdripLongKey.BolusLastSyncedId, lastSynced)
        }
    }

    private fun processChangedBoluses() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastBolusId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.BolusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.BolusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.bolusesRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
                aapsLogger.info(LTag.XDRIP, "Loading Bolus data Start: $startId ${bolus.first} forID: ${bolus.second.id} ")
                if (!isOld(bolus.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairBolus(bolus.first, bolus.second.id))
                sendTreatments(force = false, progress)
                confirmLastBolusIdIfGreater(bolus.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.CarbsLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting Carbs data sync from $lastSynced")
            preferences.put(XdripLongKey.CarbsLastSyncedId, lastSynced)
        }
    }

    private fun processChangedCarbs() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastCarbsId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.CarbsLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.CarbsLastSyncedId, 0)
                startId = 0
            }
            queueCounter.carbsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
                aapsLogger.info(LTag.XDRIP, "Loading Carbs data Start: $startId ${carb.first} forID: ${carb.second.id} ")
                if (!isOld(carb.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairCarbs(carb.first, carb.second.id))
                sendTreatments(force = false, progress)
                confirmLastCarbsIdIfGreater(carb.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.BolusCalculatorLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting BolusCalculatorResult data sync from $lastSynced")
            preferences.put(XdripLongKey.BolusCalculatorLastSyncedId, lastSynced)
        }
    }

    private fun processChangedBolusCalculatorResults() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastBolusCalculatorResultId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.BolusCalculatorLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.BolusCalculatorLastSyncedId, 0)
                startId = 0
            }
            queueCounter.bcrRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
                aapsLogger.info(LTag.XDRIP, "Loading BolusCalculatorResult data Start: $startId ${bolusCalculatorResult.first} forID: ${bolusCalculatorResult.second.id} ")
                if (!isOld(bolusCalculatorResult.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id))
                sendTreatments(force = false, progress)
                confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.TemporaryTargetLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting TemporaryTarget data sync from $lastSynced")
            preferences.put(XdripLongKey.TemporaryTargetLastSyncedId, lastSynced)
        }
    }

    private fun processChangedTempTargets() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastTemporaryTargetId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.TemporaryTargetLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.TemporaryTargetLastSyncedId, 0)
                startId = 0
            }
            queueCounter.ttsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
                aapsLogger.info(LTag.XDRIP, "Loading TemporaryTarget data Start: $startId ${tt.first} forID: ${tt.second.id} ")
                if (!isOld(tt.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id))
                sendTreatments(force = false, progress)
                confirmLastTempTargetsIdIfGreater(tt.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.FoodLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting Food data sync from $lastSynced")
            preferences.put(XdripLongKey.FoodLastSyncedId, lastSynced)
        }
    }

    private fun processChangedFoods() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastFoodId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.FoodLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.FoodLastSyncedId, 0)
                startId = 0
            }
            queueCounter.foodsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementFood(startId).blockingGet()?.let { food ->
                aapsLogger.info(LTag.XDRIP, "Loading Food data Start: $startId ${food.first} forID: ${food.second.id} ")
                preparedFoods.add(DataSyncSelector.PairFood(food.first, food.second.id))
                sendFoods(force = false, progress)
                confirmLastFoodIdIfGreater(food.second.id)
            } ?: break
        }
        sendFoods(force = true, progress)
    }

    private fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.TherapyEventLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting TherapyEvents data sync from $lastSynced")
            preferences.put(XdripLongKey.TherapyEventLastSyncedId, lastSynced)
        }
    }

    private fun processChangedTherapyEvents() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastTherapyEventId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.TherapyEventLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.TherapyEventLastSyncedId, 0)
                startId = 0
            }
            queueCounter.tesRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
                aapsLogger.info(LTag.XDRIP, "Loading TherapyEvents data Start: $startId ${te.first} forID: ${te.second.id} ")
                if (!isOld(te.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairTherapyEvent(te.first, te.second.id))
                sendTreatments(force = false, progress)
                confirmLastTherapyEventIdIfGreater(te.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.DeviceStatusLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting DeviceStatus data sync from $lastSynced")
            preferences.put(XdripLongKey.DeviceStatusLastSyncedId, lastSynced)
        }
    }

    private fun processChangedDeviceStatuses() {
        while (true) {
            val lastDbId = persistenceLayer.getLastDeviceStatusId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.DeviceStatusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.DeviceStatusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.dssRemaining = lastDbId - startId
            persistenceLayer.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
                aapsLogger.info(LTag.XDRIP, "Loading DeviceStatus data Start: $startId $deviceStatus")
                if (!isOld(deviceStatus.timestamp))
                    xdripPlugin.sendToXdrip("devicestatus", DataSyncSelector.PairDeviceStatus(deviceStatus, lastDbId), "$startId/$lastDbId")
                confirmLastDeviceStatusIdIfGreater(lastDbId)
            } ?: break
        }
    }

    private fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.TemporaryBasalLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting TemporaryBasal data sync from $lastSynced")
            preferences.put(XdripLongKey.TemporaryBasalLastSyncedId, lastSynced)
        }
    }

    private fun processChangedTemporaryBasals() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastTemporaryBasalId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.TemporaryBasalLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.TemporaryBasalLastSyncedId, 0)
                startId = 0
            }
            queueCounter.tbrsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
                aapsLogger.info(LTag.XDRIP, "Loading TemporaryBasal data Start: $startId ${tb.first} forID: ${tb.second.id} ")
                if (!isOld(tb.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id))
                sendTreatments(force = false, progress)
                confirmLastTemporaryBasalIdIfGreater(tb.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.ExtendedBolusLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting ExtendedBolus data sync from $lastSynced")
            preferences.put(XdripLongKey.ExtendedBolusLastSyncedId, lastSynced)
        }
    }

    private fun processChangedExtendedBoluses() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastExtendedBolusId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.ExtendedBolusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.ExtendedBolusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.ebsRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
                aapsLogger.info(LTag.XDRIP, "Loading ExtendedBolus data Start: $startId ${eb.first} forID: ${eb.second.id} ")
                val profile = profileFunction.getProfile(eb.first.timestamp)
                if (profile != null && !isOld(eb.first.timestamp)) {
                    preparedTreatments.add(DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id))
                    sendTreatments(force = false, progress)
                } else
                    aapsLogger.info(LTag.XDRIP, "Ignoring ExtendedBolus. No profile: ${eb.second.id} ")
                confirmLastExtendedBolusIdIfGreater(eb.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.ProfileSwitchLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting ProfileSwitch data sync from $lastSynced")
            preferences.put(XdripLongKey.ProfileSwitchLastSyncedId, lastSynced)
        }
    }

    private fun processChangedProfileSwitches() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastProfileSwitchId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.ProfileSwitchLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.ProfileSwitchLastSyncedId, 0)
                startId = 0
            }
            queueCounter.pssRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.XDRIP, "Loading ProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                if (!isOld(ps.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id))
                sendTreatments(force = false, progress)
                confirmLastProfileSwitchIdIfGreater(ps.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.EffectiveProfileSwitchLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting EffectiveProfileSwitch data sync from $lastSynced")
            preferences.put(XdripLongKey.EffectiveProfileSwitchLastSyncedId, lastSynced)
        }
    }

    private fun processChangedEffectiveProfileSwitches() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastEffectiveProfileSwitchId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.EffectiveProfileSwitchLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.EffectiveProfileSwitchLastSyncedId, 0)
                startId = 0
            }
            queueCounter.epssRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.XDRIP, "Loading EffectiveProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                if (!isOld(ps.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id))
                sendTreatments(force = false, progress)
                confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastRunningModeIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(XdripLongKey.RunningModeLastSyncedId)) {
            //aapsLogger.debug(LTag.XDRIP, "Setting OfflineEvent data sync from $lastSynced")
            preferences.put(XdripLongKey.RunningModeLastSyncedId, lastSynced)
        }
    }

    private fun processChangedRunningModes() {
        var progress: String
        while (true) {
            val lastDbId = persistenceLayer.getLastRunningModeId() ?: 0L
            if (!isEnabled) return
            var startId = preferences.get(XdripLongKey.RunningModeLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(XdripLongKey.RunningModeLastSyncedId, 0)
                startId = 0
            }
            queueCounter.oesRemaining = lastDbId - startId
            progress = "$startId/$lastDbId"
            persistenceLayer.getNextSyncElementRunningMode(startId).blockingGet()?.let { rm ->
                aapsLogger.info(LTag.XDRIP, "Loading RunningMode data Start: $startId ${rm.first} forID: ${rm.second.id} ")
                if (!isOld(rm.first.timestamp))
                    preparedTreatments.add(DataSyncSelector.PairRunningMode(rm.first, rm.second.id))
                sendTreatments(force = false, progress)
                confirmLastRunningModeIdIfGreater(rm.second.id)
            } ?: break
        }
        sendTreatments(force = true, progress)
    }

    private fun confirmLastProfileStore(lastSynced: Long) {
        preferences.put(XdripLongKey.ProfileStoreLastSyncedId, lastSynced)
    }

    override fun profileReceived(timestamp: Long) {
        preferences.put(XdripLongKey.ProfileStoreLastSyncedId, timestamp)
    }

    private fun processChangedProfileStore() {
        if (!isEnabled) return
        val lastSync = preferences.get(XdripLongKey.ProfileStoreLastSyncedId)
        val lastChange = preferences.get(LongNonKey.LocalProfileLastChange)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            if (activePlugin.activeProfileSource.profile?.allProfilesValid != true) return
            val profileStore = activePlugin.activeProfileSource.profile
            val profileJson = profileStore?.getData() ?: return
            // add for v3
            if (JsonHelper.safeGetLongAllowNull(profileJson, "date") == null)
                profileJson.put("date", profileStore.getStartDate())
            val now = dateUtil.now()
            xdripPlugin.sendToXdrip("profile", DataSyncSelector.PairProfileStore(profileJson, now), "")
            confirmLastProfileStore(now)
            processChangedProfileStore()
        }
    }
}