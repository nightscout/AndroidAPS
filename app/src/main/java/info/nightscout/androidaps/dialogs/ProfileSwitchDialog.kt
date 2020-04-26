package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import kotlinx.android.synthetic.main.dialog_profileswitch.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*

class ProfileSwitchDialog : DialogFragmentWithDate() {

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_profileswitch_duration", overview_profileswitch_duration.value)
        savedInstanceState.putDouble("overview_profileswitch_percentage", overview_profileswitch_percentage.value)
        savedInstanceState.putDouble("overview_profileswitch_timeshift", overview_profileswitch_timeshift.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_profileswitch, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_profileswitch_duration.setParams(savedInstanceState?.getDouble("overview_profileswitch_duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, ok)
        overview_profileswitch_percentage.setParams(savedInstanceState?.getDouble("overview_profileswitch_percentage")
            ?: 100.0, Constants.CPP_MIN_PERCENTAGE.toDouble(), Constants.CPP_MAX_PERCENTAGE.toDouble(), 1.0, DecimalFormat("0"), false, ok)
        overview_profileswitch_timeshift.setParams(savedInstanceState?.getDouble("overview_profileswitch_timeshift")
            ?: 0.0, Constants.CPP_MIN_TIMESHIFT.toDouble(), Constants.CPP_MAX_TIMESHIFT.toDouble(), 1.0, DecimalFormat("0"), false, ok)

        // profile
        context?.let { context ->
            val profileStore = ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile
                ?: return
            val profileList = profileStore.getProfileList()
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            overview_profileswitch_profile.adapter = adapter
            // set selected to actual profile
            for (p in profileList.indices)
                if (profileList[p] == ProfileFunctions.getInstance().getProfileName(false))
                    overview_profileswitch_profile.setSelection(p)
        } ?: return

        TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(DateUtil.now())?.let { ps ->
            if (ps.isCPP) {
                overview_profileswitch_reuselayout.visibility = View.VISIBLE
                overview_profileswitch_reusebutton.text = MainApp.gs(R.string.reuse) + " " + ps.percentage + "% " + ps.timeshift + "h"
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
        val profileStore = ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile
            ?: return false

        val actions: LinkedList<String> = LinkedList()
        val duration = overview_profileswitch_duration.value.toInt()
        if (duration > 0)
            actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_mins, duration))
        val profile = overview_profileswitch_profile.selectedItem.toString()
        actions.add(MainApp.gs(R.string.profile) + ": " + profile)
        val percent = overview_profileswitch_percentage.value.toInt()
        if (percent != 100)
            actions.add(MainApp.gs(R.string.percent) + ": " + percent + "%")
        val timeShift = overview_profileswitch_timeshift.value.toInt()
        if (timeShift != 0)
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_timeshift_label) + ": " + MainApp.gs(R.string.format_hours, timeShift.toDouble()))
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, MainApp.gs(R.string.careportal_profileswitch), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                log.debug("USER ENTRY: PROFILE SWITCH $profile percent: $percent timeshift: $timeShift duration: $duration")
                ProfileFunctions.doProfileSwitch(profileStore, profile, duration, percent, timeShift, eventTime)
            })
        }
        return true
    }
}
