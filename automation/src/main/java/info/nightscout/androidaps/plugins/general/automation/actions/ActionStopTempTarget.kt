package info.nightscout.androidaps.plugins.general.automation.actions

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class ActionStopTempTarget(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    override fun friendlyName(): Int = R.string.stoptemptarget
    override fun shortDescription(): String = resourceHelper.gs(R.string.stoptemptarget)
    override fun icon(): Int = R.drawable.ic_stop_24dp

    override fun doAction(callback: Callback) {
        disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(dateUtil.now()))
            .subscribe({ result ->
                uel.log(UserEntry.Action.CANCEL_TT, Sources.Automation, title)
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
            }, {
                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
            })
        callback.result(PumpEnactResult(injector).success(true).comment(R.string.ok))?.run()
    }

    override fun isValid(): Boolean = true
}