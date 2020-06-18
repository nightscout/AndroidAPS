package info.nightscout.androidaps.plugins.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.virtualpump_fragment.*
import javax.inject.Inject

class VirtualPumpFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin

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
        disposable += rxBus
            .toObservable(EventVirtualPumpUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
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
        virtualpump_basabasalrate?.text = resourceHelper.gs(R.string.pump_basebasalrate, virtualPumpPlugin.baseBasalRate)
        virtualpump_tempbasal?.text = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
            ?: ""
        virtualpump_extendedbolus?.text = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis())?.toString()
            ?: ""
        virtualpump_battery?.text = resourceHelper.gs(R.string.format_percent, virtualPumpPlugin.batteryPercent)
        virtualpump_reservoir?.text = resourceHelper.gs(R.string.formatinsulinunits, virtualPumpPlugin.reservoirInUnits.toDouble())

        virtualPumpPlugin.refreshConfiguration()
        val pumpType = virtualPumpPlugin.pumpType

        virtualpump_type?.text = pumpType?.description
        virtualpump_type_def?.text = pumpType?.getFullDescription(resourceHelper.gs(R.string.virtualpump_pump_def), pumpType.hasExtendedBasals(), resourceHelper)
    }
}
