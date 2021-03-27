package info.nightscout.androidaps.plugins.general.nsclient

import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.GlucoseValue
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.extensions.toJson
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class DataSyncSelectorImplementation @Inject constructor(
    private val sp: SP,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val profileFunction: ProfileFunction,
    private val nsClientPlugin: NSClientPlugin,
    private val activePlugin: ActivePluginProvider,
    private val appRepository: AppRepository
) : DataSyncSelector {

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_temporary_target_last_sync)
        sp.remove(R.string.key_ns_glucose_value_last_sync)
    }

    override fun confirmTempTargetsTimestamp(lastSynced: Long) {
        aapsLogger.debug(LTag.NSCLIENT, "Setting TT data sync from $lastSynced")
        sp.putLong(R.string.key_ns_temporary_target_last_sync, lastSynced)
    }

    override fun confirmTempTargetsTimestampIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_sync, 0))
            confirmTempTargetsTimestamp(lastSynced)
    }

    // Prepared for v3 (returns all modified after)
    override fun changedTempTargets(): List<TemporaryTarget> {
        val startId = sp.getLong(R.string.key_ns_temporary_target_last_sync, 0)
        return appRepository.getModifiedTemporaryTargetsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TT data for sync from $startId. Records ${it.size}")
        }
    }

    override fun processChangedTempTargetsCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_temporary_target_last_sync, 0)
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

    override fun confirmLastGlucoseValueId(lastSynced: Long) {
        aapsLogger.debug(LTag.NSCLIENT, "Setting GlucoseValue data sync from $lastSynced")
        sp.putLong(R.string.key_ns_glucose_value_last_sync, lastSynced)
    }

    override fun confirmLastGlucoseValueIdIfGreater(lastSynced: Long) {
        if (lastSynced > sp.getLong(R.string.key_ns_glucose_value_last_sync, 0))
            confirmLastGlucoseValueId(lastSynced)
    }

    // Prepared for v3 (returns all modified after)
    override fun changedGlucoseValues(): List<GlucoseValue> {
        val startId = sp.getLong(R.string.key_ns_glucose_value_last_sync, 0)
        return appRepository.getModifiedBgReadingsDataFromId(startId).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading GlucoseValue data for sync from $startId . Records ${it.size}")
        }
    }

    override fun processChangedGlucoseValuesCompat(): Boolean {
        val startId = sp.getLong(R.string.key_ns_glucose_value_last_sync, 0)
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

}
