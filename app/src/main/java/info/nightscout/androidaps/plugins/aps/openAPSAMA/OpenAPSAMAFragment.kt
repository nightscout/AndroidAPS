package info.nightscout.androidaps.plugins.aps.openAPSAMA

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.OpenapsamaFragmentBinding
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JSONFormatter
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

class OpenAPSAMAFragment : DaggerFragment() {

    private var disposable: CompositeDisposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: OpenapsamaFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = OpenapsamaFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.run.setOnClickListener {
            openAPSAMAPlugin.invoke("OpenAPSAMA button", false)
        }
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
            .toObservable(EventOpenAPSUpdateResultGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                updateResultGUI(it.text)
            }, fabricPrivacy::logException)

        updateGUI()
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
    private fun updateGUI() {
        if (_binding == null) return
        openAPSAMAPlugin.lastAPSResult?.let { lastAPSResult ->
            binding.result.text = JSONFormatter.format(lastAPSResult.json)
            binding.request.text = lastAPSResult.toSpanned()
        }
        openAPSAMAPlugin.lastDetermineBasalAdapterAMAJS?.let { determineBasalAdapterAMAJS ->
            binding.glucosestatus.text = JSONFormatter.format(determineBasalAdapterAMAJS.glucoseStatusParam)
            binding.currenttemp.text = JSONFormatter.format(determineBasalAdapterAMAJS.currentTempParam)
            try {
                val iobArray = JSONArray(determineBasalAdapterAMAJS.iobDataParam)
                binding.iobdata.text = TextUtils.concat(resourceHelper.gs(R.string.array_of_elements, iobArray.length()) + "\n", JSONFormatter.format(iobArray.getString(0)))
            } catch (e: JSONException) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e)
                @Suppress("SetTextI18n")
                binding.iobdata.text = "JSONException see log for details"
            }

            binding.profile.text = JSONFormatter.format(determineBasalAdapterAMAJS.profileParam)
            binding.mealdata.text = JSONFormatter.format(determineBasalAdapterAMAJS.mealDataParam)
            binding.scriptdebugdata.text = determineBasalAdapterAMAJS.scriptDebug
        }
        if (openAPSAMAPlugin.lastAPSRun != 0L) {
            binding.lastrun.text = dateUtil.dateAndTimeString(openAPSAMAPlugin.lastAPSRun)
        }
        openAPSAMAPlugin.lastAutosensResult.let {
            binding.autosensdata.text = JSONFormatter.format(it.json())
        }
    }

    private fun updateResultGUI(text: String) {
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
    }
}
