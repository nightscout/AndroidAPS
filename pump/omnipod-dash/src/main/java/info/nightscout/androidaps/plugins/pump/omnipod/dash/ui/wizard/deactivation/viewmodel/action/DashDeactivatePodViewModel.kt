package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.notifications.Notification
import app.aaps.interfaces.pump.PumpEnactResult
import app.aaps.interfaces.queue.Callback
import app.aaps.interfaces.queue.CommandQueue
import app.aaps.interfaces.rx.AapsSchedulers
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.rx.events.EventDismissNotification
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandDeactivatePod
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class DashDeactivatePodViewModel @Inject constructor(
    private val podStateManager: OmnipodDashPodStateManager,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus,
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : DeactivatePodViewModel(injector, logger, aapsSchedulers) {

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.create { source ->
        commandQueue.customCommand(
            CommandDeactivatePod(),
            object : Callback() {
                override fun run() {
                    source.onSuccess(result)
                }
            }
        )
    }

    override fun discardPod() {
        podStateManager.reset()
        rxBus.send(EventDismissNotification(Notification.OMNIPOD_POD_FAULT))
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
}
