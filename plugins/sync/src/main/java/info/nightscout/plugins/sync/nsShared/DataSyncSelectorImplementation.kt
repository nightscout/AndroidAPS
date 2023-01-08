package info.nightscout.plugins.sync.nsShared

import info.nightscout.database.ValueWrapper
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.plugins.sync.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataSyncSelectorImplementation @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val appRepository: AppRepository
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
    private val isPaused get() = sp.getBoolean(R.string.key_ns_client_paused, false)

    override fun queueSize(): Long = queueCounter.size()

    override fun doUpload() {
        if (sp.getBoolean(R.string.key_ns_upload, true) && !isPaused) {
            processChangedBoluses()
            processChangedCarbs()
            processChangedBolusCalculatorResults()
            processChangedTemporaryBasals()
            processChangedExtendedBoluses()
            processChangedProfileSwitches()
            processChangedEffectiveProfileSwitches()
            processChangedGlucoseValues()
            processChangedTempTargets()
            processChangedFoods()
            processChangedTherapyEvents()
            processChangedDeviceStatuses()
            processChangedOfflineEvents()
            processChangedProfileStore()
        }
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

        val lastDeviceStatusDbIdWrapped = appRepository.getLastDeviceStatusIdWrapped().blockingGet()
        if (lastDeviceStatusDbIdWrapped is ValueWrapper.Existing) sp.putLong(R.string.key_ns_device_status_last_synced_id, lastDeviceStatusDbIdWrapped.value)
        else sp.remove(R.string.key_ns_device_status_last_synced_id)
    }

    override fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting Bolus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_bolus_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedBoluses() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_last_synced_id, 0)
            startId = 0
        }
        queueCounter.bolusesRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Bolus data Start: $startId ${bolus.first} forID: ${bolus.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                bolus.first.id == bolus.second.id && bolus.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Loaded from NS: ${bolus.second.id} ")
                    confirmLastBolusIdIfGreater(bolus.second.id)
                    processChangedBoluses()
                    return
                }
                // only NsId changed, no need to upload
                bolus.first.onlyNsIdAdded(bolus.second)                                            -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed: ${bolus.second.id} ")
                    confirmLastBolusIdIfGreater(bolus.second.id)
                    processChangedBoluses()
                    return
                }
                // without nsId = create new
                bolus.first.interfaceIDs.nightscoutId == null                                      ->
                    activePlugin.activeNsClient?.nsAdd(
                        "treatments",
                        DataSyncSelector.PairBolus(bolus.first, bolus.second.id),
                        " $startId/$lastDbId"
                    )
                // with nsId = update if it's modified record
                bolus.first.interfaceIDs.nightscoutId != null && bolus.first.id != bolus.second.id ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairBolus(bolus.first, bolus.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting Carbs data sync from $lastSynced")
            sp.putLong(R.string.key_ns_carbs_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedCarbs() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastCarbsIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_carbs_last_synced_id, 0)
            startId = 0
        }
        queueCounter.carbsRemaining = lastDbId - startId
        appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Carbs data Start: $startId ${carb.first} forID: ${carb.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                carb.first.id == carb.second.id && carb.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Loaded from NS: ${carb.second.id} ")
                    confirmLastCarbsIdIfGreater(carb.second.id)
                    processChangedCarbs()
                    return
                }
                // only NsId changed, no need to upload
                carb.first.onlyNsIdAdded(carb.second)                                           -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.second.id} ")
                    confirmLastCarbsIdIfGreater(carb.second.id)
                    processChangedCarbs()
                    return
                }
                // without nsId = create new
                carb.first.interfaceIDs.nightscoutId == null                                    ->
                    activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairCarbs(carb.first, carb.second.id), "$startId/$lastDbId")
                // with nsId = update if it's modified record
                carb.first.interfaceIDs.nightscoutId != null && carb.first.id != carb.second.id ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairCarbs(carb.first, carb.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting BolusCalculatorResult data sync from $lastSynced")
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedBolusCalculatorResults() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastBolusCalculatorResultIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            startId = 0
        }
        queueCounter.bcrRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
            aapsLogger.info(LTag.NSCLIENT, "Loading BolusCalculatorResult data Start: $startId ${bolusCalculatorResult.first} forID: ${bolusCalculatorResult.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                bolusCalculatorResult.first.id == bolusCalculatorResult.second.id && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Loaded from NS: ${bolusCalculatorResult.second.id} ")
                    confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
                    processChangedBolusCalculatorResults()
                    return
                }
                // only NsId changed, no need to upload
                bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second)                                                            -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.second.id} ")
                    confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
                    processChangedBolusCalculatorResults()
                    return
                }
                // without nsId = create new
                bolusCalculatorResult.first.interfaceIDs.nightscoutId == null                                                                      ->
                    activePlugin.activeNsClient?.nsAdd(
                        "treatments",
                        DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update if it's modified record
                bolusCalculatorResult.first.interfaceIDs.nightscoutId != null && bolusCalculatorResult.first.id != bolusCalculatorResult.second.id ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id), "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryTarget data sync from $lastSynced")
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedTempTargets() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastTempTargetIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            startId = 0
        }
        queueCounter.ttsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryTarget data Start: $startId ${tt.first} forID: ${tt.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                tt.first.id == tt.second.id && tt.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Loaded from NS: ${tt.second.id} ")
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    processChangedTempTargets()
                    return
                }
                // only NsId changed, no need to upload
                tt.first.onlyNsIdAdded(tt.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.second.id} ")
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    processChangedTempTargets()
                    return
                }
                // without nsId = create new
                tt.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd(
                        "treatments",
                        DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id),
                        "$startId/$lastDbId"
                    )
                // existing with nsId = update
                tt.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_food_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting Food data sync from $lastSynced")
            sp.putLong(R.string.key_ns_food_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedFoods() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastFoodIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_food_last_synced_id, 0)
            startId = 0
        }
        queueCounter.foodsRemaining = lastDbId - startId
        appRepository.getNextSyncElementFood(startId).blockingGet()?.let { food ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Food data Start: $startId ${food.first} forID: ${food.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                food.first.id == food.second.id && food.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Loaded from NS: ${food.second.id} ")
                    confirmLastFoodIdIfGreater(food.second.id)
                    processChangedFoods()
                    return
                }
                // only NsId changed, no need to upload
                food.first.onlyNsIdAdded(food.second)                                           -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.second.id} ")
                    confirmLastFoodIdIfGreater(food.second.id)
                    processChangedFoods()
                    return
                }
                // without nsId = create new
                food.first.interfaceIDs.nightscoutId == null                                    ->
                    activePlugin.activeNsClient?.nsAdd("food", DataSyncSelector.PairFood(food.first, food.second.id), "$startId/$lastDbId")
                // with nsId = update
                food.first.interfaceIDs.nightscoutId != null                                    ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "food",
                        DataSyncSelector.PairFood(food.first, food.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting GlucoseValue data sync from $lastSynced")
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedGlucoseValues() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastGlucoseValueIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            startId = 0
        }
        queueCounter.gvsRemaining = lastDbId - startId
        appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
            aapsLogger.info(LTag.NSCLIENT, "Loading GlucoseValue data Start: $startId ${gv.first} forID: ${gv.second.id} ")
            if (activePlugin.activeBgSource.shouldUploadToNs(gv.first)) {
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    gv.first.id == gv.second.id && gv.first.interfaceIDs.nightscoutId != null -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Loaded from NS: ${gv.second.id} ")
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        processChangedGlucoseValues()
                        return
                    }
                    // only NsId changed, no need to upload
                    gv.first.onlyNsIdAdded(gv.second)                                         -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.second.id} ")
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        processChangedGlucoseValues()
                        return
                    }
                    // without nsId = create new
                    gv.first.interfaceIDs.nightscoutId == null                                ->
                        activePlugin.activeNsClient?.nsAdd("entries", DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id), "$startId/$lastDbId")
                    // with nsId = update
                    else                                                                      ->  //  gv.first.interfaceIDs.nightscoutId != null
                        activePlugin.activeNsClient?.nsUpdate(
                            "entries",
                            DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id),
                            "$startId/$lastDbId"
                        )
                }
            } else {
                confirmLastGlucoseValueIdIfGreater(gv.second.id)
                processChangedGlucoseValues()
                return
            }
        }
    }

    override fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting TherapyEvents data sync from $lastSynced")
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedTherapyEvents() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastTherapyEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            startId = 0
        }
        queueCounter.tesRemaining = lastDbId - startId
        appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TherapyEvents data Start: $startId ${te.first} forID: ${te.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                te.first.id == te.second.id && te.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Loaded from NS: ${te.second.id} ")
                    confirmLastTherapyEventIdIfGreater(te.second.id)
                    processChangedTherapyEvents()
                    return
                }
                // only NsId changed, no need to upload
                te.first.onlyNsIdAdded(te.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Only NS id changed ID: ${te.second.id} ")
                    confirmLastTherapyEventIdIfGreater(te.second.id)
                    processChangedTherapyEvents()
                    return
                }
                // without nsId = create new
                te.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairTherapyEvent(te.first, te.second.id), "$startId/$lastDbId")
                // nsId = update
                te.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairTherapyEvent(te.first, te.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting DeviceStatus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_device_status_last_synced_id, lastSynced)
        }
    }

    override fun processChangedDeviceStatuses() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastDeviceStatusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_device_status_last_synced_id, 0)
            startId = 0
        }
        queueCounter.dssRemaining = lastDbId - startId
        appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading DeviceStatus data Start: $startId $deviceStatus")
            when {
                // without nsId = create new
                deviceStatus.interfaceIDs.nightscoutId == null ->
                    activePlugin.activeNsClient?.nsAdd("devicestatus", DataSyncSelector.PairDeviceStatus(deviceStatus, lastDbId), "$startId/$lastDbId")
                // with nsId = ignore
                deviceStatus.interfaceIDs.nightscoutId != null -> Any()
            }
            return
        }
    }

    override fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryBasal data sync from $lastSynced")
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedTemporaryBasals() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastTemporaryBasalIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            startId = 0
        }
        queueCounter.tbrsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryBasal data Start: $startId ${tb.first} forID: ${tb.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                tb.first.id == tb.second.id && tb.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Loaded from NS: ${tb.second.id} ")
                    confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                    processChangedTemporaryBasals()
                    return
                }
                // only NsId changed, no need to upload
                tb.first.onlyNsIdAdded(tb.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.second.id} ")
                    confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                    processChangedTemporaryBasals()
                    return
                }
                // without nsId = create new
                tb.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd(
                        "treatments",
                        DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update
                tb.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting ExtendedBolus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedExtendedBoluses() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastExtendedBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            startId = 0
        }
        queueCounter.ebsRemaining = lastDbId - startId
        appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ExtendedBolus data Start: $startId ${eb.first} forID: ${eb.second.id} ")
            val profile = profileFunction.getProfile(eb.first.timestamp)
            if (profile != null) {
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    eb.first.id == eb.second.id && eb.first.interfaceIDs.nightscoutId != null -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Loaded from NS: ${eb.second.id} ")
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        processChangedExtendedBoluses()
                        return
                    }
                    // only NsId changed, no need to upload
                    eb.first.onlyNsIdAdded(eb.second)                                         -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.second.id} ")
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        processChangedExtendedBoluses()
                        return
                    }
                    // without nsId = create new
                    eb.first.interfaceIDs.nightscoutId == null                                ->
                        activePlugin.activeNsClient?.nsAdd(
                            "treatments",
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                    // with nsId = update
                    eb.first.interfaceIDs.nightscoutId != null                                ->
                        activePlugin.activeNsClient?.nsUpdate(
                            "treatments",
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                }
                return
            } else {
                aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. No profile: ${eb.second.id} ")
                confirmLastExtendedBolusIdIfGreater(eb.second.id)
                processChangedExtendedBoluses()
                return
            }
        }
    }

    override fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting ProfileSwitch data sync from $lastSynced")
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedProfileSwitches() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            startId = 0
        }
        queueCounter.pssRemaining = lastDbId - startId
        appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    confirmLastProfileSwitchIdIfGreater(ps.second.id)
                    processChangedProfileSwitches()
                    return
                }
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    confirmLastProfileSwitchIdIfGreater(ps.second.id)
                    processChangedProfileSwitches()
                    return
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId")
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastEffectiveProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting EffectiveProfileSwitch data sync from $lastSynced")
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedEffectiveProfileSwitches() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastEffectiveProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            startId = 0
        }
        queueCounter.epssRemaining = lastDbId - startId
        appRepository.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data Start: $startId ${ps.first} forID: ${ps.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Loaded from NS: ${ps.second.id} ")
                    confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
                    processChangedEffectiveProfileSwitches()
                    return
                }
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.second.id} ")
                    confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
                    processChangedEffectiveProfileSwitches()
                    return
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd(
                        "treatments",
                        DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastOfflineEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting OfflineEvent data sync from $lastSynced")
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, lastSynced)
        }
    }

    override tailrec fun processChangedOfflineEvents() {
        if (isPaused) return
        val lastDbIdWrapped = appRepository.getLastOfflineEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, 0)
            startId = 0
        }
        queueCounter.oesRemaining = lastDbId - startId
        appRepository.getNextSyncElementOfflineEvent(startId).blockingGet()?.let { oe ->
            aapsLogger.info(LTag.NSCLIENT, "Loading OfflineEvent data Start: $startId ${oe.first} forID: ${oe.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                oe.first.id == oe.second.id && oe.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Loaded from NS: ${oe.second.id} ")
                    confirmLastOfflineEventIdIfGreater(oe.second.id)
                    processChangedOfflineEvents()
                    return
                }
                // only NsId changed, no need to upload
                oe.first.onlyNsIdAdded(oe.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Only NS id changed ID: ${oe.second.id} ")
                    confirmLastOfflineEventIdIfGreater(oe.second.id)
                    processChangedOfflineEvents()
                    return
                }
                // without nsId = create new
                oe.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsAdd("treatments", DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id), "$startId/$lastDbId")
                // existing with nsId = update
                oe.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsUpdate(
                        "treatments",
                        DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id),
                        "$startId/$lastDbId"
                    )
            }
            return
        }
    }

    override fun confirmLastProfileStore(lastSynced: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, lastSynced)
    }

    override fun processChangedProfileStore() {
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
            activePlugin.activeNsClient?.nsAdd("profile", DataSyncSelector.PairProfileStore(profileJson, dateUtil.now()), "")
        }
    }
}