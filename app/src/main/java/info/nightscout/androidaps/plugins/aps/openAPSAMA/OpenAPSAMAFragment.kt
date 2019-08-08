package info.nightscout.androidaps.plugins.aps.openAPSAMA

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JSONFormatter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.openapsama_fragment.*
import org.json.JSONArray
import org.json.JSONException
import org.slf4j.LoggerFactory

class OpenAPSAMAFragment : Fragment() {
    private val log = LoggerFactory.getLogger(L.APS)
    private var disposable: CompositeDisposable = CompositeDisposable()

    operator fun CompositeDisposable.plusAssign(disposable: Disposable) {
        add(disposable)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.openapsama_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        openapsma_run.setOnClickListener {
            OpenAPSAMAPlugin.getPlugin().invoke("OpenAPSAMA button", false)
        }

        updateGUI()
    }

    override fun onResume() {
        super.onResume()

        disposable += RxBus
                .toObservable(EventOpenAPSUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateGUI()
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventOpenAPSUpdateResultGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    updateResultGUI(it.text)
                }, {
                    FabricPrivacy.logException(it)
                })
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun updateGUI() {
        OpenAPSAMAPlugin.getPlugin().lastAPSResult?.let { lastAPSResult ->
            openapsma_result.text = JSONFormatter.format(lastAPSResult.json)
            openapsma_request.text = lastAPSResult.toSpanned()
        }
        OpenAPSAMAPlugin.getPlugin().lastDetermineBasalAdapterAMAJS?.let { determineBasalAdapterAMAJS ->
            openapsma_glucosestatus.text = JSONFormatter.format(determineBasalAdapterAMAJS.glucoseStatusParam)
            openapsma_currenttemp.text = JSONFormatter.format(determineBasalAdapterAMAJS.currentTempParam)
            try {
                val iobArray = JSONArray(determineBasalAdapterAMAJS.iobDataParam)
                openapsma_iobdata.text = TextUtils.concat(String.format(MainApp.gs(R.string.array_of_elements), iobArray.length()) + "\n", JSONFormatter.format(iobArray.getString(0)))
            } catch (e: JSONException) {
                log.error("Unhandled exception", e)
                openapsma_iobdata.text = "JSONException see log for details"
            }

            openapsma_profile.text = JSONFormatter.format(determineBasalAdapterAMAJS.profileParam)
            openapsma_mealdata.text = JSONFormatter.format(determineBasalAdapterAMAJS.mealDataParam)
            openapsma_scriptdebugdata.text = determineBasalAdapterAMAJS.scriptDebug
        }
        if (OpenAPSAMAPlugin.getPlugin().lastAPSRun != 0L) {
            openapsma_lastrun.text = DateUtil.dateAndTimeFullString(OpenAPSAMAPlugin.getPlugin().lastAPSRun)
        }
        OpenAPSAMAPlugin.getPlugin().lastAutosensResult?.let {
            openapsma_autosensdata.text = JSONFormatter.format(it.json())
        }
    }

    private fun updateResultGUI(text: String) {
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
