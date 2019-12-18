package info.nightscout.androidaps.plugins.general.overview.dialogs

import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import kotlinx.android.synthetic.main.okcancel.*
import kotlinx.android.synthetic.main.overview_profileswitch_dialog.*
import org.slf4j.LoggerFactory
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
        onCreateView()
        return inflater.inflate(R.layout.overview_profileswitch_dialog, container, false)
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
    }

    override fun submit() {
        val profileStore = ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile ?: return

        val actions: LinkedList<String> = LinkedList()
        val duration = overview_profileswitch_duration.value
        if (duration > 0)
            actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_hours, duration))
        val profile = overview_profileswitch_profile.selectedItem.toString()
        actions.add(MainApp.gs(R.string.profile).toString() + ": " + profile)
        val percent = overview_profileswitch_percentage.value.toInt()
        if (percent != 100)
            actions.add(MainApp.gs(R.string.percent) + ": " + percent + "%")
        val timeShift = overview_profileswitch_timeshift.value.toInt()
        if (timeShift != 0)
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_timeshift_label) + ": " + MainApp.gs(R.string.format_hours, timeShift.toDouble()))
        val notes = overview_profileswitch_notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions))) {
                ProfileFunctions.doProfileSwitch(profileStore, profile, duration.toInt(), percent, timeShift, eventTime)
            }
        }
    }
}
