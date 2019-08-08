package info.nightscout.androidaps.plugins.aps.loop


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.loop_fragment.*

class LoopFragment : Fragment() {

    private var disposable: CompositeDisposable = CompositeDisposable()

    operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
        add(disposable)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.loop_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loop_run.setOnClickListener {
            loop_lastrun.text = MainApp.gs(R.string.executing)
            Thread { LoopPlugin.getPlugin().invoke("Loop button", true) }.start()
        }

        updateGUI()
    }

    override fun onResume() {
        super.onResume()
        disposable += RxBus
                .toObservable(EventLoopUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGUI()
                }, {
                    FabricPrivacy.logException(it)
                })

        disposable += RxBus
                .toObservable(EventLoopSetLastRunGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    clearGUI()
                    loop_lastrun.text = it.text
                }, {
                    FabricPrivacy.logException(it)
                })
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun updateGUI() {
        LoopPlugin.lastRun?.let {
            loop_request.text = it.request?.toSpanned() ?: ""
            loop_constraintsprocessed.text = it.constraintsProcessed?.toSpanned() ?: ""
            loop_source.text = it.source ?: ""
            loop_lastrun.text = it.lastAPSRun?.let { lastRun -> DateUtil.dateAndTimeString(lastRun.time) }
                    ?: ""
            loop_lastenact.text = it.lastAPSRun?.let { lastEnact -> DateUtil.dateAndTimeString(lastEnact.time) }
                    ?: ""
            loop_tbrsetbypump.text = it.tbrSetByPump?.let { tbrSetByPump -> HtmlHelper.fromHtml(tbrSetByPump.toHtml()) }
                    ?: ""
            loop_smbsetbypump.text = it.smbSetByPump?.let { smbSetByPump -> HtmlHelper.fromHtml(smbSetByPump.toHtml()) }
                    ?: ""

            val constraints =
                    it.constraintsProcessed?.let { constraintsProcessed ->
                        val allConstraints = Constraint(0.0)
                        constraintsProcessed.rateConstraint?.let { rateConstraint -> allConstraints.copyReasons(rateConstraint) }
                        constraintsProcessed.smbConstraint?.let { smbConstraint -> allConstraints.copyReasons(smbConstraint) }
                        allConstraints.mostLimitedReasons
                    } ?: ""
            loop_constraints.text = constraints
        }
    }

    private fun clearGUI() {
        loop_request.text = ""
        loop_constraints.text = ""
        loop_constraintsprocessed.text = ""
        loop_source.text = ""
        loop_lastrun.text = ""
        loop_lastenact.text = ""
        loop_tbrsetbypump.text = ""
        loop_smbsetbypump.text = ""
    }
}
