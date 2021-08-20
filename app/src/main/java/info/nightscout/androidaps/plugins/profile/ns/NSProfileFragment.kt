package info.nightscout.androidaps.plugins.profile.ns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.NsprofileFragmentBinding
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.profile.ns.events.EventNSProfileUpdateGUI
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class NSProfileFragment : DaggerFragment() {

    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsProfilePlugin: NSProfilePlugin

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: NsprofileFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = NsprofileFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.profileviewer.closeLayout.close.visibility = View.GONE // not needed for fragment

        binding.profileswitch.setOnClickListener {
            val name = binding.spinner.selectedItem?.toString() ?: ""
            nsProfilePlugin.profile?.let { store ->
                store.getSpecificProfile(name)?.let {
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.nsprofile),
                            resourceHelper.gs(R.string.activate_profile) + ": " + name + " ?", Runnable {
                            treatmentsPlugin.doProfileSwitch(store, name, 0, 100, 0, DateUtil.now())
                        })
                    }
                }
            }
        }

        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (_binding == null) return
                binding.profileviewer.invalidprofile.visibility = View.VISIBLE
                binding.profileviewer.noprofile.visibility = View.VISIBLE
                binding.profileviewer.units.text = ""
                binding.profileviewer.dia.text = ""
                binding.profileviewer.activeprofile.text = ""
                binding.profileviewer.ic.text = ""
                binding.profileviewer.isf.text = ""
                binding.profileviewer.basal.text = ""
                binding.profileviewer.basaltotal.text = ""
                binding.profileviewer.target.text = ""
                binding.profileswitch.visibility = View.GONE
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (_binding == null) return
                val name = binding.spinner.getItemAtPosition(position).toString()

                binding.profileswitch.visibility = View.GONE

                nsProfilePlugin.profile?.let { store ->
                    store.getSpecificProfile(name)?.let { profile ->
                        if (_binding == null) return
                        binding.profileviewer.units.text = profile.units
                        binding.profileviewer.dia.text = resourceHelper.gs(R.string.format_hours, profile.dia)
                        binding.profileviewer.activeprofile.text = name
                        binding.profileviewer.ic.text = profile.icList
                        binding.profileviewer.isf.text = profile.isfList
                        binding.profileviewer.basal.text = profile.basalList
                        binding.profileviewer.basaltotal.text = String.format(resourceHelper.gs(R.string.profile_total), DecimalFormatter.to2Decimal(profile.baseBasalSum()))
                        binding.profileviewer.target.text = profile.targetList
                        binding.profileviewer.basalGraph.show(profile)
                        if (profile.isValid("NSProfileFragment")) {
                            binding.profileviewer.invalidprofile.visibility = View.GONE
                            binding.profileswitch.visibility = View.VISIBLE
                        } else {
                            binding.profileviewer.invalidprofile.visibility = View.VISIBLE
                            binding.profileswitch.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventNSProfileUpdateGUI::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGUI() }, { fabricPrivacy.logException(it) })
        updateGUI()
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

    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        binding.profileviewer.noprofile.visibility = View.VISIBLE

        nsProfilePlugin.profile?.let { profileStore ->
            context?.let { context ->
                val profileList = profileStore.getProfileList()
                val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
                binding.spinner.adapter = adapter
                // set selected to actual profile
                for (p in profileList.indices) {
                    if (profileList[p] == profileFunction.getProfileName())
                        binding.spinner.setSelection(p)
                }
                binding.profileviewer.noprofile.visibility = View.GONE
            }
        }
    }
}
