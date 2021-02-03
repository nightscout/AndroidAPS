package info.nightscout.androidaps.plugins.treatments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.TreatmentsFragmentBinding
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.fragments.*
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin

    private val disposable = CompositeDisposable()

    private var _binding: TreatmentsFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.treatments.setOnClickListener {
            setFragment(TreatmentsBolusFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.extendedboluses.setOnClickListener {
            setFragment(TreatmentsExtendedBolusesFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.tempbasals.setOnClickListener {
            setFragment(TreatmentsTemporaryBasalsFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.temptargets.setOnClickListener {
            setFragment(TreatmentsTempTargetFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.profileswitches.setOnClickListener {
            setFragment(TreatmentsProfileSwitchFragment())
            setBackgroundColorOnSelected(it)
        }
        binding.careportal.setOnClickListener {
            setFragment(TreatmentsCareportalFragment())
            setBackgroundColorOnSelected(it)
        }
        setFragment(TreatmentsBolusFragment())
        setBackgroundColorOnSelected(binding.treatments)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setFragment(selectedFragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment) // f2_container is your FrameLayout container
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .addToBackStack(null)
            .commit()
    }

    private fun setBackgroundColorOnSelected(selected: View) {
        binding.treatments.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.extendedboluses.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.tempbasals.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.temptargets.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.profileswitches.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        binding.careportal.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.extendedboluses.visibility = (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0).toVisibility()
    }
}