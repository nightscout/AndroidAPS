package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.DialogProfileswitchBinding
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class ProfileSwitchDialog : DialogFragmentWithDate() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider

    private var profileIndex: Int? = null

    private var _binding: DialogProfileswitchBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("percentage", binding.percentage.value)
        savedInstanceState.putDouble("timeshift", binding.timeshift.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        arguments?.let { bundle ->
            profileIndex = bundle.getInt("profileIndex", 0)
        }
        _binding = DialogProfileswitchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.duration.setParams(savedInstanceState?.getDouble("duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok)
        binding.percentage.setParams(savedInstanceState?.getDouble("percentage")
            ?: 100.0, Constants.CPP_MIN_PERCENTAGE.toDouble(), Constants.CPP_MAX_PERCENTAGE.toDouble(), 5.0, DecimalFormat("0"), false, binding.okcancel.ok)
        binding.timeshift.setParams(savedInstanceState?.getDouble("timeshift")
            ?: 0.0, Constants.CPP_MIN_TIMESHIFT.toDouble(), Constants.CPP_MAX_TIMESHIFT.toDouble(), 1.0, DecimalFormat("0"), false, binding.okcancel.ok)

        // profile
        context?.let { context ->
            val profileStore = activePlugin.activeProfileInterface.profile
                ?: return
            val profileList = profileStore.getProfileList()
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            binding.profile.adapter = adapter
            // set selected to actual profile
            if (profileIndex != null)
                binding.profile.setSelection(profileIndex as Int)
            else
                for (p in profileList.indices)
                    if (profileList[p] == profileFunction.getProfileName(false))
                        binding.profile.setSelection(p)
        } ?: return

        treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now())?.let { ps ->
            if (ps.isCPP) {
                binding.reuselayout.visibility = View.VISIBLE
                binding.reusebutton.text = resourceHelper.gs(R.string.reuse_profile_pct_hours, ps.percentage, ps.timeshift)
                binding.reusebutton.setOnClickListener {
                    binding.percentage.value = ps.percentage.toDouble()
                    binding.timeshift.value = ps.timeshift.toDouble()
                }
            } else {
                binding.reuselayout.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val profileStore = activePlugin.activeProfileInterface.profile
            ?: return false

        val actions: LinkedList<String> = LinkedList()
        val duration = binding.duration.value?.toInt() ?: return false
        if (duration > 0)
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, duration))
        val profile = binding.profile.selectedItem.toString()
        actions.add(resourceHelper.gs(R.string.profile) + ": " + profile)
        val percent = binding.percentage.value.toInt()
        if (percent != 100)
            actions.add(resourceHelper.gs(R.string.percent) + ": " + percent + "%")
        val timeShift = binding.timeshift.value.toInt()
        if (timeShift != 0)
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_timeshift_label) + ": " + resourceHelper.gs(R.string.format_hours, timeShift.toDouble()))
        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                aapsLogger.debug("USER ENTRY: PROFILE SWITCH $profile percent: $percent timeshift: $timeShift duration: $duration")
                treatmentsPlugin.doProfileSwitch(profileStore, profile, duration, percent, timeShift, eventTime)
            })
        }
        return true
    }
}
