package info.nightscout.androidaps.plugins.treatments.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import kotlinx.android.synthetic.main.close.*
import kotlinx.android.synthetic.main.profileviewer_fragment.*

/**
 * Created by adrian on 17/08/17.
 */

class ProfileViewerDialog : DialogFragment() {
    private var time: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            time = bundle.getLong("time", 0)
        }

        return inflater.inflate(R.layout.profileviewer_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }

        profileview_reload.visibility = View.GONE
        profileview_datedelimiter.visibility = View.VISIBLE
        profileview_datelayout.visibility = View.VISIBLE
        profileview_noprofile.visibility = View.VISIBLE

        val profileSwitch = TreatmentsPlugin.getPlugin().getProfileSwitchFromHistory(time)
        profileSwitch?.profileObject?.let {
            profileview_units.text = it.units
            profileview_dia.text = MainApp.gs(R.string.format_hours, it.dia)
            profileview_activeprofile.text = profileSwitch.customizedName
            profileview_date.text = DateUtil.dateAndTimeString(profileSwitch.date)
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
        dialog.window?.let {
            val params = it.attributes
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.attributes = params
        }
        super.onResume()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putLong("time", time)
    }

}
