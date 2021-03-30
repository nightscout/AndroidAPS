package info.nightscout.androidaps.plugins.general.nsclient

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.extensions.toJson
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class DataSyncSelectorImplementation @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val profileFunction: ProfileFunction,
    private val nsClientPlugin: NSClientPlugin,
    private val activePlugin: ActivePluginProvider,
    private val appRepository: AppRepository
) : DataSyncSelector {

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_temporary_target_last_synced_id)
        sp.remove(R.string.key_ns_glucose_value_last_synced_id)
        sp.remove(R.string.key_ns_food_last_synced_id)
        sp.remove(R.string.key_ns_bolus_last_synced_id)
        sp.remove(R.string.key_ns_carbs_last_synced_id)
        sp.remove(R.string.key_ns_bolus_calculator_result_last_synced_id)
    }

    override fun confirmLastBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Bolus data sync from $lastSynced")
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

    override fun processChangedBolusesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_bolus_last_synced_id, 0)
        appRepository.getNextSyncElementBolus(startId).blockingGet()?.let { bolus ->
            aapsLogger.info(LTag.DATABASE, "Loading Bolus data Start: $startId ID: ${bolus.first.id} HistoryID: ${bolus.second} ")
            when {
                // removed and not uploaded yet = ignore
                !bolus.first.isValid && bolus.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !bolus.first.isValid && bolus.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", bolus.first.interfaceIDs.nightscoutId, DataSyncSelector.PairBolus(bolus.first, bolus.second))
                // existing without nsId = create new
                bolus.first.isValid && bolus.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", bolus.first.toJson(), DataSyncSelector.PairBolus(bolus.first, bolus.second))
                // existing with nsId = update
                bolus.first.isValid && bolus.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", bolus.first.interfaceIDs.nightscoutId, bolus.first.toJson(), DataSyncSelector.PairBolus(bolus.first, bolus.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastCarbsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Carbs data sync from $lastSynced")
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

    override fun processChangedCarbsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_carbs_last_synced_id, 0)
        appRepository.getNextSyncElementCarbs(startId).blockingGet()?.let { carb ->
            aapsLogger.info(LTag.DATABASE, "Loading Carbs data Start: $startId ID: ${carb.first.id} HistoryID: ${carb.second} ")
            when {
                // removed and not uploaded yet = ignore
                !carb.first.isValid && carb.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !carb.first.isValid && carb.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", carb.first.interfaceIDs.nightscoutId, DataSyncSelector.PairCarbs(carb.first, carb.second))
                // existing without nsId = create new
                carb.first.isValid && carb.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", carb.first.toJson(), DataSyncSelector.PairCarbs(carb.first, carb.second))
                // existing with nsId = update
                carb.first.isValid && carb.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", carb.first.interfaceIDs.nightscoutId, carb.first.toJson(), DataSyncSelector.PairCarbs(carb.first, carb.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastBolusCalculatorResultsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting BolusCalculatorResult data sync from $lastSynced")
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

    override fun processChangedBolusCalculatorResultsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_bolus_calculator_result_last_synced_id, 0)
        appRepository.getNextSyncElementBolusCalculatorResult(startId).blockingGet()?.let { bolusCalculatorResult ->
            aapsLogger.info(LTag.DATABASE, "Loading BolusCalculatorResult data Start: $startId ID: ${bolusCalculatorResult.first.id} HistoryID: ${bolusCalculatorResult.second} ")
            when {
                // removed and not uploaded yet = ignore
                !bolusCalculatorResult.first.isValid && bolusCalculatorResult.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !bolusCalculatorResult.first.isValid && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", bolusCalculatorResult.first.interfaceIDs.nightscoutId, DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second))
                // existing without nsId = create new
                bolusCalculatorResult.first.isValid && bolusCalculatorResult.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", bolusCalculatorResult.first.toJson(), DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second))
                // existing with nsId = update
                bolusCalculatorResult.first.isValid && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", bolusCalculatorResult.first.interfaceIDs.nightscoutId, bolusCalculatorResult.first.toJson(), DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastTempTargetsIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryTarget data sync from $lastSynced")
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

    override fun processChangedTempTargetsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_temporary_target_last_synced_id, 0)
        appRepository.getNextSyncElementTemporaryTarget(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.DATABASE, "Loading TemporaryTarget data Start: $startId ID: ${tt.first.id} HistoryID: ${tt.second} ")
            when {
                // removed and not uploaded yet = ignore
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", tt.first.interfaceIDs.nightscoutId, DataSyncSelector.PairTemporaryTarget(tt.first, tt.second))
                // existing without nsId = create new
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", tt.first.toJson(profileFunction.getUnits()), DataSyncSelector.PairTemporaryTarget(tt.first, tt.second))
                // existing with nsId = update
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", tt.first.interfaceIDs.nightscoutId, tt.first.toJson(profileFunction.getUnits()), DataSyncSelector.PairTemporaryTarget(tt.first, tt.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastFoodIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_food_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting Food data sync from $lastSynced")
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

    override fun processChangedFoodsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_food_last_synced_id, 0)
        appRepository.getNextSyncElementFood(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.DATABASE, "Loading Food data Start: $startId ID: ${tt.first.id} HistoryID: ${tt.second} ")
            when {
                // removed and not uploaded yet = ignore
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("food", tt.first.interfaceIDs.nightscoutId, DataSyncSelector.PairFood(tt.first, tt.second))
                // existing without nsId = create new
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("food", tt.first.toJson(), DataSyncSelector.PairFood(tt.first, tt.second))
                // existing with nsId = update
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("food", tt.first.interfaceIDs.nightscoutId, tt.first.toJson(), DataSyncSelector.PairFood(tt.first, tt.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting GlucoseValue data sync from $lastSynced")
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

    override fun processChangedGlucoseValuesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_glucose_value_last_synced_id, 0)
        appRepository.getNextSyncElementGlucoseValue(startId).blockingGet()?.let { gv ->
            aapsLogger.info(LTag.DATABASE, "Loading GlucoseValue data Start: $startId ID: ${gv.first.id} HistoryID: ${gv.second} ")
            if (activePlugin.activeBgSource.uploadToNs(gv.first)) {
                when {
                    // removed and not uploaded yet = ignore
                    !gv.first.isValid && gv.first.interfaceIDs.nightscoutId == null -> Any()
                    // removed and already uploaded = send for removal
                    !gv.first.isValid && gv.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbRemove("entries", gv.first.interfaceIDs.nightscoutId, DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
                    // existing without nsId = create new
                    gv.first.isValid && gv.first.interfaceIDs.nightscoutId == null  ->
                        nsClientPlugin.nsClientService?.dbAdd("entries", gv.first.toJson(), DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
                    // existing with nsId = update
                    gv.first.isValid && gv.first.interfaceIDs.nightscoutId != null  ->
                        nsClientPlugin.nsClientService?.dbUpdate("entries", gv.first.interfaceIDs.nightscoutId, gv.first.toJson(), DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
                }
                return true
            }
        }
        return false
    }

    override fun confirmLastTherapyEventIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TherapyEvents data sync from $lastSynced")
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

    override fun processChangedTherapyEventsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_therapy_event_last_synced_id, 0)
        appRepository.getNextSyncElementTherapyEvent(startId).blockingGet()?.let { tt ->
            aapsLogger.info(LTag.DATABASE, "Loading TherapyEvents data Start: $startId ID: ${tt.first.id} HistoryID: ${tt.second} ")
            when {
                // removed and not uploaded yet = ignore
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", tt.first.interfaceIDs.nightscoutId, DataSyncSelector.PairTherapyEvent(tt.first, tt.second))
                // existing without nsId = create new
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", tt.first.toJson(), DataSyncSelector.PairTherapyEvent(tt.first, tt.second))
                // existing with nsId = update
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", tt.first.interfaceIDs.nightscoutId, tt.first.toJson(), DataSyncSelector.PairTherapyEvent(tt.first, tt.second))
            }
            return true
        }
        return false
    }

}
