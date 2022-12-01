package info.nightscout.workflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.android.HasAndroidInjector
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventUpdateOverviewIobCob
import info.nightscout.rx.events.EventUpdateOverviewSensitivity
import javax.inject.Inject

class UpdateIobCobSensWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
    }

    override fun doWork(): Result {
        activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewIobCob("UpdateIobCobSensWorker"))
        activePlugin.activeOverview.overviewBus.send(EventUpdateOverviewSensitivity("UpdateIobCobSensWorker"))
        return Result.success()
    }
}