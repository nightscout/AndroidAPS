package info.nightscout.androidaps.plugins.aps.loop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.loop_fragment.*
import javax.inject.Inject

class LoopFragment : DaggerFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.loop_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loop_run.setOnClickListener {
            loop_lastrun.text = resourceHelper.gs(R.string.executing)
            Thread { loopPlugin.invoke("Loop button", true) }.start()
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGUI()
            }, { fabricPrivacy.logException(it) })

        disposable += rxBus
            .toObservable(EventLoopSetLastRunGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                clearGUI()
                loop_lastrun?.text = it.text
            }, { fabricPrivacy.logException(it) })

        updateGUI()
        sp.putBoolean(R.string.key_objectiveuseloop, true)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGUI() {
        if (loop_request == null) return
        loopPlugin.lastRun?.let {
            loop_request?.text = it.request?.toSpanned() ?: ""
            loop_constraintsprocessed?.text = it.constraintsProcessed?.toSpanned() ?: ""
            loop_source?.text = it.source ?: ""
            loop_lastrun?.text = dateUtil.dateAndTimeString(it.lastAPSRun)
                ?: ""
            loop_smbrequest_time?.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBRequest)
            loop_smbexecution_time?.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBEnact)
            loop_tbrrequest_time?.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBRRequest)
            loop_tbrexecution_time?.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBREnact)

            loop_tbrsetbypump?.text = it.tbrSetByPump?.let { tbrSetByPump -> HtmlHelper.fromHtml(tbrSetByPump.toHtml()) }
                ?: ""
            loop_smbsetbypump?.text = it.smbSetByPump?.let { smbSetByPump -> HtmlHelper.fromHtml(smbSetByPump.toHtml()) }
                ?: ""

            val constraints =
                it.constraintsProcessed?.let { constraintsProcessed ->
                    val allConstraints = Constraint(0.0)
                    constraintsProcessed.rateConstraint?.let { rateConstraint -> allConstraints.copyReasons(rateConstraint) }
                    constraintsProcessed.smbConstraint?.let { smbConstraint -> allConstraints.copyReasons(smbConstraint) }
                    allConstraints.getMostLimitedReasons(aapsLogger)
                } ?: ""
            loop_constraints?.text = constraints
        }
    }

    @Synchronized
    private fun clearGUI() {
        loop_request?.text = ""
        loop_constraints?.text = ""
        loop_constraintsprocessed?.text = ""
        loop_source?.text = ""
        loop_lastrun?.text = ""
        loop_smbrequest_time?.text = ""
        loop_smbexecution_time?.text = ""
        loop_tbrrequest_time?.text = ""
        loop_tbrexecution_time?.text = ""
        loop_tbrsetbypump?.text = ""
        loop_smbsetbypump?.text = ""
    }
}
