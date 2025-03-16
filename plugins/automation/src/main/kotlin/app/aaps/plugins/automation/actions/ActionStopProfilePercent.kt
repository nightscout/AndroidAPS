package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.plugins.automation.R
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ActionStopProfilePercent(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    override fun friendlyName(): Int = R.string.stopprofilepercent
    override fun shortDescription(): String = rh.gs(R.string.stopprofilepercent)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
       if(profileFunction.createProfileSwitch(0, 100, 0)) {
            uel.log(
                UserEntry.Action.PROFILE_SWITCH,
                Sources.Automation,
                title + ": " + rh.gs(app.aaps.core.ui.R.string.startprofile,100, 0),
                ValueWithUnit.Percent(100),
                ValueWithUnit.Minute(0)
            )
            callback.result(PumpEnactResult(injector).success(true).comment(app.aaps.core.ui.R.string.ok)).run()
        } else {
            aapsLogger.error(LTag.AUTOMATION, "Final profile not valid")
            callback.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.ok)).run()
        }
    }

    override fun isValid(): Boolean = true
}