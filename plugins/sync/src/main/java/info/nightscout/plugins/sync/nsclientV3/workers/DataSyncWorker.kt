package info.nightscout.plugins.sync.nsclientV3.workers

import android.content.Context
import androidx.work.WorkerParameters
import info.nightscout.androidaps.annotations.OpenForTesting
import info.nightscout.core.utils.worker.LoggingWorker
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.sync.DataSyncSelector
import javax.inject.Inject

@OpenForTesting
class DataSyncWorker(
    context: Context, params: WorkerParameters
) : LoggingWorker(context, params) {

    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var activePlugin: ActivePlugin

    override fun doWorkAndLog(): Result {
        if (activePlugin.activeNsClient?.hasWritePermission == true) dataSyncSelector.doUpload()
        return Result.success()
    }
}