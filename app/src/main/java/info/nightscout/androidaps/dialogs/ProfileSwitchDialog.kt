package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.databinding.DialogProfileswitchBinding
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class ProfileSwitchDialog : DialogFragmentWithDate() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private var profileIndex: Int? = null

    private val disposable = CompositeDisposable()

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
            val profileStore = activePlugin.activeProfileSource.profile
                ?: return
            val profileList = profileStore.getProfileList()
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            binding.profile.adapter = adapter
            // set selected to actual profile
            if (profileIndex != null)
                binding.profile.setSelection(profileIndex as Int)
            else
                for (p in profileList.indices)
                    if (profileList[p] == profileFunction.getOriginalProfileName())
                        binding.profile.setSelection(p)
        } ?: return

        profileFunction.getProfile()?.let { profile ->
            if (profile.percentage != 100 || profile.timeshift != 0) {
                binding.reuselayout.visibility = View.VISIBLE
                binding.reusebutton.text = resourceHelper.gs(R.string.reuse_profile_pct_hours, profile.percentage, profile.timeshift)
                binding.reusebutton.setOnClickListener {
                    binding.percentage.value = profile.percentage.toDouble()
                    binding.timeshift.value = profile.timeshift.toDouble()
                }
            } else {
                binding.reuselayout.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val profileStore = activePlugin.activeProfileSource.profile
            ?: return false

        val actions: LinkedList<String> = LinkedList()
        val duration = binding.duration.value?.toInt() ?: return false
        if (duration > 0L)
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, duration))
        val profileName = binding.profile.selectedItem.toString()
        actions.add(resourceHelper.gs(R.string.profile) + ": " + profileName)
        val percent = binding.percentage.value.toInt()
        if (percent != 100)
            actions.add(resourceHelper.gs(R.string.percent) + ": " + percent + "%")
        val timeShift = binding.timeshift.value.toInt()
        if (timeShift != 0)
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_timeshift_label) + ": " + resourceHelper.gs(R.string.format_hours, timeShift.toDouble()))
        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                profileFunction.createProfileSwitch(profileStore,
                    profileName = profileName,
                    durationInMinutes = duration,
                    percentage = percent,
                    timeShiftInHours = timeShift,
                    timestamp = eventTime)
                uel.log(Action.PROFILE_SWITCH,
                    Sources.ProfileSwitchDialog,
                    notes,
                    ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                    ValueWithUnit.SimpleString(profileName),
                    ValueWithUnit.Percent(percent),
                    ValueWithUnit.Hour(timeShift).takeIf { timeShift != 0 },
                    ValueWithUnit.Minute(duration).takeIf { duration != 0 })
            })
        }
        return true
    }
}
