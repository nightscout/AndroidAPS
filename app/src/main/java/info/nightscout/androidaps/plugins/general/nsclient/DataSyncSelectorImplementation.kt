package info.nightscout.androidaps.plugins.general.nsclient

import androidx.work.ListenableWorker
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.transactions.UpdateTemporaryTargetTransaction
import info.nightscout.androidaps.db.DbRequest
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
    private val appRepository: AppRepository
) : DataSyncSelector {

    /*
    val updateTempTargetNsId = Runnable {
        interfaceIDs.nightscoutId = nsId
        repository.runTransactionForResult(UpdateTemporaryTargetTransaction(this))
            .doOnError {
                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                ret = ListenableWorker.Result.failure()
            }
            .blockingGet()
    }
     */

    override fun resetToNextFullSync() {
        sp.remove(R.string.key_ns_temporary_target_last_sync)
    }

    override fun confirmTempTargetsTimestamp(lastSynced: Long) {
        aapsLogger.debug(LTag.NSCLIENT, "Setting TT data sync from $lastSynced")
        sp.putLong(R.string.key_ns_temporary_target_last_sync, lastSynced)
    }

    override fun confirmTempTargetsTimestampIfGreater(lastSynced: Long) {
        aapsLogger.debug(LTag.NSCLIENT, "Setting TT data sync from $lastSynced")
        if (lastSynced > sp.getLong(R.string.key_ns_temporary_target_last_sync, 0)) {
            aapsLogger.debug(LTag.NSCLIENT, ">>> Setting TT data sync from $lastSynced")
            sp.putLong(R.string.key_ns_temporary_target_last_sync, lastSynced)
        }
    }

    override fun changedTempTargets(): List<TemporaryTarget> {
        val startTime = sp.getLong(R.string.key_ns_temporary_target_last_sync, 0)
        return appRepository.getAllChangedTemporaryTargetsFromTime(startTime, 1).blockingGet().also {
            aapsLogger.debug(LTag.NSCLIENT, "Loading TT data for sync from $startTime ${dateUtil.dateAndTimeAndSecondsString(startTime)}. Records ${it.size}")
        }
    }

    override fun processChangedTempTargetsCompat(): Boolean {
        val changedTT = changedTempTargets()
        changedTT.forEach { tt ->
            when {
                // removed and not uploaded yet = ignore
                !tt.isValid && tt.interfaceIDs.nightscoutId == null -> Any()
                // removed and already uploaded = send for removal
                !tt.isValid && tt.interfaceIDs.nightscoutId != null ->
                    nsClientPlugin.nsClientService?.dbRemove("treatments", tt.interfaceIDs.nightscoutId, tt)
                // existing without nsId = create new
                tt.isValid && tt.interfaceIDs.nightscoutId == null  ->
                    nsClientPlugin.nsClientService?.dbAdd("treatments", tt.toJson(profileFunction.getUnits()), tt)
                // existing with nsId = update
                tt.isValid && tt.interfaceIDs.nightscoutId != null  ->
                    nsClientPlugin.nsClientService?.dbUpdate("treatments", tt.interfaceIDs.nightscoutId, tt.toJson(profileFunction.getUnits()), tt)
            }
        }
        return changedTT.isNotEmpty()
    }
}