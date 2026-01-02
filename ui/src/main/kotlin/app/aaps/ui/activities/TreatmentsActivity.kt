package app.aaps.ui.activities

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.ui.R
import app.aaps.ui.activities.fragments.TreatmentsBolusCarbsFragment
import app.aaps.ui.activities.fragments.TreatmentsCareportalFragment
import app.aaps.ui.activities.fragments.TreatmentsExtendedBolusesFragment
import app.aaps.ui.activities.fragments.TreatmentsProfileSwitchFragment
import app.aaps.ui.activities.fragments.TreatmentsRunningModeFragment
import app.aaps.ui.activities.fragments.TreatmentsTempTargetFragment
import app.aaps.ui.activities.fragments.TreatmentsTemporaryBasalsFragment
import app.aaps.ui.activities.fragments.TreatmentsUserEntryFragment
import app.aaps.ui.databinding.TreatmentsFragmentBinding
import com.google.android.material.tabs.TabLayout
import javax.inject.Inject

class TreatmentsActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper

    private lateinit var binding: TreatmentsFragmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = TreatmentsFragmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Use index, TabItems crashes with an id
        val showEbTab = !activePlugin.activePump.isFakingTempsByExtendedBoluses && activePlugin.activePump.pumpDescription.isExtendedBolusCapable
        binding.treatmentsTabs.getTabAt(1)?.view?.visibility = showEbTab.toVisibility()

        setFragment(TreatmentsBolusCarbsFragment())
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = rh.gs(R.string.carbs_and_bolus)

        binding.treatmentsTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val fragment = when (tab.position) {
                    0    -> TreatmentsBolusCarbsFragment::class.java
                    1    -> TreatmentsExtendedBolusesFragment::class.java
                    2    -> TreatmentsTemporaryBasalsFragment::class.java
                    3    -> TreatmentsTempTargetFragment::class.java
                    4    -> TreatmentsProfileSwitchFragment::class.java
                    5    -> TreatmentsCareportalFragment::class.java
                    6    -> TreatmentsRunningModeFragment::class.java
                    else -> TreatmentsUserEntryFragment::class.java
                }
                setFragment(fragment.getDeclaredConstructor().newInstance())
                supportActionBar?.title = tab.contentDescription
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setFragment(selectedFragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }
}