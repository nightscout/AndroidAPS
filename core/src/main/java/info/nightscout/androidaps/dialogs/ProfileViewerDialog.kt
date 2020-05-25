package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.close.*
import kotlinx.android.synthetic.main.dialog_profileviewer.*
import org.json.JSONObject
import javax.inject.Inject

class ProfileViewerDialog : DaggerDialogFragment() {
    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var dateUtil: DateUtil

    private var time: Long = 0

    enum class Mode(val i: Int) {
        RUNNING_PROFILE(1),
        CUSTOM_PROFILE(2)
    }

    private var mode: Mode = Mode.RUNNING_PROFILE
    private var customProfileJson: String = ""
    private var customProfileName: String = ""
    private var customProfileUnits: String = Constants.MGDL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            time = bundle.getLong("time", 0)
            mode = Mode.values()[bundle.getInt("mode", Mode.RUNNING_PROFILE.ordinal)]
            customProfileJson = bundle.getString("customProfile", "")
            customProfileUnits = bundle.getString("customProfileUnits", Constants.MGDL)
            customProfileName = bundle.getString("customProfileName", "")
        }

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        return inflater.inflate(R.layout.dialog_profileviewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        close.setOnClickListener { dismiss() }

        val profile: Profile?
        val profileName: String?
        val date: String?
        when (mode) {
            Mode.RUNNING_PROFILE -> {
                profile = activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.profileObject
                profileName = activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.customizedName
                date = dateUtil.dateAndTimeString(activePlugin.activeTreatments.getProfileSwitchFromHistory(time)?.date
                    ?: 0)
                profileview_datelayout.visibility = View.VISIBLE
            }

            Mode.CUSTOM_PROFILE  -> {
                profile = Profile(injector, JSONObject(customProfileJson), customProfileUnits)
                profileName = customProfileName
                date = ""
                profileview_datelayout.visibility = View.GONE
            }
        }
        profileview_noprofile.visibility = View.VISIBLE

        profile?.let {
            profileview_units.text = it.units
            profileview_dia.text = resourceHelper.gs(R.string.format_hours, it.dia)
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putLong("time", time)
        bundle.putInt("mode", mode.ordinal)
        bundle.putString("customProfile", customProfileJson)
        bundle.putString("customProfileName", customProfileName)
        bundle.putString("customProfileUnits", customProfileUnits)
    }

}
