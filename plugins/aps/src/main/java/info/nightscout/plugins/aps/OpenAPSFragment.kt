package info.nightscout.plugins.aps

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import dagger.android.support.DaggerFragment
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.plugins.aps.databinding.OpenapsFragmentBinding
import info.nightscout.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.plugins.aps.events.EventResetOpenAPSGui
import info.nightscout.plugins.aps.utils.JSONFormatter
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

class OpenAPSFragment : DaggerFragment(), MenuProvider {

    private var disposable: CompositeDisposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var jsonFormatter: JSONFormatter

    @Suppress("PrivatePropertyName")
    private val ID_MENU_RUN = 503

    private var _binding: OpenapsFragmentBinding? = null
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        OpenapsFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefresh.setColorSchemeColors(rh.gac(context, android.R.attr.colorPrimaryDark), rh.gac(context, android.R.attr.colorPrimary), rh.gac(context,com.google.android.material.R.attr.colorSecondary))
        binding.swipeRefresh.setOnRefreshListener {
            binding.lastrun.text = rh.gs(R.string.executing)
            handler.post { activePlugin.activeAPS.invoke("OpenAPS swipe refresh", false) }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_RUN, 0, rh.gs(R.string.openapsma_run)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.setGroupDividerEnabled(true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_RUN -> {
                binding.lastrun.text = rh.gs(R.string.executing)
                handler.post { activePlugin.activeAPS.invoke("OpenAPS menu", false) }
                true
            }

            else        -> false
        }

    @Synchronized
    override fun onResume() {
        super.onResume()

        disposable += rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           updateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventResetOpenAPSGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           resetGUI(it.text)
                       }, fabricPrivacy::logException)

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGUI() {
        if (_binding == null) return
        val openAPSPlugin = activePlugin.activeAPS
        openAPSPlugin.lastAPSResult?.let { lastAPSResult ->
            binding.result.text = jsonFormatter.format(lastAPSResult.json)
            binding.request.text = lastAPSResult.toSpanned()
        }
        openAPSPlugin.lastDetermineBasalAdapter?.let { determineBasalAdapter ->
            binding.glucosestatus.text = jsonFormatter.format(determineBasalAdapter.glucoseStatusParam)
            binding.currenttemp.text = jsonFormatter.format(determineBasalAdapter.currentTempParam)
            try {
                val iobArray = JSONArray(determineBasalAdapter.iobDataParam)
                binding.iobdata.text = TextUtils.concat(rh.gs(R.string.array_of_elements, iobArray.length()) + "\n", jsonFormatter.format(iobArray.getString(0)))
            } catch (e: JSONException) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e)
                @Suppress("SetTextI18n")
                binding.iobdata.text = "JSONException see log for details"
            }

            binding.profile.text = jsonFormatter.format(determineBasalAdapter.profileParam)
            binding.mealdata.text = jsonFormatter.format(determineBasalAdapter.mealDataParam)
            binding.scriptdebugdata.text = determineBasalAdapter.scriptDebug.replace("\\s+".toRegex(), " ")
            openAPSPlugin.lastAPSResult?.inputConstraints?.let {
                binding.constraints.text = it.getReasons(aapsLogger)
            }
        }
        if (openAPSPlugin.lastAPSRun != 0L) {
            binding.lastrun.text = dateUtil.dateAndTimeString(openAPSPlugin.lastAPSRun)
        }
        openAPSPlugin.lastAutosensResult.let {
            binding.autosensdata.text = jsonFormatter.format(it.json())
        }
        binding.swipeRefresh.isRefreshing = false
    }

    @Synchronized
    private fun resetGUI(text: String) {
        if (_binding == null) return
        binding.result.text = text
        binding.glucosestatus.text = ""
        binding.currenttemp.text = ""
        binding.iobdata.text = ""
        binding.profile.text = ""
        binding.mealdata.text = ""
        binding.autosensdata.text = ""
        binding.scriptdebugdata.text = ""
        binding.request.text = ""
        binding.lastrun.text = ""
        binding.swipeRefresh.isRefreshing = false
    }
}