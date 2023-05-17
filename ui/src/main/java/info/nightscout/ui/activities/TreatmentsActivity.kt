package info.nightscout.ui.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.tabs.TabLayout
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.activities.fragments.TreatmentsBolusCarbsFragment
import info.nightscout.ui.activities.fragments.TreatmentsCareportalFragment
import info.nightscout.ui.activities.fragments.TreatmentsExtendedBolusesFragment
import info.nightscout.ui.activities.fragments.TreatmentsProfileSwitchFragment
import info.nightscout.ui.activities.fragments.TreatmentsTempTargetFragment
import info.nightscout.ui.activities.fragments.TreatmentsTemporaryBasalsFragment
import info.nightscout.ui.activities.fragments.TreatmentsUserEntryFragment
import info.nightscout.ui.databinding.TreatmentsFragmentBinding
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
        val useFakeTempBasal = activePlugin.activePump.isFakingTempsByExtendedBoluses
        binding.treatmentsTabs.getTabAt(1)?.view?.visibility = useFakeTempBasal.toVisibility()

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
                    else -> TreatmentsUserEntryFragment::class.java
                }
                setFragment(fragment.newInstance())
                supportActionBar?.title = tab.contentDescription
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else              -> super.onOptionsItemSelected(item)
        }

    private fun setFragment(selectedFragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

}