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
import info.nightscout.androidaps.databinding.LoopFragmentBinding
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

        binding.swipeRefreshLoop.setColorSchemeResources(R.color.orange, R.color.green, R.color.blue)
        binding.swipeRefreshLoop.setProgressBackgroundColorSchemeColor(ResourcesCompat.getColor(resources, R.color.swipe_background, null))

        // Initialize a new Random instance
        mRandom = Random()

        // Initialize the handler instance
        mHandler = Handler()

        binding.swipeRefreshLoop.setOnRefreshListener {
            mRunnable = Runnable {
                binding.loopLastrun.text = resourceHelper.gs(R.string.executing)
                Thread { loopPlugin.invoke("Loop button", true) }.start()
                // Hide swipe to refresh icon animation
                binding.swipeRefreshLoop.isRefreshing = false
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
                binding.loopLastrun.text = it.text
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
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        loopPlugin.lastRun?.let {
            binding.loopRequest.text = it.request?.toSpanned() ?: ""
            binding.loopConstraintsprocessed.text = it.constraintsProcessed?.toSpanned() ?: ""
            binding.loopSource.text = it.source ?: ""
            binding.loopLastrun.text = dateUtil.dateAndTimeString(it.lastAPSRun)
                ?: ""
            binding.loopSmbrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBRequest)
            binding.loopSmbexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastSMBEnact)
            binding.loopTbrrequestTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBRRequest)
            binding.loopTbrexecutionTime.text = dateUtil.dateAndTimeAndSecondsString(it.lastTBREnact)

            binding.loopTbrsetbypump.text = it.tbrSetByPump?.let { tbrSetByPump -> HtmlHelper.fromHtml(tbrSetByPump.toHtml()) }
                ?: ""
            binding.loopSmbsetbypump.text = it.smbSetByPump?.let { smbSetByPump -> HtmlHelper.fromHtml(smbSetByPump.toHtml()) }
                ?: ""

            val constraints =
                it.constraintsProcessed?.let { constraintsProcessed ->
                    val allConstraints = Constraint(0.0)
                    constraintsProcessed.rateConstraint?.let { rateConstraint -> allConstraints.copyReasons(rateConstraint) }
                    constraintsProcessed.smbConstraint?.let { smbConstraint -> allConstraints.copyReasons(smbConstraint) }
                    allConstraints.getMostLimitedReasons(aapsLogger)
                } ?: ""
            binding.loopConstraints.text = constraints
        }
    }

    @Synchronized
    private fun clearGUI() {
        binding.loopRequest.text = ""
        binding.loopConstraints.text = ""
        binding.loopConstraintsprocessed.text = ""
        binding.loopSource.text = ""
        binding.loopLastrun.text = ""
        binding.loopSmbrequestTime.text = ""
        binding.loopSmbexecutionTime.text = ""
        binding.loopTbrrequestTime.text = ""
        binding.loopTbrexecutionTime.text = ""
        binding.loopTbrsetbypump.text = ""
        binding.loopSmbsetbypump.text = ""
    }
}
