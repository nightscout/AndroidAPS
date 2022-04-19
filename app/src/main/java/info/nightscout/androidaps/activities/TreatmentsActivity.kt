package info.nightscout.androidaps.activities

import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.fragments.*
import info.nightscout.androidaps.databinding.TreatmentsFragmentBinding
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import javax.inject.Inject

class TreatmentsActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var activePlugin: ActivePlugin

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

        binding.treatmentsTabs.addOnTabSelectedListener(object : OnTabSelectedListener {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else              -> false
        }
    }

    private fun setFragment(selectedFragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

}
