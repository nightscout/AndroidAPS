package info.nightscout.androidaps.plugins.treatments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.tabs.TabLayout
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
import kotlinx.android.synthetic.main.localprofile_fragment.*
import kotlinx.android.synthetic.main.treatments_fragment.*
import kotlinx.android.synthetic.main.treatments_fragment.tabLayout
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

        tabLayout.addTab(tabLayout.newTab().setText(R.string.bolus))

        if (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0)
            tabLayout.addTab(tabLayout.newTab().setText(R.string.extended_bolus))

        tabLayout.addTab(tabLayout.newTab().setText(R.string.tempbasal_label))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.careportal_temporarytarget))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.careportal_profileswitch))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.careportal))


        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if ( tab.text == getText(R.string.bolus)) {
                    setFragment(TreatmentsBolusFragment())
                }
                if (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0){
                    if ( tab.text == getText(R.string.extended_bolus)) {
                        setFragment(TreatmentsExtendedBolusesFragment())
                    }
                }
                if ( tab.text == getText(R.string.tempbasal_label)) {
                    setFragment(TreatmentsTemporaryBasalsFragment())
                }
                if ( tab.text == getText(R.string.careportal_temporarytarget)) {
                    setFragment(TreatmentsTempTargetFragment())
                }
                if ( tab.text == getText(R.string.careportal_profileswitch)) {
                    setFragment(TreatmentsProfileSwitchFragment())
                }
                if ( tab.text == getText(R.string.careportal)) {
                    setFragment(TreatmentsCareportalFragment())
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        setFragment(TreatmentsBolusFragment())
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


    private fun updateGui() {
    }
}