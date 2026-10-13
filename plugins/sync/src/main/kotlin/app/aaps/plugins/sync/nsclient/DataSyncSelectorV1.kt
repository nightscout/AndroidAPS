package app.aaps.plugins.sync.nsclient

import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.utils.JsonHelper
import app.aaps.core.utils.waitMillis
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsShared.extensions.onlyNsIdAdded
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncSelectorV1 @Inject constructor(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val rxBus: RxBus
) : DataSyncSelector {

    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        var rmsRemaining: Long = -1L
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
                rmsRemaining
    }

    private val queueCounter = QueueCounter()
    private val isPaused get() = preferences.get(NsclientBooleanKey.NsPaused)

    override fun queueSize(): Long = queueCounter.size()

    private var running = false
    private val sync = Any()

    private val bgUploadEnabled get() = preferences.get(BooleanKey.BgSourceUploadToNs) && activePlugin.activeBgSource !is NSClientSource

    override suspend fun doUpload() {
        synchronized(sync) {
            if (running) {
                rxBus.send(EventNSClientNewLog("● RUN", "Already running"))
                return
            }
            running = true
        }
        rxBus.send(EventNSClientUpdateGuiStatus())
        if (preferences.get(BooleanKey.NsClientUploadData) && !isPaused) {
            queueCounter.bolusesRemaining = (persistenceLayer.getLastBolusId() ?: 0L) - preferences.get(NsclientLongKey.BolusLastSyncedId)
            queueCounter.carbsRemaining = (persistenceLayer.getLastCarbsId() ?: 0L) - preferences.get(NsclientLongKey.CarbsLastSyncedId)
            queueCounter.bcrRemaining = (persistenceLayer.getLastBolusCalculatorResultId() ?: 0L) - preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)
            queueCounter.ttsRemaining = (persistenceLayer.getLastTemporaryTargetId() ?: 0L) - preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)
            queueCounter.foodsRemaining = (persistenceLayer.getLastFoodId() ?: 0L) - preferences.get(NsclientLongKey.FoodLastSyncedId)
            queueCounter.gvsRemaining = (persistenceLayer.getLastGlucoseValueId() ?: 0L) - preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)
            queueCounter.tesRemaining = (persistenceLayer.getLastTherapyEventId() ?: 0L) - preferences.get(NsclientLongKey.TherapyEventLastSyncedId)
            queueCounter.dssRemaining = (persistenceLayer.getLastDeviceStatusId() ?: 0L) - preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)
            queueCounter.tbrsRemaining = (persistenceLayer.getLastTemporaryBasalId() ?: 0L) - preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)
            queueCounter.ebsRemaining = (persistenceLayer.getLastExtendedBolusId() ?: 0L) - preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)
            queueCounter.pssRemaining = (persistenceLayer.getLastProfileSwitchId() ?: 0L) - preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)
            queueCounter.epssRemaining = (persistenceLayer.getLastEffectiveProfileSwitchId() ?: 0L) - preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)
            queueCounter.rmsRemaining = (persistenceLayer.getLastRunningModeId() ?: 0L) - preferences.get(NsclientLongKey.RunningModeLastSyncedId)
            rxBus.send(EventNSClientUpdateGuiQueue())
            val boluses = scope.async { processChangedBoluses() }
            val carbs = scope.async { processChangedCarbs() }
            val bcs = scope.async { processChangedBolusCalculatorResults() }
            val tbs = scope.async { processChangedTemporaryBasals() }
            val ebs = scope.async { processChangedExtendedBoluses() }
            val pss = scope.async { processChangedProfileSwitches() }
            val epss = scope.async { processChangedEffectiveProfileSwitches() }
            val gvs = scope.async { processChangedGlucoseValues() }
            val tts = scope.async { processChangedTempTargets() }
            val foods = scope.async { processChangedFoods() }
            val tes = scope.async { processChangedTherapyEvents() }
            val dss = scope.async { processChangedDeviceStatuses() }
            val rms = scope.async { processChangedRunningModes() }
            val ps = scope.async { processChangedProfileStore() }

            boluses.await()
            carbs.await()
            bcs.await()
            tbs.await()
            ebs.await()
            pss.await()
            epss.await()
            gvs.await()
            tts.await()
            foods.await()
            tes.await()
            dss.await()
            rms.await()
            ps.await()
        }
        rxBus.send(EventNSClientUpdateGuiStatus())
        running = false
    }

    override fun resetToNextFullSync() {
        preferences.remove(NsclientLongKey.GlucoseValueLastSyncedId)
        preferences.remove(NsclientLongKey.TemporaryBasalLastSyncedId)
        preferences.remove(NsclientLongKey.TemporaryTargetLastSyncedId)
        preferences.remove(NsclientLongKey.ExtendedBolusLastSyncedId)
        preferences.remove(NsclientLongKey.FoodLastSyncedId)
        preferences.remove(NsclientLongKey.BolusLastSyncedId)
        preferences.remove(NsclientLongKey.CarbsLastSyncedId)
        preferences.remove(NsclientLongKey.BolusCalculatorLastSyncedId)
        preferences.remove(NsclientLongKey.TherapyEventLastSyncedId)
        preferences.remove(NsclientLongKey.ProfileSwitchLastSyncedId)
        preferences.remove(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)
        preferences.remove(NsclientLongKey.RunningModeLastSyncedId)
        preferences.remove(NsclientLongKey.ProfileStoreLastSyncedId)

        val lastDeviceStatusDbId = persistenceLayer.getLastDeviceStatusId()
        if (lastDeviceStatusDbId != null) preferences.put(NsclientLongKey.DeviceStatusLastSyncedId, lastDeviceStatusDbId)
        else preferences.remove(NsclientLongKey.DeviceStatusLastSyncedId)
    }

    private fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.BolusLastSyncedId)) {
            preferences.put(NsclientLongKey.BolusLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastBolusId() ?: 0L
            var startId = preferences.get(NsclientLongKey.BolusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.BolusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.bolusesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Bolus data Start: $startId ${bolus.first} forID: ${bolus.second.id} ")
                val dataPair = DataSyncSelector.PairBolus(bolus.first, bolus.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolus.first.id == bolus.second.id && bolus.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Loaded from NS: ${bolus.second.id} ")
                    // only NsId changed, no need to upload
                    bolus.first.onlyNsIdAdded(bolus.second)                                   ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed: ${bolus.second.id} ")
                    // without nsId = create new
                    bolus.first.ids.nightscoutId == null                                      -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, " $startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    bolus.first.ids.nightscoutId != null && bolus.first.id != bolus.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastBolusIdIfGreater(bolus.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.CarbsLastSyncedId)) {
            preferences.put(NsclientLongKey.CarbsLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedCarbs() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastCarbsId() ?: 0L
            var startId = preferences.get(NsclientLongKey.CarbsLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.CarbsLastSyncedId, 0)
                startId = 0
            }
            queueCounter.carbsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Carbs data Start: $startId ${carb.first} forID: ${carb.second.id} ")
                val dataPair = DataSyncSelector.PairCarbs(carb.first, carb.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    carb.first.id == carb.second.id && carb.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Loaded from NS: ${carb.second.id} ")
                    // only NsId changed, no need to upload
                    carb.first.onlyNsIdAdded(carb.second)                                  ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.second.id} ")
                    // without nsId = create new
                    carb.first.ids.nightscoutId == null                                    -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    carb.first.ids.nightscoutId != null && carb.first.id != carb.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastCarbsIdIfGreater(carb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)) {
            preferences.put(NsclientLongKey.BolusCalculatorLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedBolusCalculatorResults() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastBolusCalculatorResultId() ?: 0L
            var startId = preferences.get(NsclientLongKey.BolusCalculatorLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.BolusCalculatorLastSyncedId, 0)
                startId = 0
            }
            queueCounter.bcrRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
                aapsLogger.info(LTag.NSCLIENT, "Loading BolusCalculatorResult data Start: $startId ${bolusCalculatorResult.first} forID: ${bolusCalculatorResult.second.id} ")
                val dataPair = DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolusCalculatorResult.first.id == bolusCalculatorResult.second.id && bolusCalculatorResult.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Loaded from NS: ${bolusCalculatorResult.second.id} ")
                    // only NsId changed, no need to upload
                    bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second)                                                   ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.second.id} ")
                    // without nsId = create new
                    bolusCalculatorResult.first.ids.nightscoutId == null                                                                      -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update if it's modified record
                    bolusCalculatorResult.first.ids.nightscoutId != null && bolusCalculatorResult.first.id != bolusCalculatorResult.second.id -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)) {
            preferences.put(NsclientLongKey.TemporaryTargetLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedTempTargets() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastTemporaryTargetId() ?: 0L
            var startId = preferences.get(NsclientLongKey.TemporaryTargetLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.TemporaryTargetLastSyncedId, 0)
                startId = 0
            }
            queueCounter.ttsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryTarget data Start: $startId ${tt.first} forID: ${tt.second.id} ")
                val dataPair = DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    tt.first.id == tt.second.id && tt.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Loaded from NS: ${tt.second.id} ")
                    // only NsId changed, no need to upload
                    tt.first.onlyNsIdAdded(tt.second)                                ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.second.id} ")
                    // without nsId = create new
                    tt.first.ids.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // existing with nsId = update
                    tt.first.ids.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastTempTargetsIdIfGreater(tt.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.FoodLastSyncedId)) {
            preferences.put(NsclientLongKey.FoodLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedFoods() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastFoodId() ?: 0L
            var startId = preferences.get(NsclientLongKey.FoodLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.FoodLastSyncedId, 0)
                startId = 0
            }
            queueCounter.foodsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementFood(startId).blockingGet()?.let { food ->
                aapsLogger.info(LTag.NSCLIENT, "Loading Food data Start: $startId ${food.first} forID: ${food.second.id} ")
                val dataPair = DataSyncSelector.PairFood(food.first, food.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    food.first.id == food.second.id && food.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Loaded from NS: ${food.second.id} ")
                    // only NsId changed, no need to upload
                    food.first.onlyNsIdAdded(food.second)                                  ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.second.id} ")
                    // without nsId = create new
                    food.first.ids.nightscoutId == null                                    -> {
                        activePlugin.activeNsClient?.nsAdd("food", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    food.first.ids.nightscoutId != null                                    -> {
                        activePlugin.activeNsClient?.nsUpdate("food", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastFoodIdIfGreater(food.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)) {
            preferences.put(NsclientLongKey.GlucoseValueLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedGlucoseValues() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastGlucoseValueId() ?: 0L
            var startId = preferences.get(NsclientLongKey.GlucoseValueLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.GlucoseValueLastSyncedId, 0)
                startId = 0
            }
            queueCounter.gvsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
                aapsLogger.info(LTag.NSCLIENT, "Loading GlucoseValue data Start: $startId ${gv.first} forID: ${gv.second.id} ")
                val dataPair = DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id)
                if (bgUploadEnabled) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        gv.first.id == gv.second.id && gv.first.ids.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Loaded from NS: ${gv.second.id} ")
                        // only NsId changed, no need to upload
                        gv.first.onlyNsIdAdded(gv.second)                                ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.second.id} ")
                        // without nsId = create new
                        gv.first.ids.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("entries", dataPair, "$startId/$lastDbId")
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        else                                                             -> {//  gv.first.interfaceIDs.nightscoutId != null
                            activePlugin.activeNsClient?.nsUpdate("entries", dataPair, "$startId/$lastDbId")
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                }
                if (cont) confirmLastGlucoseValueIdIfGreater(gv.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.TherapyEventLastSyncedId)) {
            preferences.put(NsclientLongKey.TherapyEventLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedTherapyEvents() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastTherapyEventId() ?: 0L
            var startId = preferences.get(NsclientLongKey.TherapyEventLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.TherapyEventLastSyncedId, 0)
                startId = 0
            }
            queueCounter.tesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TherapyEvents data Start: $startId ${te.first} forID: ${te.second.id} ")
                val dataPair = DataSyncSelector.PairTherapyEvent(te.first, te.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    te.first.id == te.second.id && te.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Loaded from NS: ${te.second.id} ")
                    // only NsId changed, no need to upload
                    te.first.onlyNsIdAdded(te.second)                                ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Only NS id changed ID: ${te.second.id} ")
                    // without nsId = create new
                    te.first.ids.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // nsId = update
                    te.first.ids.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastTherapyEventIdIfGreater(te.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)) {
            preferences.put(NsclientLongKey.DeviceStatusLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedDeviceStatuses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastDeviceStatusId() ?: 0L
            var startId = preferences.get(NsclientLongKey.DeviceStatusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.DeviceStatusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.dssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
                aapsLogger.info(LTag.NSCLIENT, "Loading DeviceStatus data Start: $startId $deviceStatus")
                val dataPair = DataSyncSelector.PairDeviceStatus(deviceStatus, lastDbId)
                activePlugin.activeNsClient?.nsAdd("devicestatus", dataPair, "$startId/$lastDbId")
                synchronized(dataPair) { dataPair.waitMillis(60000) }
                cont = dataPair.confirmed
                if (cont) confirmLastDeviceStatusIdIfGreater(deviceStatus.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)) {
            preferences.put(NsclientLongKey.TemporaryBasalLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedTemporaryBasals() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastTemporaryBasalId() ?: 0L
            var startId = preferences.get(NsclientLongKey.TemporaryBasalLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.TemporaryBasalLastSyncedId, 0)
                startId = 0
            }
            queueCounter.tbrsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryBasal data Start: $startId ${tb.first} forID: ${tb.second.id} ")
                val dataPair = DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id)
                val profile = profileFunction.getProfile(tb.first.timestamp)
                if (profile != null) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        tb.first.id == tb.second.id && tb.first.ids.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Loaded from NS: ${tb.second.id} ")
                        // only NsId changed, no need to upload
                        tb.first.onlyNsIdAdded(tb.second)                                ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.second.id} ")
                        // without nsId = create new
                        tb.first.ids.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        tb.first.ids.nightscoutId != null                                -> {
                            activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                } else aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. No profile: ${tb.second.id} ")
                if (cont) confirmLastTemporaryBasalIdIfGreater(tb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)) {
            preferences.put(NsclientLongKey.ExtendedBolusLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedExtendedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastExtendedBolusId() ?: 0L
            var startId = preferences.get(NsclientLongKey.ExtendedBolusLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.ExtendedBolusLastSyncedId, 0)
                startId = 0
            }
            queueCounter.ebsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
                aapsLogger.info(LTag.NSCLIENT, "Loading ExtendedBolus data Start: $startId ${eb.first} forID: ${eb.second.id} ")
                val dataPair = DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id)
                val profile = profileFunction.getProfile(eb.first.timestamp)
                if (profile != null) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        eb.first.id == eb.second.id && eb.first.ids.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Loaded from NS: ${eb.second.id} ")
                        // only NsId changed, no need to upload
                        eb.first.onlyNsIdAdded(eb.second)                                ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.second.id} ")
                        // without nsId = create new
                        eb.first.ids.nightscoutId == null                                -> {
                            activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                        // with nsId = update
                        eb.first.ids.nightscoutId != null                                -> {
                            activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId", profile)
                            synchronized(dataPair) { dataPair.waitMillis(60000) }
                            cont = dataPair.confirmed
                        }
                    }
                } else aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. No profile: ${eb.second.id} ")
                if (cont) confirmLastExtendedBolusIdIfGreater(eb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)) {
            preferences.put(NsclientLongKey.ProfileSwitchLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastProfileSwitchId() ?: 0L
            var startId = preferences.get(NsclientLongKey.ProfileSwitchLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.ProfileSwitchLastSyncedId, 0)
                startId = 0
            }
            queueCounter.pssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.NSCLIENT, "Loading ProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                val dataPair = DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.ids.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    ps.first.ids.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)) {
            preferences.put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedEffectiveProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastEffectiveProfileSwitchId() ?: 0L
            var startId = preferences.get(NsclientLongKey.EffectiveProfileSwitchLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.EffectiveProfileSwitchLastSyncedId, 0)
                startId = 0
            }
            queueCounter.epssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
                aapsLogger.info(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
                val dataPair = DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.ids.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // with nsId = update
                    ps.first.ids.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastRunningModeIdIfGreater(lastSynced: Long) {
        if (lastSynced > preferences.get(NsclientLongKey.RunningModeLastSyncedId)) {
            preferences.put(NsclientLongKey.RunningModeLastSyncedId, lastSynced)
        }
    }

    private suspend fun processChangedRunningModes() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = persistenceLayer.getLastRunningModeId() ?: 0L
            var startId = preferences.get(NsclientLongKey.RunningModeLastSyncedId)
            if (startId > lastDbId) {
                preferences.put(NsclientLongKey.RunningModeLastSyncedId, 0)
                startId = 0
            }
            queueCounter.rmsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            persistenceLayer.getNextSyncElementRunningMode(startId).blockingGet()?.let { rm ->
                aapsLogger.info(LTag.NSCLIENT, "Loading RunningMode data Start: $startId ${rm.first} forID: ${rm.second.id} ")
                val dataPair = DataSyncSelector.PairRunningMode(rm.first, rm.second.id)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    rm.first.id == rm.second.id && rm.first.ids.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring RunningMode. Loaded from NS: ${rm.second.id} ")
                    // only NsId changed, no need to upload
                    rm.first.onlyNsIdAdded(rm.second)                                ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring RunningMode. Only NS id changed ID: ${rm.second.id} ")
                    // without nsId = create new
                    rm.first.ids.nightscoutId == null                                -> {
                        activePlugin.activeNsClient?.nsAdd("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                    // existing with nsId = update
                    rm.first.ids.nightscoutId != null                                -> {
                        activePlugin.activeNsClient?.nsUpdate("treatments", dataPair, "$startId/$lastDbId")
                        synchronized(dataPair) { dataPair.waitMillis(60000) }
                        cont = dataPair.confirmed
                    }
                }
                if (cont) confirmLastRunningModeIdIfGreater(rm.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileStore(lastSynced: Long) {
        preferences.put(NsclientLongKey.ProfileStoreLastSyncedId, lastSynced)
    }

    override fun profileReceived(timestamp: Long) {
        preferences.put(NsclientLongKey.ProfileStoreLastSyncedId, timestamp)
    }

    private suspend fun processChangedProfileStore() {
        if (isPaused) return
        val lastSync = preferences.get(NsclientLongKey.ProfileStoreLastSyncedId)
        val lastChange = preferences.get(LongNonKey.LocalProfileLastChange)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            if (activePlugin.activeProfileSource.profile?.allProfilesValid != true) return
            val profileStore = activePlugin.activeProfileSource.profile
            val profileJson = profileStore?.getData() ?: return
            // add for v3
            if (JsonHelper.safeGetLongAllowNull(profileJson, "date") == null)
                profileJson.put("date", profileStore.getStartDate())
            val dataPair = DataSyncSelector.PairProfileStore(profileJson, dateUtil.now())
            activePlugin.activeNsClient?.nsAdd("profile", dataPair, "")
            synchronized(dataPair) { dataPair.waitMillis(60000) }
            val now = dateUtil.now()
            val cont = dataPair.confirmed
            if (cont) confirmLastProfileStore(now)
        }
    }
}