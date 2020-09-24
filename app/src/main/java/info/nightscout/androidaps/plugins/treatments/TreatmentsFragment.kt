package info.nightscout.androidaps.plugins.treatments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.fragments.*
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_fragment.*
import javax.inject.Inject

class TreatmentsFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin

    private val disposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        treatments_treatments.setOnClickListener {
            setFragment(TreatmentsBolusFragment())
            setBackgroundColorOnSelected(it)
        }
        treatments_extendedboluses.setOnClickListener {
            setFragment(TreatmentsExtendedBolusesFragment())
            setBackgroundColorOnSelected(it)
        }
        treatments_tempbasals.setOnClickListener {
            setFragment(TreatmentsTemporaryBasalsFragment())
            setBackgroundColorOnSelected(it)
        }
        treatments_temptargets.setOnClickListener {
            setFragment(TreatmentsTempTargetFragment())
            setBackgroundColorOnSelected(it)
        }
        treatments_profileswitches.setOnClickListener {
            setFragment(TreatmentsProfileSwitchFragment())
            setBackgroundColorOnSelected(it)
        }
        treatments_careportal.setOnClickListener {
            setFragment(TreatmentsCareportalFragment())
            setBackgroundColorOnSelected(it)
        }
        setFragment(TreatmentsBolusFragment())
        setBackgroundColorOnSelected(treatments_treatments)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }) { fabricPrivacy.logException(it) }
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun setFragment(selectedFragment: Fragment) {
        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.treatments_fragment_container, selectedFragment) // f2_container is your FrameLayout container
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        ft.addToBackStack(null)
        ft.commit()
    }

    private fun setBackgroundColorOnSelected(selected: View) {
        treatments_treatments.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        treatments_extendedboluses.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        treatments_tempbasals.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        treatments_temptargets.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        treatments_profileswitches.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        treatments_careportal.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
    }

    private fun updateGui() {
        if (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0)
            treatments_extendedboluses?.visibility = View.VISIBLE
        else
            treatments_extendedboluses?.visibility = View.GONE
    }
}