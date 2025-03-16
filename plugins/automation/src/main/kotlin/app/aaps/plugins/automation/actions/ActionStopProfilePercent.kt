package info.nightscout.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.automation.R
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class ActionStopProfilePercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uel: UserEntryLogger

    override fun friendlyName(): Int = R.string.stopprofilepercent
    override fun shortDescription(): String = rh.gs(R.string.stopprofilepercent)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        if (profileFunction.createProfileSwitch(0, 100, 0)) {
            uel.log(
                UserEntry.Action.PROFILE_SWITCH,
                Sources.Automation,
                title + ": " + rh.gs(info.nightscout.core.ui.R.string.startprofile, 100, 0),
                ValueWithUnit.Percent(100),
                ValueWithUnit.Minute(0)
            )
            callback.result(PumpEnactResult(injector).success(true).comment(info.nightscout.core.ui.R.string.ok)).run()
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            callback.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.ok)).run()
        }
    }

    override fun isValid(): Boolean = true
}