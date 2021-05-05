package info.nightscout.androidaps.plugins.general.nsclient

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.extensions.toJson
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class DataSyncSelectorImplementation @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val nsClientPlugin: NSClientPlugin,
    private val activePlugin: ActivePlugin,
    private val appRepository: AppRepository,
    private val localProfilePlugin: LocalProfilePlugin
) : DataSyncSelector {

    override fun doUpload() {
        if (sp.getBoolean(R.string.key_ns_upload, true)) {
            processChangedBolusesCompat()
            processChangedCarbsCompat()
            processChangedBolusCalculatorResultsCompat()
            processChangedTemporaryBasalsCompat()
            processChangedExtendedBolusesCompat()
            processChangedProfileSwitchesCompat()
            processChangedGlucoseValuesCompat()
            processChangedTempTargetsCompat()
            processChangedFoodsCompat()
            processChangedTherapyEventsCompat()
            processChangedDeviceStatusesCompat()
            processChangedProfileStore()
        }
    }

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_temporary_target_last_synced_id)
        sp.remove(R.string.key_ns_glucose_value_last_synced_id)
        sp.remove(R.string.key_ns_food_last_synced_id)
        sp.remove(R.string.key_ns_bolus_last_synced_id)
        sp.remove(R.string.key_ns_carbs_last_synced_id)
        sp.remove(R.string.key_ns_bolus_calculator_result_last_synced_id)
        sp.remove(R.string.key_ns_device_status_last_synced_id)
        sp.remove(R.string.key_ns_temporary_basal_last_synced_id)
        sp.remove(R.string.key_ns_extended_bolus_last_synced_id)
        sp.remove(R.string.key_ns_therapy_event_last_synced_id)
        sp.remove(R.string.key_ns_profile_switch_last_synced_id)
        sp.remove(R.string.key_ns_profile_store_last_synced_timestamp)
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
                    nsClientPlugin.nsClientService?.dbAdd("treatments", bolus.first.toJson(dateUtil), DataSyncSelector.PairBolus(bolus.first, bolus.second))
                // existing with nsId = update
                bolus.first.isValid && bolus.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", bolus.first.interfaceIDs.nightscoutId, bolus.first.toJson(dateUtil), DataSyncSelector.PairBolus(bolus.first, bolus.second))
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
                    nsClientPlugin.nsClientService?.dbAdd("treatments", carb.first.toJson(dateUtil), DataSyncSelector.PairCarbs(carb.first, carb.second))
                // existing with nsId = update
                carb.first.isValid && carb.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", carb.first.interfaceIDs.nightscoutId, carb.first.toJson(dateUtil), DataSyncSelector.PairCarbs(carb.first, carb.second))
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
                    nsClientPlugin.nsClientService?.dbAdd("treatments", bolusCalculatorResult.first.toJson(dateUtil), DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second))
                // existing with nsId = update
                bolusCalculatorResult.first.isValid && bolusCalculatorResult.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", bolusCalculatorResult.first.interfaceIDs.nightscoutId, bolusCalculatorResult.first.toJson(dateUtil), DataSyncSelector.PairBolusCalculatorResult(bolusCalculatorResult.first, bolusCalculatorResult.second))
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
                    nsClientPlugin.nsClientService?.dbAdd("treatments", tt.first.toJson(profileFunction.getUnits(), dateUtil), DataSyncSelector.PairTemporaryTarget(tt.first, tt.second))
                // existing with nsId = update
                tt.first.isValid && tt.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", tt.first.interfaceIDs.nightscoutId, tt.first.toJson(profileFunction.getUnits(), dateUtil), DataSyncSelector.PairTemporaryTarget(tt.first, tt.second))
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
            if (activePlugin.activeBgSource.shouldUploadToNs(gv.first)) {
                when {
                    // removed and not uploaded yet = ignore
                    !gv.first.isValid && gv.first.interfaceIDs.nightscoutId == null -> Any()
                    // removed and already uploaded = send for removal
                    !gv.first.isValid && gv.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbRemove("entries", gv.first.interfaceIDs.nightscoutId, DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
                    // existing without nsId = create new
                    gv.first.isValid && gv.first.interfaceIDs.nightscoutId == null  ->
                        nsClientPlugin.nsClientService?.dbAdd("entries", gv.first.toJson(dateUtil), DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
                    // existing with nsId = update
                    gv.first.isValid && gv.first.interfaceIDs.nightscoutId != null  ->
                        nsClientPlugin.nsClientService?.dbUpdate("entries", gv.first.interfaceIDs.nightscoutId, gv.first.toJson(dateUtil), DataSyncSelector.PairGlucoseValue(gv.first, gv.second))
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

    override fun confirmLastDeviceStatusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting DeviceStatus data sync from $lastSynced")
            sp.putLong(R.string.key_ns_device_status_last_synced_id, lastSynced)
        }
    }

    override fun changedDeviceStatuses(): List<DeviceStatus> {
        val startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        return appRepository.getModifiedDeviceStatusDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading DeviceStatus data for sync from $startId. Records ${it.size}")
        }
    }

    override fun processChangedDeviceStatusesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_device_status_last_synced_id, 0)
        appRepository.getNextSyncElementDeviceStatus(startId).blockingGet()?.let { deviceStatus ->
            aapsLogger.info(LTag.DATABASE, "Loading DeviceStatus data Start: $startId ID: ${deviceStatus.id}")
            when {
                // without nsId = create new
                deviceStatus.interfaceIDs.nightscoutId == null ->
                    nsClientPlugin.nsClientService?.dbAdd("devicestatus", deviceStatus.toJson(dateUtil), deviceStatus)
                // with nsId = ignore
                deviceStatus.interfaceIDs.nightscoutId != null -> Any()
            }
            return true
        }
        return false
    }

    override fun confirmLastTemporaryBasalIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting TemporaryBasal data sync from $lastSynced")
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

    override fun processChangedTemporaryBasalsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_temporary_basal_last_synced_id, 0)
        appRepository.getNextSyncElementTemporaryBasal(startId).blockingGet()?.let { tb ->
            aapsLogger.info(LTag.DATABASE, "Loading TemporaryBasal data Start: $startId ID: ${tb.first.id} HistoryID: ${tb.second} ")
            profileFunction.getProfile(tb.first.timestamp)?.let { profile ->
                when {
                    // removed and not uploaded yet = ignore
                    !tb.first.isValid && tb.first.interfaceIDs.nightscoutId == null -> Any()
                    // removed and already uploaded = send for removal
                    !tb.first.isValid && tb.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbRemove("treatments", tb.first.interfaceIDs.nightscoutId, DataSyncSelector.PairTemporaryBasal(tb.first, tb.second))
                    // existing without nsId = create new
                    tb.first.isValid && tb.first.interfaceIDs.nightscoutId == null  ->
                        nsClientPlugin.nsClientService?.dbAdd("treatments", tb.first.toJson(profile, dateUtil), DataSyncSelector.PairTemporaryBasal(tb.first, tb.second))
                    // existing with nsId = update
                    tb.first.isValid && tb.first.interfaceIDs.nightscoutId != null  ->
                        nsClientPlugin.nsClientService?.dbUpdate("treatments", tb.first.interfaceIDs.nightscoutId, tb.first.toJson(profile, dateUtil), DataSyncSelector.PairTemporaryBasal(tb.first, tb.second))
                }
                return true
            }
        }
        return false
    }

    override fun confirmLastExtendedBolusIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting ExtendedBolus data sync from $lastSynced")
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

    override fun processChangedExtendedBolusesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_extended_bolus_last_synced_id, 0)
        appRepository.getNextSyncElementExtendedBolus(startId).blockingGet()?.let { eb ->
            aapsLogger.info(LTag.DATABASE, "Loading ExtendedBolus data Start: $startId ID: ${eb.first.id} HistoryID: ${eb.second} ")
            profileFunction.getProfile(eb.first.timestamp)?.let { profile ->
                when {
                    // removed and not uploaded yet = ignore
                    !eb.first.isValid && eb.first.interfaceIDs.nightscoutId == null -> Any()
                    // removed and already uploaded = send for removal
                    !eb.first.isValid && eb.first.interfaceIDs.nightscoutId != null ->
                        nsClientPlugin.nsClientService?.dbRemove("treatments", eb.first.interfaceIDs.nightscoutId, DataSyncSelector.PairExtendedBolus(eb.first, eb.second))
                    // existing without nsId = create new
                    eb.first.isValid && eb.first.interfaceIDs.nightscoutId == null  ->
                        nsClientPlugin.nsClientService?.dbAdd("treatments", eb.first.toJson(profile, dateUtil), DataSyncSelector.PairExtendedBolus(eb.first, eb.second))
                    // existing with nsId = update
                    eb.first.isValid && eb.first.interfaceIDs.nightscoutId != null  ->
                        nsClientPlugin.nsClientService?.dbUpdate("treatments", eb.first.interfaceIDs.nightscoutId, eb.first.toJson(profile, dateUtil), DataSyncSelector.PairExtendedBolus(eb.first, eb.second))
                }
                return true
            }
        }
        return false
    }

    override fun confirmLastProfileSwitchIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, "Setting ProfileSwitch data sync from $lastSynced")
            sp.putLong(R.string.key_ns_profile_switch_last_synced_id, lastSynced)
        }
    }

    override fun changedProfileSwitch(): List<ProfileSwitch> {
        val startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        return appRepository.getModifiedProfileSwitchDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading ProfileSwitch data for sync from $startId. Records ${it.size}")
        }
    }

    override fun processChangedProfileSwitchesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_profile_switch_last_synced_id, 0)
        appRepository.getNextSyncElementProfileSwitch(startId).blockingGet()?.let { eb ->
            aapsLogger.info(LTag.DATABASE, "Loading ProfileSwitch data Start: $startId ID: ${eb.first.id} HistoryID: ${eb.second} ")
            when {
                // removed and not uploaded yet = ignore
                !eb.first.isValid && eb.first.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !eb.first.isValid && eb.first.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", eb.first.interfaceIDs.nightscoutId, DataSyncSelector.PairProfileSwitch(eb.first, eb.second))
                // existing without nsId = create new
                eb.first.isValid && eb.first.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", eb.first.toJson(dateUtil), DataSyncSelector.PairProfileSwitch(eb.first, eb.second))
                // existing with nsId = update
                eb.first.isValid && eb.first.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", eb.first.interfaceIDs.nightscoutId, eb.first.toJson(dateUtil), DataSyncSelector.PairProfileSwitch(eb.first, eb.second))
            }
            return true
        }
        return false
    }

    override fun confirmLastProfileStore(lastSynced: Long) {
        sp.putLong(R.string.key_ns_profile_store_last_synced_timestamp, lastSynced)
    }

    override fun processChangedProfileStore() {
        val lastSync = sp.getLong(R.string.key_ns_profile_store_last_synced_timestamp, 0)
        val lastChange = sp.getLong(R.string.key_local_profile_last_change, 0)
        if (lastChange == 0L) return
        localProfilePlugin.createProfileStore()
        val profileJson = localProfilePlugin.profile?.data ?: return
        if (lastChange > lastSync)
            nsClientPlugin.nsClientService?.dbAdd("profile", profileJson, DataSyncSelector.PairProfileStore(profileJson, dateUtil.now()))
    }
}
