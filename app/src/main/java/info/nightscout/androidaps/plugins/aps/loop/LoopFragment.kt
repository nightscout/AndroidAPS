package info.nightscout.androidaps.plugins.aps.loop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.LoopFragmentBinding
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class LoopFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: LoopFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = LoopFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.run.setOnClickListener {
            binding.lastrun.text = resourceHelper.gs(R.string.executing)
            Thread { loopPlugin.invoke("Loop button", true) }.start()
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                updateGUI()
            }, fabricPrivacy::logException)

        disposable += rxBus
            .toObservable(EventLoopSetLastRunGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                clearGUI()
                binding.lastrun.text = it.text
            }, fabricPrivacy::logException)

        updateGUI()
        sp.putBoolean(R.string.key_objectiveuseloop, true)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        loopPlugin.lastRun?.let {
            binding.request.text = it.request?.toSpanned() ?: ""
            binding.constraintsprocessed.text = it.constraintsProcessed?.toSpanned() ?: ""
            binding.source.text = it.source ?: ""
            binding.lastrun.text = dateUtil.dateAndTimeString(it.lastAPSRun)
                ?: ""
            binding.smbrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBRequest)
            binding.smbexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBEnact)
            binding.tbrrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBRRequest)
            binding.tbrexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBREnact)

            binding.tbrsetbypump.text = it.tbrSetByPump?.let { tbrSetByPump -> HtmlHelper.fromHtml(tbrSetByPump.toHtml()) }
                ?: ""
            binding.smbsetbypump.text = it.smbSetByPump?.let { smbSetByPump -> HtmlHelper.fromHtml(smbSetByPump.toHtml()) }
                ?: ""

            val constraints =
                it.constraintsProcessed?.let { constraintsProcessed ->
                    val allConstraints = Constraint(0.0)
                    constraintsProcessed.rateConstraint?.let { rateConstraint -> allConstraints.copyReasons(rateConstraint) }
                    constraintsProcessed.smbConstraint?.let { smbConstraint -> allConstraints.copyReasons(smbConstraint) }
                    allConstraints.getMostLimitedReasons(aapsLogger)
                } ?: ""
            binding.constraints.text = constraints
        }
    }

    @Synchronized
    private fun clearGUI() {
        binding.request.text = ""
        binding.constraints.text = ""
        binding.constraintsprocessed.text = ""
        binding.source.text = ""
        binding.lastrun.text = ""
        binding.smbrequestTime.text = ""
        binding.smbexecutionTime.text = ""
        binding.tbrrequestTime.text = ""
        binding.tbrexecutionTime.text = ""
        binding.tbrsetbypump.text = ""
        binding.smbsetbypump.text = ""
    }
}
