package info.nightscout.androidaps.plugins.treatments.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileInterface
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import kotlinx.android.synthetic.main.close.*
import kotlinx.android.synthetic.main.profileviewer_fragment.*

class ProfileViewerDialog : DialogFragment() {
    private var time: Long = 0

    enum class Mode(val i: Int) {
        RUNNING_PROFILE(1),
        PUMP_PROFILE(2)
    }

    private var mode: Mode = Mode.RUNNING_PROFILE;

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            time = bundle.getLong("time", 0)
            mode = Mode.values()[bundle.getInt("mode", Mode.RUNNING_PROFILE.ordinal)]
        }

        return inflater.inflate(R.layout.profileviewer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }
        profileview_reload.setOnClickListener {
            ConfigBuilderPlugin.getPlugin().commandQueue.readStatus("ProfileViewDialog", null)
            dismiss()
        }

        val profile: Profile?
        val profileName: String?
        val date: String?
        when (mode) {
            Mode.RUNNING_PROFILE -> {
                profile = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(time)?.profileObject
                profileName = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(time)?.customizedName
                date = DateUtil.dateAndTimeString(TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(time)?.date
                        ?: 0)
                profileview_reload.visibility = View.GONE
                profileview_datelayout.visibility = View.VISIBLE
            }
            Mode.PUMP_PROFILE -> {
                profile = (ConfigBuilderPlugin.getPlugin().activePump as ProfileInterface?)?.profile?.defaultProfile
                profileName = (ConfigBuilderPlugin.getPlugin().activePump as ProfileInterface?)?.profileName
                date = ""
                profileview_reload.visibility = View.VISIBLE
                profileview_datelayout.visibility = View.GONE
            }
        }
        profileview_noprofile.visibility = View.VISIBLE

        profile?.let {
            profileview_units.text = it.units
            profileview_dia.text = MainApp.gs(R.string.format_hours, it.dia)
            profileview_activeprofile.text = profileName
            profileview_date.text = date
            profileview_ic.text = it.icList
            profileview_isf.text = it.isfList
            profileview_basal.text = it.basalList
            profileview_target.text = it.targetList
            basal_graph.show(it)

            profileview_noprofile.visibility = View.GONE
            profileview_invalidprofile.visibility = if (it.isValid("ProfileViewDialog")) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        super.onResume()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putLong("time", time)
        bundle.putInt("mode", mode.ordinal)
    }

}
