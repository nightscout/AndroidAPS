package info.nightscout.plugins.sync.nsclient

import info.nightscout.core.extensions.toJson
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.DeviceStatus
import info.nightscout.database.entities.EffectiveProfileSwitch
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.Food
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.ProfileSwitch
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.nsclient.extensions.toJson
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
    private val appRepository: AppRepository,
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

    override fun queueSize(): Long = queueCounter.size()

    override fun doUpload() {
        if (sp.getBoolean(R.string.key_ns_upload, true)) {
            processChangedBolusesCompat()
            processChangedCarbsCompat()
            processChangedBolusCalculatorResultsCompat()
            processChangedTemporaryBasalsCompat()
            processChangedExtendedBolusesCompat()
            processChangedProfileSwitchesCompat()
            processChangedEffectiveProfileSwitchesCompat()
            processChangedGlucoseValuesCompat()
            processChangedTempTargetsCompat()
            processChangedFoodsCompat()
            processChangedTherapyEventsCompat()
            processChangedDeviceStatusesCompat()
            processChangedOfflineEventsCompat()
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

    // Prepared for v3 (returns all modified after)
    override fun changedBoluses(): List<Bolus> {
        val startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        return appRepository.getModifiedBolusesDataFromId(startId)
            .blockingGet()
            .filter { it.type != Bolus.Type.PRIMING }
            .also {
                aapsLogger.debug(LTag.NSCLIENT, "Loading Bolus data for sync from $startId. Records ${it.size}")
            }
    }

    override tailrec fun processChangedBolusesCompat() {
        val lastDbIdWrapped = appRepository.getLastBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_last_synced_id, 0)
            startId = 0
        }
        queueCounter.bolusesRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Bolus data Start: $startId ID: ${bolus.first.id} HistoryID: ${bolus.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                bolus.first.id == bolus.second.id && bolus.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Loaded from NS: ${bolus.first.id} HistoryID: ${bolus.second.id} ")
                    confirmLastBolusIdIfGreater(bolus.second.id)
                    processChangedBolusesCompat()
                    return
                }
                // only NsId changed, no need to upload
                bolus.first.onlyNsIdAdded(bolus.second)                                            -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Bolus. Only NS id changed ID: ${bolus.first.id} HistoryID: ${bolus.second.id} ")
                    confirmLastBolusIdIfGreater(bolus.second.id)
                    processChangedBolusesCompat()
                    return
                }
                // without nsId = create new
                bolus.first.interfaceIDs.nightscoutId == null                                      ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd(
                        "treatments",
                        bolus.first.toJson(true, dateUtil),
                        DataSyncSelector.PairBolus(bolus.first, bolus.second.id),
                        " $startId/$lastDbId"
                    )
                // with nsId = update if it's modified record
                bolus.first.interfaceIDs.nightscoutId != null && bolus.first.id != bolus.second.id ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        bolus.first.interfaceIDs.nightscoutId,
                        bolus.first.toJson(false, dateUtil),
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

    // Prepared for v3 (returns all modified after)
    override fun changedCarbs(): List<Carbs> {
        val startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        return appRepository.getModifiedCarbsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading Carbs data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedCarbsCompat() {
        val lastDbIdWrapped = appRepository.getLastCarbsIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_carbs_last_synced_id, 0)
            startId = 0
        }
        queueCounter.carbsRemaining = lastDbId - startId
        appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Carbs data Start: $startId ID: ${carb.first.id} HistoryID: ${carb.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                carb.first.id == carb.second.id && carb.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Loaded from NS: ${carb.first.id} HistoryID: ${carb.second.id} ")
                    confirmLastCarbsIdIfGreater(carb.second.id)
                    processChangedCarbsCompat()
                    return
                }
                // only NsId changed, no need to upload
                carb.first.onlyNsIdAdded(carb.second)                                           -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Carbs. Only NS id changed ID: ${carb.first.id} HistoryID: ${carb.second.id} ")
                    confirmLastCarbsIdIfGreater(carb.second.id)
                    processChangedCarbsCompat()
                    return
                }
                // without nsId = create new
                carb.first.interfaceIDs.nightscoutId == null                                    ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("treatments", carb.first.toJson(true, dateUtil), DataSyncSelector.PairCarbs(carb.first, carb.second.id), "$startId/$lastDbId")
                // with nsId = update if it's modified record
                carb.first.interfaceIDs.nightscoutId != null && carb.first.id != carb.second.id ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        carb.first.interfaceIDs.nightscoutId,
                        carb.first.toJson(false, dateUtil),
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

    // Prepared for v3 (returns all modified after)
    override fun changedBolusCalculatorResults(): List<BolusCalculatorResult> {
        val startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        return appRepository.getModifiedBolusCalculatorResultsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading BolusCalculatorResult data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedBolusCalculatorResultsCompat() {
        val lastDbIdWrapped = appRepository.getLastBolusCalculatorResultIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
            startId = 0
        }
        queueCounter.bcrRemaining = lastDbId - startId
        appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
            aapsLogger.info(LTag.NSCLIENT, "Loading BolusCalculatorResult data Start: $startId ID: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                bolusCalculatorResult.first.id == bolusCalculatorResult.second.id && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Loaded from NS: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second.id} ")
                    confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
                    processChangedBolusCalculatorResultsCompat()
                    return
                }
                // only NsId changed, no need to upload
                bolusCalculatorResult.first.onlyNsIdAdded(bolusCalculatorResult.second)                                                            -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring BolusCalculatorResult. Only NS id changed ID: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second.id} ")
                    confirmLastBolusCalculatorResultsIdIfGreater(bolusCalculatorResult.second.id)
                    processChangedBolusCalculatorResultsCompat()
                    return
                }
                // without nsId = create new
                bolusCalculatorResult.first.interfaceIDs.nightscoutId == null                                                                      ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd(
                        "treatments",
                        bolusCalculatorResult.first.toJson(true, dateUtil, profileFunction),
                        DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update if it's modified record
                bolusCalculatorResult.first.interfaceIDs.nightscoutId != null && bolusCalculatorResult.first.id != bolusCalculatorResult.second.id ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments", bolusCalculatorResult.first.interfaceIDs.nightscoutId, bolusCalculatorResult.first.toJson(false, dateUtil, profileFunction),
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

    // Prepared for v3 (returns all modified after)
    override fun changedTempTargets(): List<TemporaryTarget> {
        val startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        return appRepository.getModifiedTemporaryTargetsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TemporaryTarget data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedTempTargetsCompat() {
        val lastDbIdWrapped = appRepository.getLastTempTargetIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_target_last_synced_id, 0)
            startId = 0
        }
        queueCounter.ttsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryTarget data Start: $startId ID: ${tt.first.id} HistoryID: ${tt.second.id} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                tt.first.id == tt.second.id && tt.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Loaded from NS: ${tt.first.id} HistoryID: ${tt.second.id} ")
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    processChangedTempTargetsCompat()
                    return
                }
                // only NsId changed, no need to upload
                tt.first.onlyNsIdAdded(tt.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryTarget. Only NS id changed ID: ${tt.first.id} HistoryID: ${tt.second.id} ")
                    confirmLastTempTargetsIdIfGreater(tt.second.id)
                    processChangedTempTargetsCompat()
                    return
                }
                // without nsId = create new
                tt.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd(
                        "treatments",
                        tt.first.toJson(true, profileFunction.getUnits(), dateUtil),
                        DataSyncSelector.PairTemporaryTarget(tt.first, tt.second.id),
                        "$startId/$lastDbId"
                    )
                // existing with nsId = update
                tt.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        tt.first.interfaceIDs.nightscoutId,
                        tt.first.toJson(false, profileFunction.getUnits(), dateUtil),
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

    // Prepared for v3 (returns all modified after)
    override fun changedFoods(): List<Food> {
        val startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        return appRepository.getModifiedFoodDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading Food data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedFoodsCompat() {
        val lastDbIdWrapped = appRepository.getLastFoodIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_food_last_synced_id, 0)
            startId = 0
        }
        queueCounter.foodsRemaining = lastDbId - startId
        appRepository.getNextSyncElementFood(startId).blockingGet()?.let { food ->
            aapsLogger.info(LTag.NSCLIENT, "Loading Food data Start: $startId ID: ${food.first.id} HistoryID: ${food.second} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                food.first.id == food.second.id && food.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Loaded from NS: ${food.first.id} HistoryID: ${food.second.id} ")
                    confirmLastFoodIdIfGreater(food.second.id)
                    processChangedFoodsCompat()
                    return
                }
                // only NsId changed, no need to upload
                food.first.onlyNsIdAdded(food.second)                                           -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring Food. Only NS id changed ID: ${food.first.id} HistoryID: ${food.second.id} ")
                    confirmLastFoodIdIfGreater(food.second.id)
                    processChangedFoodsCompat()
                    return
                }
                // without nsId = create new
                food.first.interfaceIDs.nightscoutId == null                                    ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("food", food.first.toJson(true), DataSyncSelector.PairFood(food.first, food.second.id), "$startId/$lastDbId")
                // with nsId = update
                food.first.interfaceIDs.nightscoutId != null                                    ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "food",
                        food.first.interfaceIDs.nightscoutId,
                        food.first.toJson(false),
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

    // Prepared for v3 (returns all modified after)
    override fun changedGlucoseValues(): List<GlucoseValue> {
        val startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        return appRepository.getModifiedBgReadingsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading GlucoseValue data for sync from $startId . Records ${it.size}")
        }
    }

    override tailrec fun processChangedGlucoseValuesCompat() {
        val lastDbIdWrapped = appRepository.getLastGlucoseValueIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_glucose_value_last_synced_id, 0)
            startId = 0
        }
        queueCounter.gvsRemaining = lastDbId - startId
        appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
            aapsLogger.info(LTag.NSCLIENT, "Loading GlucoseValue data ID: ${gv.first.id} HistoryID: ${gv.second.id} ")
            if (activePlugin.activeBgSource.shouldUploadToNs(gv.first)) {
                when {
                    // new record with existing NS id => must be coming from NS => ignore 
                    gv.first.id == gv.second.id && gv.first.interfaceIDs.nightscoutId != null -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Loaded from NS: ${gv.first.id} HistoryID: ${gv.second.id} ")
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        processChangedGlucoseValuesCompat()
                        return
                    }
                    // only NsId changed, no need to upload
                    gv.first.onlyNsIdAdded(gv.second)                                         -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring GlucoseValue. Only NS id changed ID: ${gv.first.id} HistoryID: ${gv.second.id} ")
                        confirmLastGlucoseValueIdIfGreater(gv.second.id)
                        processChangedGlucoseValuesCompat()
                        return
                    }
                    // without nsId = create new
                    gv.first.interfaceIDs.nightscoutId == null                                ->
                        activePlugin.activeNsClient?.nsClientService?.dbAdd("entries", gv.first.toJson(true, dateUtil), DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id), "$startId/$lastDbId")
                    // with nsId = update
                    else                                                                      ->  //  gv.first.interfaceIDs.nightscoutId != null
                        activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                            "entries",
                            gv.first.interfaceIDs.nightscoutId,
                            gv.first.toJson(false, dateUtil),
                            DataSyncSelector.PairGlucoseValue(gv.first, gv.second.id),
                            "$startId/$lastDbId"
                        )
                }
            } else {
                confirmLastGlucoseValueIdIfGreater(gv.second.id)
                processChangedGlucoseValuesCompat()
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

    // Prepared for v3 (returns all modified after)
    override fun changedTherapyEvents(): List<TherapyEvent> {
        val startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        return appRepository.getModifiedTherapyEventDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TherapyEvents data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedTherapyEventsCompat() {
        val lastDbIdWrapped = appRepository.getLastTherapyEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_therapy_event_last_synced_id, 0)
            startId = 0
        }
        queueCounter.tesRemaining = lastDbId - startId
        appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { te ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TherapyEvents data Start: $startId ID: ${te.first.id} HistoryID: ${te.second} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                te.first.id == te.second.id && te.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Loaded from NS: ${te.first.id} HistoryID: ${te.second.id} ")
                    confirmLastTherapyEventIdIfGreater(te.second.id)
                    processChangedTherapyEventsCompat()
                    return
                }
                // only NsId changed, no need to upload
                te.first.onlyNsIdAdded(te.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring TherapyEvent. Only NS id changed ID: ${te.first.id} HistoryID: ${te.second.id} ")
                    confirmLastTherapyEventIdIfGreater(te.second.id)
                    processChangedTherapyEventsCompat()
                    return
                }
                // without nsId = create new
                te.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("treatments", te.first.toJson(true, dateUtil), DataSyncSelector.PairTherapyEvent(te.first, te.second.id), "$startId/$lastDbId")
                // nsId = update
                te.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        te.first.interfaceIDs.nightscoutId,
                        te.first.toJson(false, dateUtil),
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

    override fun changedDeviceStatuses(): List<DeviceStatus> {
        val startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        return appRepository.getModifiedDeviceStatusDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading DeviceStatus data for sync from $startId. Records ${it.size}")
        }
    }

    override fun processChangedDeviceStatusesCompat() {
        val lastDbIdWrapped = appRepository.getLastDeviceStatusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_device_status_last_synced_id, 0)
            startId = 0
        }
        queueCounter.dssRemaining = lastDbId - startId
        appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
            aapsLogger.info(LTag.NSCLIENT, "Loading DeviceStatus data Start: $startId ID: ${deviceStatus.id}")
            when {
                // without nsId = create new
                deviceStatus.interfaceIDs.nightscoutId == null ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("devicestatus", deviceStatus.toJson(dateUtil), deviceStatus, "$startId/$lastDbId")
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

    // Prepared for v3 (returns all modified after)
    override fun changedTemporaryBasals(): List<TemporaryBasal> {
        val startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        return appRepository.getModifiedTemporaryBasalDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TemporaryBasal data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedTemporaryBasalsCompat() {
        val lastDbIdWrapped = appRepository.getLastTemporaryBasalIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
            startId = 0
        }
        queueCounter.tbrsRemaining = lastDbId - startId
        appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading TemporaryBasal data Start: $startId ID: ${tb.first.id} HistoryID: ${tb.second} ")
            val profile = profileFunction.getProfile(tb.first.timestamp)
            if (profile != null) {
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    tb.first.id == tb.second.id && tb.first.interfaceIDs.nightscoutId != null -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Loaded from NS: ${tb.first.id} HistoryID: ${tb.second.id} ")
                        confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                        processChangedTemporaryBasalsCompat()
                        return
                    }
                    // only NsId changed, no need to upload
                    tb.first.onlyNsIdAdded(tb.second)                                         -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. Only NS id changed ID: ${tb.first.id} HistoryID: ${tb.second.id} ")
                        confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                        processChangedTemporaryBasalsCompat()
                        return
                    }
                    // without nsId = create new
                    tb.first.interfaceIDs.nightscoutId == null                                ->
                        activePlugin.activeNsClient?.nsClientService?.dbAdd(
                            "treatments",
                            tb.first.toJson(true, profile, dateUtil),
                            DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                            "$startId/$lastDbId"
                        )
                    // with nsId = update
                    tb.first.interfaceIDs.nightscoutId != null                                ->
                        activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                            "treatments",
                            tb.first.interfaceIDs.nightscoutId,
                            tb.first.toJson(false, profile, dateUtil),
                            DataSyncSelector.PairTemporaryBasal(tb.first, tb.second.id),
                            "$startId/$lastDbId"
                        )
                }
                return
            } else {
                aapsLogger.info(LTag.NSCLIENT, "Ignoring TemporaryBasal. No profile: ${tb.first.id} HistoryID: ${tb.second.id} ")
                confirmLastTemporaryBasalIdIfGreater(tb.second.id)
                processChangedTemporaryBasalsCompat()
                return
            }
        }
    }

    override fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            //aapsLogger.debug(LTag.NSCLIENT, "Setting ExtendedBolus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, lastSynced)
        }
    }

    // Prepared for v3 (returns all modified after)
    override fun changedExtendedBoluses(): List<ExtendedBolus> {
        val startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        return appRepository.getModifiedExtendedBolusDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading ExtendedBolus data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedExtendedBolusesCompat() {
        val lastDbIdWrapped = appRepository.getLastExtendedBolusIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
            startId = 0
        }
        queueCounter.ebsRemaining = lastDbId - startId
        appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ExtendedBolus data Start: $startId ID: ${eb.first.id} HistoryID: ${eb.second} ")
            val profile = profileFunction.getProfile(eb.first.timestamp)
            if (profile != null) {
                when {
                    // new record with existing NS id => must be coming from NS => ignore
                    eb.first.id == eb.second.id && eb.first.interfaceIDs.nightscoutId != null -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Loaded from NS: ${eb.first.id} HistoryID: ${eb.second.id} ")
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        processChangedExtendedBolusesCompat()
                        return
                    }
                    // only NsId changed, no need to upload
                    eb.first.onlyNsIdAdded(eb.second)                                         -> {
                        aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. Only NS id changed ID: ${eb.first.id} HistoryID: ${eb.second.id} ")
                        confirmLastExtendedBolusIdIfGreater(eb.second.id)
                        processChangedExtendedBolusesCompat()
                        return
                    }
                    // without nsId = create new
                    eb.first.interfaceIDs.nightscoutId == null                                ->
                        activePlugin.activeNsClient?.nsClientService?.dbAdd(
                            "treatments",
                            eb.first.toJson(true, profile, dateUtil),
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                    // with nsId = update
                    eb.first.interfaceIDs.nightscoutId != null                                ->
                        activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                            "treatments",
                            eb.first.interfaceIDs.nightscoutId,
                            eb.first.toJson(false, profile, dateUtil),
                            DataSyncSelector.PairExtendedBolus(eb.first, eb.second.id),
                            "$startId/$lastDbId"
                        )
                }
                return
            } else {
                aapsLogger.info(LTag.NSCLIENT, "Ignoring ExtendedBolus. No profile: ${eb.first.id} HistoryID: ${eb.second.id} ")
                confirmLastExtendedBolusIdIfGreater(eb.second.id)
                processChangedExtendedBolusesCompat()
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

    override fun changedProfileSwitch(): List<ProfileSwitch> {
        val startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        return appRepository.getModifiedProfileSwitchDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading ProfileSwitch data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedProfileSwitchesCompat() {
        val lastDbIdWrapped = appRepository.getLastProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, 0)
            startId = 0
        }
        queueCounter.pssRemaining = lastDbId - startId
        appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading ProfileSwitch data Start: $startId ID: ${ps.first.id} HistoryID: ${ps.second} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Loaded from NS: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    confirmLastProfileSwitchIdIfGreater(ps.second.id)
                    processChangedProfileSwitchesCompat()
                    return
                }
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring ProfileSwitch. Only NS id changed ID: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    confirmLastProfileSwitchIdIfGreater(ps.second.id)
                    processChangedProfileSwitchesCompat()
                    return
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("treatments", ps.first.toJson(true, dateUtil), DataSyncSelector.PairProfileSwitch(ps.first, ps.second.id), "$startId/$lastDbId")
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        ps.first.interfaceIDs.nightscoutId,
                        ps.first.toJson(false, dateUtil),
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

    override fun changedEffectiveProfileSwitch(): List<EffectiveProfileSwitch> {
        val startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
        return appRepository.getModifiedEffectiveProfileSwitchDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedEffectiveProfileSwitchesCompat() {
        val lastDbIdWrapped = appRepository.getLastEffectiveProfileSwitchIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_effective_profile_switch_last_synced_id, 0)
            startId = 0
        }
        queueCounter.epssRemaining = lastDbId - startId
        appRepository.getNextSyncElementEffectiveProfileSwitch(startId).blockingGet()?.let { ps ->
            aapsLogger.info(LTag.NSCLIENT, "Loading EffectiveProfileSwitch data Start: $startId ID: ${ps.first.id} HistoryID: ${ps.second} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                ps.first.id == ps.second.id && ps.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Loaded from NS: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
                    processChangedEffectiveProfileSwitchesCompat()
                    return
                }
                // only NsId changed, no need to upload
                ps.first.onlyNsIdAdded(ps.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring EffectiveProfileSwitch. Only NS id changed ID: ${ps.first.id} HistoryID: ${ps.second.id} ")
                    confirmLastEffectiveProfileSwitchIdIfGreater(ps.second.id)
                    processChangedEffectiveProfileSwitchesCompat()
                    return
                }
                // without nsId = create new
                ps.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd(
                        "treatments",
                        ps.first.toJson(true, dateUtil),
                        DataSyncSelector.PairEffectiveProfileSwitch(ps.first, ps.second.id),
                        "$startId/$lastDbId"
                    )
                // with nsId = update
                ps.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        ps.first.interfaceIDs.nightscoutId,
                        ps.first.toJson(false, dateUtil),
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

    // Prepared for v3 (returns all modified after)
    override fun changedOfflineEvents(): List<OfflineEvent> {
        val startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
        return appRepository.getModifiedOfflineEventsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading OfflineEvent data for sync from $startId. Records ${it.size}")
        }
    }

    override tailrec fun processChangedOfflineEventsCompat() {
        val lastDbIdWrapped = appRepository.getLastOfflineEventIdWrapped().blockingGet()
        val lastDbId = if (lastDbIdWrapped is ValueWrapper.Existing) lastDbIdWrapped.value else 0L
        var startId = sp.getLong(R.string.key_ns_offline_event_last_synced_id, 0)
        if (startId > lastDbId) {
            sp.putLong(R.string.key_ns_offline_event_last_synced_id, 0)
            startId = 0
        }
        queueCounter.oesRemaining = lastDbId - startId
        appRepository.getNextSyncElementOfflineEvent(startId).blockingGet()?.let { oe ->
            aapsLogger.info(LTag.NSCLIENT, "Loading OfflineEvent data Start: $startId ID: ${oe.first.id} HistoryID: ${oe.second} ")
            when {
                // new record with existing NS id => must be coming from NS => ignore
                oe.first.id == oe.second.id && oe.first.interfaceIDs.nightscoutId != null -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Loaded from NS: ${oe.first.id} HistoryID: ${oe.second.id} ")
                    confirmLastOfflineEventIdIfGreater(oe.second.id)
                    processChangedOfflineEventsCompat()
                    return
                }
                // only NsId changed, no need to upload
                oe.first.onlyNsIdAdded(oe.second)                                         -> {
                    aapsLogger.info(LTag.NSCLIENT, "Ignoring OfflineEvent. Only NS id changed ID: ${oe.first.id} HistoryID: ${oe.second.id} ")
                    confirmLastOfflineEventIdIfGreater(oe.second.id)
                    processChangedOfflineEventsCompat()
                    return
                }
                // without nsId = create new
                oe.first.interfaceIDs.nightscoutId == null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbAdd("treatments", oe.first.toJson(true, dateUtil), DataSyncSelector.PairOfflineEvent(oe.first, oe.second.id), "$startId/$lastDbId")
                // existing with nsId = update
                oe.first.interfaceIDs.nightscoutId != null                                ->
                    activePlugin.activeNsClient?.nsClientService?.dbUpdate(
                        "treatments",
                        oe.first.interfaceIDs.nightscoutId,
                        oe.first.toJson(false, dateUtil),
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
        val lastSync = sp.getLong(R.string.key_ns_profile_store_last_synced_timestamp, 0)
        val lastChange = sp.getLong(R.string.key_local_profile_last_change, 0)
        if (lastChange == 0L) return
        if (lastChange > lastSync) {
            if (activePlugin.activeProfileSource.profile?.allProfilesValid != true) return
            val profileJson = activePlugin.activeProfileSource.profile?.data ?: return
            activePlugin.activeNsClient?.nsClientService?.dbAdd("profile", profileJson, DataSyncSelector.PairProfileStore(profileJson, dateUtil.now()), "")
        }
    }
}
