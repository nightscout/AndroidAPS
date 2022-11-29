package info.nightscout.androidaps.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.general.overview.OverviewPlugin
import info.nightscout.plugins.general.overview.events.EventUpdateOverviewIobCob
import info.nightscout.plugins.general.overview.events.EventUpdateOverviewSensitivity
import info.nightscout.rx.bus.RxBus
import javax.inject.Inject

class UpdateIobCobSensWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var overviewPlugin: OverviewPlugin

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override fun doWork(): Result {
        overviewPlugin.overviewBus.send(EventUpdateOverviewIobCob("UpdateIobCobSensWorker"))
        overviewPlugin.overviewBus.send(EventUpdateOverviewSensitivity("UpdateIobCobSensWorker"))
        return Result.success()
    }
}