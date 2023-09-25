package info.nightscout.plugins.sync.nsclientV3

import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.JsonHelper
import info.nightscout.database.impl.AppRepository
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
class DataSyncSelectorV3 @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val appRepository: AppRepository,
    private val rxBus: RxBus,
    private val storeDataForDb: StoreDataForDb,
    private val config: Config
) : DataSyncSelector {

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
    private val isPaused get() = sp.getBoolean(R.string.key_ns_paused, false)

    override fun queueSize(): Long = queueCounter.size()

    private val bgUploadEnabled get() = sp.getBoolean(info.nightscout.core.utils.R.string.key_do_ns_upload, false) && activePlugin.activeBgSource !is NSClientSource

    override suspend fun doUpload() {
        rxBus.send(EventNSClientUpdateGuiStatus())
        if ((config.NSCLIENT || sp.getBoolean(R.string.key_ns_upload, true)) && !isPaused) {
            queueCounter.bolusesRemaining = (appRepository.getLastBolusId() ?: 0L) - sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
            queueCounter.carbsRemaining = (appRepository.getLastCarbsId() ?: 0L) - sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
            queueCounter.bcrRemaining = (appRepository.getLastBolusCalculatorResultId() ?: 0L) - sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            queueCounter.ttsRemaining = (appRepository.getLastTempTargetId() ?: 0L) - sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            queueCounter.foodsRemaining = (appRepository.getLastFoodId() ?: 0L) - sp.getLong(R.string.key_ns_food_last_synced_id, 0)
            queueCounter.gvsRemaining = (appRepository.getLastGlucoseValueId() ?: 0L) - sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            queueCounter.tesRemaining = (appRepository.getLastTherapyEventId() ?: 0L) - sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            queueCounter.dssRemaining = (appRepository.getLastDeviceStatusId() ?: 0L) - sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
            queueCounter.tbrsRemaining = (appRepository.getLastTemporaryBasalId() ?: 0L) - sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            queueCounter.ebsRemaining = (appRepository.getLastExtendedBolusId() ?: 0L) - sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            queueCounter.pssRemaining = (appRepository.getLastProfileSwitchId() ?: 0L) - sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            queueCounter.epssRemaining = (appRepository.getLastEffectiveProfileSwitchId() ?: 0L) - sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            queueCounter.oesRemaining = (appRepository.getLastOfflineEventId() ?: 0L) - sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
            rxBus.send(EventNSClientUpdateGuiQueue())
            processChangedGlucoseValues()
            processChangedBoluses()
            processChangedCarbs()
            processChangedBolusCalculatorResults()
            processChangedTemporaryBasals()
            processChangedExtendedBoluses()
            processChangedProfileSwitches()
            processChangedEffectiveProfileSwitches()
            processChangedTempTargets()
            processChangedFoods()
            processChangedTherapyEvents()
            processChangedDeviceStatuses()
            processChangedOfflineEvents()
            processChangedProfileStore()
            storeDataForDb.updateNsIds()
        }
        rxBus.send(EventNSClientUpdateGuiStatus())
    }

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_glucose_value_last_synced_id)
        sp.remove(R.string.key_ns_temporary_basal_last_synced_id)
        sp.remove(R.string.key_ns_temporary_target_last_synced_id)
        sp.remove(R.string.key_ns_extended_bolus_last_synced_id)
        sp.remove(R.string.key_ns_food_last_synced_id)
        sp.remove(R.string.key_ns_bolus_last_synced_id)
        sp.remove(R.string.key_ns_carbs_last_synced_id)
        sp.remove(R.string.key_ns_bolus_calculator_result_last_synced_id)
        sp.remove(R.string.key_ns_therapy_event_last_synced_id)
        sp.remove(R.string.key_ns_profile_switch_last_synced_id)
        sp.remove(R.string.key_ns_effective_profile_switch_last_synced_id)
        sp.remove(R.string.key_ns_offline_event_last_synced_id)
        sp.remove(R.string.key_ns_profile_store_last_synced_timestamp)

        val lastDeviceStatusDbId = appRepository.getLastDeviceStatusId()
        if (lastDeviceStatusDbId != null) sp.putLong(R.string.key_ns_device_status_last_synced_id, lastDeviceStatusDbId)
        else sp.remove(R.string.key_ns_device_status_last_synced_id)
    }

    private fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_bolus_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastBolusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_bolus_last_synced_id, 0)
                startId = 0
            }
            queueCounter.bolusesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolus.first.id == bolus.second.id && bolus.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Loaded from NS: ${bolus.second.id} ")
                    // only NsId changed, no need to upload
                    bolus.first.onlyNsIdAdded(bolus.second)                                            ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed: ${bolus.second.id} ")
                    // without nsId = create new
                    bolus.first.interfaceIDs.nightscoutId == null                                      ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairBolus(bolus.first, bolus.second.id), " $startId/$lastDbId") ?: false
                    // with nsId = update if it's modified record
                    bolus.first.interfaceIDs.nightscoutId != null && bolus.first.id != bolus.second.id ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairBolus(bolus.first, bolus.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastBolusIdIfGreater(bolus.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_carbs_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedCarbs() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastCarbsId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_carbs_last_synced_id, 0)
                startId = 0
            }
            queueCounter.carbsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    carb.first.id == carb.second.id && carb.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Loaded from NS: ${carb.second.id} ")
                    // only NsId changed, no need to upload
                    carb.first.onlyNsIdAdded(carb.second)                                           ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.second.id} ")
                    // without nsId = create new
                    carb.first.interfaceIDs.nightscoutId == null                                    ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairCarbs(carb.first, carb.second.id), "$startId/$lastDbId") ?: false
                    // with nsId = update if it's modified record
                    carb.first.interfaceIDs.nightscoutId != null && carb.first.id != carb.second.id ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairCarbs(carb.first, carb.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastCarbsIdIfGreater(carb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedBolusCalculatorResults() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastBolusCalculatorResultId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
                startId = 0
            }
            queueCounter.bcrRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    bolusCalculatorResult.first.id == bolusCalculatorResult.second.id && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Loaded from NS: ${bolusCalculatorResult.second.id} ")
                    // only NsId changed, no need to upload
                    bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second)                                                            ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.second.id} ")
                    // without nsId = create new
                    bolusCalculatorResult.first.interfaceIDs.nightscoutId == null                                                                      ->
                        cont = activePlugin.activeNsClient?.nsAdd(
                            "treatments",
                            DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id),
                            "$startId/$lastDbId"
                        ) ?: false
                    // with nsId = update if it's modified record
                    bolusCalculatorResult.first.interfaceIDs.nightscoutId != null && bolusCalculatorResult.first.id != bolusCalculatorResult.second.id ->
                        cont = activePlugin.activeNsClient?.nsUpdate(
                            "treatments",
                            DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id),
                            "$startId/$lastDbId"
                        ) ?: false
                }
                if (cont) confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTempTargets() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTempTargetId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_temporary_target_last_synced_id, 0)
                startId = 0
            }
            queueCounter.ttsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    tt.first.id == tt.second.id && tt.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Loaded from NS: ${tt.second.id} ")
                    // only NsId changed, no need to upload
                    tt.first.onlyNsIdAdded(tt.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.second.id} ")
                    // without nsId = create new
                    tt.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id), "$startId/$lastDbId") ?: false
                    // existing with nsId = update
                    tt.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastTempTargetsIdIfGreater(tt.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_food_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_food_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedFoods() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastFoodId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_food_last_synced_id, 0)
                startId = 0
            }
            queueCounter.foodsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementFood(startId).blockingGet()?.let { food ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    food.first.id == food.second.id && food.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Loaded from NS: ${food.second.id} ")
                    // only NsId changed, no need to upload
                    food.first.onlyNsIdAdded(food.second)                                           ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.second.id} ")
                    // without nsId = create new
                    food.first.interfaceIDs.nightscoutId == null                                    ->
                        cont = activePlugin.activeNsClient?.nsAdd("food", DataSyncSelector.PairFood(food.first, food.second.id), "$startId/$lastDbId") ?: false
                    // with nsId = update
                    food.first.interfaceIDs.nightscoutId != null                                    ->
                        cont = activePlugin.activeNsClient?.nsUpdate("food", DataSyncSelector.PairFood(food.first, food.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastFoodIdIfGreater(food.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedGlucoseValues() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastGlucoseValueId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_glucose_value_last_synced_id, 0)
                startId = 0
            }
            queueCounter.gvsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
                if (bgUploadEnabled) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        gv.first.id == gv.second.id && gv.first.interfaceIDs.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Loaded from NS: ${gv.second.id} ")
                        // only NsId changed, no need to upload
                        gv.first.onlyNsIdAdded(gv.second)                                         ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.second.id} ")
                        // without nsId = create new
                        gv.first.interfaceIDs.nightscoutId == null                                ->
                            cont = activePlugin.activeNsClient?.nsAdd("entries", DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id), "$startId/$lastDbId") ?: false
                        // with nsId = update
                        else                                                                      ->  //  gv.first.interfaceIDs.nightscoutId != null
                            cont = activePlugin.activeNsClient?.nsUpdate("entries", DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id), "$startId/$lastDbId") ?: false
                    }
                }
                if (cont) confirmLastGlucoseValueIdIfGreater(gv.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTherapyEvents() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTherapyEventId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_therapy_event_last_synced_id, 0)
                startId = 0
            }
            queueCounter.tesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    te.first.id == te.second.id && te.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Loaded from NS: ${te.second.id} ")
                    // only NsId changed, no need to upload
                    te.first.onlyNsIdAdded(te.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Only NS id changed ID: ${te.second.id} ")
                    // without nsId = create new
                    te.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairTherapyEvent(te.first, te.second.id), "$startId/$lastDbId") ?: false
                    // nsId = update
                    te.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairTherapyEvent(te.first, te.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastTherapyEventIdIfGreater(te.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_device_status_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedDeviceStatuses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastDeviceStatusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_device_status_last_synced_id, 0)
                startId = 0
            }
            queueCounter.dssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
                cont = activePlugin.activeNsClient?.nsAdd("devicestatus", DataSyncSelector.PairDeviceStatus(deviceStatus, lastDbId), "$startId/$lastDbId") ?: false
                if (cont) confirmLastDeviceStatusIdIfGreater(deviceStatus.id)
                // with nsId = ignore
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedTemporaryBasals() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastTemporaryBasalId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
                startId = 0
            }
            queueCounter.tbrsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
                val profile = profileFunction.getProfile(tb.first.timestamp)
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    tb.first.id == tb.second.id && tb.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Loaded from NS: ${tb.second.id} ")
                    // only NsId changed, no need to upload
                    tb.first.onlyNsIdAdded(tb.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.second.id} ")
                    // without nsId = create new
                    tb.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id), "$startId/$lastDbId", profile) ?: false
                    // with nsId = update
                    tb.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id), "$startId/$lastDbId", profile) ?: false
                }
                if (cont) confirmLastTemporaryBasalIdIfGreater(tb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedExtendedBoluses() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastExtendedBolusId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
                startId = 0
            }
            queueCounter.ebsRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
                val profile = profileFunction.getProfile(eb.first.timestamp)
                if (profile != null) {
                    when {
                        // new record with existing NS id => must be coming from NS => ignore
                        eb.first.id == eb.second.id && eb.first.interfaceIDs.nightscoutId != null ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Loaded from NS: ${eb.second.id} ")
                        // only NsId changed, no need to upload
                        eb.first.onlyNsIdAdded(eb.second)                                         ->
                            aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.second.id} ")
                        // without nsId = create new
                        eb.first.interfaceIDs.nightscoutId == null                                ->
                            cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id), "$startId/$lastDbId") ?: false
                        // with nsId = update
                        eb.first.interfaceIDs.nightscoutId != null                                ->
                            cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id), "$startId/$lastDbId") ?: false
                    }
                } else aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. No profile: ${eb.second.id} ")
                if (cont) confirmLastExtendedBolusIdIfGreater(eb.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastProfileSwitchId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_profile_switch_last_synced_id, 0)
                startId = 0
            }
            queueCounter.pssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId") ?: false
                    // with nsId = update
                    ps.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedEffectiveProfileSwitches() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastEffectiveProfileSwitchId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
                startId = 0
            }
            queueCounter.epssRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    // only NsId changed, no need to upload
                    ps.first.onlyNsIdAdded(ps.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    // without nsId = create new
                    ps.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId") ?: false
                    // with nsId = update
                    ps.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastOfflineEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)) {
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, lastSynced)
        }
    }

    private suspend fun processChangedOfflineEvents() {
        var cont = true
        while (cont) {
            if (isPaused) return
            val lastDbId = appRepository.getLastOfflineEventId() ?: 0L
            var startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
            if (startId > lastDbId) {
                aapsLogger.info(LTag.NSCLIENT, "Resetting startId: $startId lastDbId: $lastDbId")
                sp.putLong(R.string.key_ns_offline_event_last_synced_id, 0)
                startId = 0
            }
            queueCounter.oesRemaining = lastDbId - startId
            rxBus.send(EventNSClientUpdateGuiQueue())
            appRepository.getNextSyncElementOfflineEvent(startId).blockingGet()?.let { oe ->
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    oe.first.id == oe.second.id && oe.first.interfaceIDs.nightscoutId != null ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Loaded from NS: ${oe.second.id} ")
                    // only NsId changed, no need to upload
                    oe.first.onlyNsIdAdded(oe.second)                                         ->
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Only NS id changed ID: ${oe.second.id} ")
                    // without nsId = create new
                    oe.first.interfaceIDs.nightscoutId == null                                ->
                        cont = activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id), "$startId/$lastDbId") ?: false
                    // existing with nsId = update
                    oe.first.interfaceIDs.nightscoutId != null                                ->
                        cont = activePlugin.activeNsClient?.nsUpdate("treatments", DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id), "$startId/$lastDbId") ?: false
                }
                if (cont) confirmLastOfflineEventIdIfGreater(oe.second.id)
            } ?: run {
                cont = false
            }
        }
    }

    private fun confirmLastProfileStore(lastSynced: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, lastSynced)
    }

    override fun profileReceived(timestamp: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, timestamp)
    }

    private suspend fun processChangedProfileStore() {
        if (isPaused) return
        val lastSync = sp.getLong(R.string.key_ns_profile_store_last_synced_timestamp, 0)
        val lastChange = sp.getLong(info.nightscout.core.utils.R.string.key_local_profile_last_change, 0)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            if (activePlugin.activeProfileSource.profile?.allProfilesValid != true) return
            val profileStore = activePlugin.activeProfileSource.profile
            val profileJson = profileStore?.data ?: return
            // add for v3
            if (JsonHelper.safeGetLongAllowNull(profileJson, "date") == null)
                profileJson.put("date", profileStore.getStartDate())
            val now = dateUtil.now()
            if (activePlugin.activeNsClient?.nsAdd("profile", DataSyncSelector.PairProfileStore(profileJson, now), "") == true)
                confirmLastProfileStore(now)
        }
    }
}