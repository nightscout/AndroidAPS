package info.nightscout.androidaps.plugins.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.virtualpump_fragment.*
import org.slf4j.LoggerFactory

class VirtualPumpFragment : Fragment() {
    private val disposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.virtualpump_fragment, container, false)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventVirtualPumpUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        disposable.add(RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        disposable.add(RxBus
                .toObservable(EventExtendedBolusChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun updateGui() {
        val virtualPump = VirtualPumpPlugin.getPlugin()
        virtualpump_basabasalrate?.text = MainApp.gs(R.string.pump_basebasalrate, virtualPump.baseBasalRate)
        virtualpump_tempbasal?.text = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
                ?: ""
        virtualpump_extendedbolus?.text = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis())?.toString() ?: ""
        virtualpump_battery?.text = MainApp.gs(R.string.format_percent, virtualPump.batteryPercent)
        virtualpump_reservoir?.text = MainApp.gs(R.string.formatinsulinunits, virtualPump.reservoirInUnits.toDouble())

        virtualPump.refreshConfiguration()
        val pumpType = virtualPump.pumpType

        virtualpump_type?.text = pumpType.description
        virtualpump_type_def?.text = pumpType.getFullDescription(MainApp.gs(R.string.virtualpump_pump_def), pumpType.hasExtendedBasals())
    }
}
