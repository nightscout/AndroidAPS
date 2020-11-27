package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_profileswitch.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class ProfileSwitchDialog : DialogFragmentWithDate() {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider

    var profileIndex: Int? = null

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_profileswitch_duration", overview_profileswitch_duration.value)
        savedInstanceState.putDouble("overview_profileswitch_percentage", overview_profileswitch_percentage.value)
        savedInstanceState.putDouble("overview_profileswitch_timeshift", overview_profileswitch_timeshift.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        arguments?.let { bundle ->
            profileIndex = bundle.getInt("profileIndex", 0)
        }
        return inflater.inflate(R.layout.dialog_profileswitch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_profileswitch_duration.setParams(savedInstanceState?.getDouble("overview_profileswitch_duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, ok)
        overview_profileswitch_percentage.setParams(savedInstanceState?.getDouble("overview_profileswitch_percentage")
            ?: 100.0, Constants.CPP_MIN_PERCENTAGE.toDouble(), Constants.CPP_MAX_PERCENTAGE.toDouble(), 5.0, DecimalFormat("0"), false, ok)
        overview_profileswitch_timeshift.setParams(savedInstanceState?.getDouble("overview_profileswitch_timeshift")
            ?: 0.0, Constants.CPP_MIN_TIMESHIFT.toDouble(), Constants.CPP_MAX_TIMESHIFT.toDouble(), 1.0, DecimalFormat("0"), false, ok)

        // profile
        context?.let { context ->
            val profileStore = activePlugin.activeProfileInterface.profile
                ?: return
            val profileList = profileStore.getProfileList()
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            overview_profileswitch_profile.adapter = adapter
            // set selected to actual profile
            if (profileIndex != null)
                overview_profileswitch_profile.setSelection(profileIndex as Int)
            else
                for (p in profileList.indices)
                    if (profileList[p] == profileFunction.getProfileName(false))
                        overview_profileswitch_profile.setSelection(p)
        } ?: return

        treatmentsPlugin.getProfileSwitchFromHistory(DateUtil.now())?.let { ps ->
            if (ps.isCPP) {
                overview_profileswitch_reuselayout.visibility = View.VISIBLE
                overview_profileswitch_reusebutton.text = resourceHelper.gs(R.string.reuse) + " " + ps.percentage + "% " + ps.timeshift + "h"
                overview_profileswitch_reusebutton.setOnClickListener {
                    overview_profileswitch_percentage.value = ps.percentage.toDouble()
                    overview_profileswitch_timeshift.value = ps.timeshift.toDouble()
                }
            } else {
                overview_profileswitch_reuselayout.visibility = View.GONE
            }
        }
    }

    override fun submit(): Boolean {
        val profileStore = activePlugin.activeProfileInterface.profile
            ?: return false

        val actions: LinkedList<String> = LinkedList()
        val duration = overview_profileswitch_duration?.value?.toInt() ?: return false
        if (duration > 0)
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, duration))
        val profile = overview_profileswitch_profile.selectedItem.toString()
        actions.add(resourceHelper.gs(R.string.profile) + ": " + profile)
        val percent = overview_profileswitch_percentage.value.toInt()
        if (percent != 100)
            actions.add(resourceHelper.gs(R.string.percent) + ": " + percent + "%")
        val timeShift = overview_profileswitch_timeshift.value.toInt()
        if (timeShift != 0)
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_timeshift_label) + ": " + resourceHelper.gs(R.string.format_hours, timeShift.toDouble()))
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_profileswitch), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                aapsLogger.debug("USER ENTRY: PROFILE SWITCH $profile percent: $percent timeshift: $timeShift duration: $duration")
                treatmentsPlugin.doProfileSwitch(profileStore, profile, duration, percent, timeShift, eventTime)
            })
        }
        return true
    }
}
