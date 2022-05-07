package info.nightscout.androidaps.plugins.aps.loop

import android.os.Bundle
import android.view.*
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.LoopFragmentBinding
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.Loop
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopSetLastRunGui
import info.nightscout.androidaps.plugins.aps.loop.events.EventLoopUpdateGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var loop: Loop
    @Inject lateinit var dateUtil: DateUtil

    private val ID_MENU_RUN = 1

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: LoopFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        _binding = LoopFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(binding.swipeRefresh) {
            setColorSchemeColors(rh.gac(context, R.attr.colorPrimaryDark), rh.gac(context, R.attr.colorPrimary), rh.gac(context, R.attr.colorSecondary))
            setOnRefreshListener {
                binding.lastrun.text = rh.gs(info.nightscout.androidaps.R.string.executing)
                Thread { loop.invoke("Loop swiperefresh", true) }.start()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (isResumed) {
            menu.removeItem(ID_MENU_RUN)
            menu.add(Menu.FIRST, ID_MENU_RUN, 0, rh.gs(R.string.openapsma_run)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.setGroupDividerEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_RUN -> {
                binding.lastrun.text = rh.gs(R.string.executing)
                Thread { loop.invoke("Loop menu", true) }.start()
                true
            }

            else        -> false
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
        loop.lastRun?.let {
            binding.request.text = it.request?.toSpanned() ?: ""
            binding.constraintsprocessed.text = it.constraintsProcessed?.toSpanned() ?: ""
            binding.source.text = it.source ?: ""
            binding.lastrun.text = dateUtil.dateAndTimeString(it.lastAPSRun)
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
            binding.swipeRefresh.isRefreshing = false
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
        binding.swipeRefresh.isRefreshing = false
    }
}
