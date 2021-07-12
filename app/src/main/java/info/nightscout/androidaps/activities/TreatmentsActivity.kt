package info.nightscout.androidaps.activities

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
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

        binding.tempBasals.visibility = buildHelper.isEngineeringMode().toVisibility()
        binding.extendedBoluses.visibility = (buildHelper.isEngineeringMode() && !activePlugin.activePump.isFakingTempsByExtendedBoluses).toVisibility()

        binding.treatments.setOnClickListener {
            setFragment(TreatmentsBolusCarbsFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.extendedBoluses.setOnClickListener {
            setFragment(TreatmentsExtendedBolusesFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.tempBasals.setOnClickListener {
            setFragment(TreatmentsTemporaryBasalsFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.tempTargets.setOnClickListener {
            setFragment(TreatmentsTempTargetFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.profileSwitches.setOnClickListener {
            setFragment(TreatmentsProfileSwitchFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.careportal.setOnClickListener {
            setFragment(TreatmentsCareportalFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.userentry.setOnClickListener {
            setFragment(TreatmentsUserEntryFragment())
            setBackgroundColorOnSelected(it)
        }
        setFragment(TreatmentsBolusCarbsFragment())
        setBackgroundColorOnSelected(binding.treatments)
    }

    private fun setFragment(selectedFragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment)
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .commit()
    }

    private fun setBackgroundColorOnSelected(selected: View) {
        binding.treatments.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.extendedBoluses.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.tempBasals.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.tempTargets.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.profileSwitches.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.careportal.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.userentry.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
    }

}
