package info.nightscout.androidaps.plugins.aps.loop

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
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
import java.util.*
import javax.inject.Inject

class LoopFragment : DaggerFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var dateUtil: DateUtil

    private lateinit var mRandom: Random
    private lateinit var mHandler: Handler
    private lateinit var mRunnable:Runnable
    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.loop_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        swipeRefresh_loop.setColorSchemeResources(R.color.orange, R.color.green, R.color.blue)
        swipeRefresh_loop.setProgressBackgroundColorSchemeColor(ResourcesCompat.getColor(resources, R.color.swipe_background, null))

        // Initialize a new Random instance
        mRandom = Random()

        // Initialize the handler instance
        mHandler = Handler()

        swipeRefresh_loop.setOnRefreshListener {
            mRunnable = Runnable {
                loop_lastrun.text = resourceHelper.gs(R.string.executing)
                Thread { loopPlugin.invoke("Loop button", true) }.start()
                // Hide swipe to refresh icon animation
                swipeRefresh_loop.isRefreshing = false
            }

            // Execute the task after specified time
            mHandler.postDelayed(
                mRunnable,
                (3000).toLong() // Delay 1 to 5 seconds
            )
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
