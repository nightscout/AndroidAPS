package info.nightscout.androidaps.plugins.aps.openAPSMA

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.openAPSMA.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JSONFormatter
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.openapsama_fragment.*
import org.slf4j.LoggerFactory

class OpenAPSMAFragment : Fragment() {
    private val log = LoggerFactory.getLogger(L.APS)
    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.openapsma_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        openapsma_run.setOnClickListener {
            OpenAPSMAPlugin.getPlugin().invoke("OpenAPSMA button", false)
        }

    }

    @Synchronized
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
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    private fun updateGUI() {
        if (openapsma_result == null) return
        OpenAPSMAPlugin.getPlugin().lastAPSResult?.let { lastAPSResult ->
            openapsma_result.text = JSONFormatter.format(lastAPSResult.json)
            openapsma_request.text = lastAPSResult.toSpanned()
        }
        OpenAPSMAPlugin.getPlugin().lastDetermineBasalAdapterMAJS?.let { determineBasalAdapterMAJS ->
            openapsma_glucosestatus.text = JSONFormatter.format(determineBasalAdapterMAJS.glucoseStatusParam)
            openapsma_currenttemp.text = JSONFormatter.format(determineBasalAdapterMAJS.currentTempParam)
            openapsma_iobdata.text = JSONFormatter.format(determineBasalAdapterMAJS.iobDataParam)
            openapsma_profile.text = JSONFormatter.format(determineBasalAdapterMAJS.profileParam)
            openapsma_mealdata.text = JSONFormatter.format(determineBasalAdapterMAJS.mealDataParam)
        }
        if (OpenAPSMAPlugin.getPlugin().lastAPSRun != 0L) {
            openapsma_lastrun.text = DateUtil.dateAndTimeString(OpenAPSMAPlugin.getPlugin().lastAPSRun)
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
        openapsma_request.text = ""
        openapsma_lastrun.text = ""
    }
}
