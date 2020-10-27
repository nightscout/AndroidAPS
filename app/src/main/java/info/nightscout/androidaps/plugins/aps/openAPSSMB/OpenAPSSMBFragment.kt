package info.nightscout.androidaps.plugins.aps.openAPSSMB

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JSONFormatter
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.openapsama_fragment.*
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

class OpenAPSSMBFragment : DaggerFragment() {
    private var disposable: CompositeDisposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
    @Inject lateinit var dateUtil: DateUtil

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.openapsama_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        openapsma_run.setOnClickListener {
            openAPSSMBPlugin.invoke("OpenAPSSMB button", false)
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGUI()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventOpenAPSUpdateResultGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateResultGUI(it.text)
            }, { fabricPrivacy.logException(it) })

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGUI() {
        if (openapsma_result == null) return
        openAPSSMBPlugin.lastAPSResult?.let { lastAPSResult ->
            openapsma_result.text = JSONFormatter.format(lastAPSResult.json)
            openapsma_request.text = lastAPSResult.toSpanned()
        }
        openAPSSMBPlugin.lastDetermineBasalAdapterSMBJS?.let { determineBasalAdapterSMBJS ->
            openapsma_glucosestatus.text = JSONFormatter.format(determineBasalAdapterSMBJS.glucoseStatusParam)
            openapsma_currenttemp.text = JSONFormatter.format(determineBasalAdapterSMBJS.currentTempParam)
            try {
                val iobArray = JSONArray(determineBasalAdapterSMBJS.iobDataParam)
                openapsma_iobdata.text = TextUtils.concat(resourceHelper.gs(R.string.array_of_elements, iobArray.length()) + "\n", JSONFormatter.format(iobArray.getString(0)))
            } catch (e: JSONException) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e)
                @SuppressLint("SetTextI18n")
                openapsma_iobdata.text = "JSONException see log for details"
            }

            openapsma_profile.text = JSONFormatter.format(determineBasalAdapterSMBJS.profileParam)
            openapsma_mealdata.text = JSONFormatter.format(determineBasalAdapterSMBJS.mealDataParam)
            openapsma_scriptdebugdata.text = determineBasalAdapterSMBJS.scriptDebug
            openAPSSMBPlugin.lastAPSResult?.inputConstraints?.let {
                openapsma_constraints.text = it.getReasons(aapsLogger)
            }
        }
        if (openAPSSMBPlugin.lastAPSRun != 0L) {
            openapsma_lastrun.text = dateUtil.dateAndTimeString(openAPSSMBPlugin.lastAPSRun)
        }
        openAPSSMBPlugin.lastAutosensResult?.let {
            openapsma_autosensdata.text = JSONFormatter.format(it.json())
        }
    }

    @Synchronized
    private fun updateResultGUI(text: String) {
        if (openapsma_result == null) return
        openapsma_result.text = text
        openapsma_glucosestatus.text = ""
        openapsma_currenttemp.text = ""
        openapsma_iobdata.text = ""
        openapsma_profile.text = ""
        openapsma_mealdata.text = ""
        openapsma_autosensdata.text = ""
        openapsma_scriptdebugdata.text = ""
        openapsma_request.text = ""
        openapsma_lastrun.text = ""
    }
}
