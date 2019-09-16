package info.nightscout.androidaps.plugins.profile.local


import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.careportal.CareportalFragment
import info.nightscout.androidaps.plugins.general.careportal.Dialogs.NewNSTreatmentDialog
import info.nightscout.androidaps.plugins.insulin.InsulinOrefBasePlugin.MIN_DIA
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.localprofile_fragment.*
import java.text.DecimalFormat

class LocalProfileFragment : Fragment() {
    private var disposable: CompositeDisposable = CompositeDisposable()

    private var basalView: TimeListEdit? = null
    private var spinner: SpinnerHelper? = null

    private val save = Runnable {
        doEdit()
        basalView?.updateLabel(MainApp.gs(R.string.nsprofileview_basal_label) + ": " + sumLabel())
    }

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            LocalProfilePlugin.currentProfile().dia = SafeParse.stringToDouble(localprofile_dia.text.toString())
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = LocalProfilePlugin.createProfileStore().defaultProfile
        val sum = profile?.baseBasalSum() ?: 0.0
        return " ∑" + DecimalFormatter.to2Decimal(sum) + MainApp.gs(R.string.insulin_unit_shortname)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.localprofile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        build()
    }

    fun build() {
        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription ?: return
        val units = ProfileFunctions.getSystemUnits()

        localprofile_name.setText(LocalProfilePlugin.currentProfile().name)
        localprofile_dia.setParams(LocalProfilePlugin.currentProfile().dia, HardLimits.MINDIA, HardLimits.MAXDIA, 0.1, DecimalFormat("0.0"), false, localprofile_save, textWatch)
        TimeListEdit(context, view, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.currentProfile().ic, null, HardLimits.MINIC, HardLimits.MAXIC, 0.1, DecimalFormat("0.0"), save)
        basalView = TimeListEdit(context, view, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + sumLabel(), LocalProfilePlugin.currentProfile().basal, null, pumpDescription.basalMinimumRate, 10.0, 0.01, DecimalFormat("0.00"), save)
        if (units == Constants.MGDL) {
            TimeListEdit(context, view, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.currentProfile().isf, null, HardLimits.MINISF, HardLimits.MAXISF, 1.0, DecimalFormat("0"), save)
            TimeListEdit(context, view, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), 1.0, DecimalFormat("0"), save)
        } else {
            TimeListEdit(context, view, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.currentProfile().isf, null, Profile.fromMgdlToUnits(HardLimits.MINISF, Constants.MMOL), Profile.fromMgdlToUnits(HardLimits.MAXISF, Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, view, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), Constants.MMOL), Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
        }

        // Spinner
        spinner = SpinnerHelper(view?.findViewById(R.id.localprofile_spinner))
        val profileList: ArrayList<CharSequence> = LocalProfilePlugin.profile?.profileList
                ?: ArrayList()
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            spinner?.adapter = adapter
            spinner?.setSelection(LocalProfilePlugin.currentProfileIndex)
        } ?: return
        spinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (LocalProfilePlugin.isEdited) {
                    context?.let { context ->
                        OKDialog.show(context, MainApp.gs(R.string.confirmation), MainApp.gs(R.string.doyouwantswitchprofile)) {
                            LocalProfilePlugin.currentProfileIndex = position
                            build()
                        }
                    }
                } else {
                    LocalProfilePlugin.currentProfileIndex = position
                    build()
                }
            }
        })

        localprofile_profile_add.setOnClickListener {
            LocalProfilePlugin.addNewProfile()
            build()
        }

        localprofile_profile_remove.setOnClickListener {
            LocalProfilePlugin.removeCurrentProfile()
            build()
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) localprofile_basal.visibility = View.GONE

        localprofile_mgdl.isChecked = LocalProfilePlugin.currentProfile().mgdl
        localprofile_mmol.isChecked = !LocalProfilePlugin.currentProfile().mgdl
        localprofile_mgdl.isEnabled = false
        localprofile_mmol.isEnabled = false

        localprofile_profileswitch.setOnClickListener {
            val newDialog = NewNSTreatmentDialog()
            val profileSwitch = CareportalFragment.PROFILESWITCHDIRECT
            profileSwitch.executeProfileSwitch = true
            newDialog.setOptions(profileSwitch, R.string.careportal_profileswitch)
            fragmentManager?.let { newDialog.show(it, "NewNSTreatmentDialog") }
        }

        localprofile_reset.setOnClickListener {
            LocalProfilePlugin.loadSettings()
            localprofile_mgdl.isChecked = LocalProfilePlugin.currentProfile().mgdl
            localprofile_mmol.isChecked = !LocalProfilePlugin.currentProfile().mgdl
            localprofile_dia.setParams(LocalProfilePlugin.currentProfile().dia, MIN_DIA, 12.0, 0.1, DecimalFormat("0.0"), false, localprofile_save, textWatch)
            TimeListEdit(context, view, R.id.localprofile_ic, MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.currentProfile().ic, null, 0.5, 50.0, 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, view, R.id.localprofile_isf, MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.currentProfile().isf, null, 0.5, 500.0, 0.1, DecimalFormat("0.0"), save)
            basalView = TimeListEdit(context, view, R.id.localprofile_basal, MainApp.gs(R.string.nsprofileview_basal_label) + ": " + sumLabel(), LocalProfilePlugin.currentProfile().basal, null, pumpDescription.basalMinimumRate, 10.0, 0.01, DecimalFormat("0.00"), save)
            TimeListEdit(context, view, R.id.localprofile_target, MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, 3.0, 200.0, 0.1, DecimalFormat("0.0"), save)
            updateGUI()
        }

        localprofile_save.setOnClickListener {
            if (!LocalProfilePlugin.isValidEditState()) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            LocalProfilePlugin.storeSettings()
            updateGUI()
        }
        updateGUI()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventInitializationChanged::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGUI() }, { FabricPrivacy.logException(it) })
        )
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun doEdit() {
        LocalProfilePlugin.isEdited = true
        updateGUI()
    }

    fun updateGUI() {
        if (invalidprofile == null) return
        val isValid = LocalProfilePlugin.isValidEditState()
        val isEdited = LocalProfilePlugin.isEdited
        if (isValid) {
            invalidprofile.visibility = View.GONE //show invalid profile

            if (isEdited) {
                //edited profile -> save first
                localprofile_profileswitch.visibility = View.GONE
                localprofile_save.visibility = View.VISIBLE
            } else {
                localprofile_profileswitch.visibility = View.VISIBLE
                localprofile_save.visibility = View.GONE
            }
        } else {
            invalidprofile.visibility = View.VISIBLE
            localprofile_profileswitch.visibility = View.GONE
            localprofile_save.visibility = View.GONE //don't save an invalid profile
        }

        //Show reset button if data was edited
        if (isEdited) {
            localprofile_reset.visibility = View.VISIBLE
        } else {
            localprofile_reset.visibility = View.GONE
        }
    }
}
