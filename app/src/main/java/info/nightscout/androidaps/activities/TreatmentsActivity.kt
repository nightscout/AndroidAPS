package info.nightscout.androidaps.activities

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.fragments.*
import info.nightscout.androidaps.databinding.TreatmentsFragmentBinding
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
        //binding.tempBasals.visibility = buildHelper.isEngineeringMode().toVisibility()
        //binding.extendedBoluses.visibility = (buildHelper.isEngineeringMode() && !activePlugin.activePump.isFakingTempsByExtendedBoluses).toVisibility()

        binding.treatments.setOnClickListener {
            setFragment(TreatmentsBolusCarbsFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.carbs_and_bolus)
        }
        binding.extendedBoluses.setOnClickListener {
            setFragment(TreatmentsExtendedBolusesFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.extended_bolus)
        }
        binding.tempBasals.setOnClickListener {
            setFragment(TreatmentsTemporaryBasalsFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.tempbasal_label)
        }
        binding.tempTargets.setOnClickListener {
            setFragment(TreatmentsTempTargetFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.tempt_targets)
        }
        binding.profileSwitches.setOnClickListener {
            setFragment(TreatmentsProfileSwitchFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.profile_changes)
        }
        binding.careportal.setOnClickListener {
            setFragment(TreatmentsCareportalFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.careportal)
        }
        binding.userentry.setOnClickListener {
            setFragment(TreatmentsUserEntryFragment())
            setBackgroundColorOnSelected(it)
            supportActionBar?.title = rh.gs(R.string.user_action)
        }
        setFragment(TreatmentsBolusCarbsFragment())
        setBackgroundColorOnSelected(binding.treatments)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = rh.gs(R.string.carbs_and_bolus)
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

    private fun setBackgroundColorOnSelected(selected: View) {
        binding.treatments.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.extendedBoluses.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.tempBasals.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.tempTargets.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.profileSwitches.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.careportal.setBackgroundColor(rh.gc(R.color.defaultbackground))
        binding.userentry.setBackgroundColor(rh.gc(R.color.defaultbackground))
        selected.setBackgroundColor(rh.gc(R.color.tabBgColorSelected))
    }

}
