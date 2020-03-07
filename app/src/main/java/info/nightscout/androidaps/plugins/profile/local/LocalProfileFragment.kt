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
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.insulin.InsulinOrefBasePlugin.MIN_DIA
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
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
            LocalProfilePlugin.currentProfile().name = localprofile_name.text.toString()
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = LocalProfilePlugin.createProfileStore().getDefaultProfile()
        val sum = profile?.baseBasalSum() ?: 0.0
        return " ∑" + DecimalFormatter.to2Decimal(sum) + MainApp.gs(R.string.insulin_unit_shortname)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.localprofile_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // activate DIA tab
        processVisibilityOnClick(dia_tab)
        localprofile_dia_placeholder.visibility = View.VISIBLE
        // setup listeners
        dia_tab.setOnClickListener {
            processVisibilityOnClick(it)
            localprofile_dia_placeholder.visibility = View.VISIBLE
        }
        ic_tab.setOnClickListener {
            processVisibilityOnClick(it)
            localprofile_ic.visibility = View.VISIBLE
        }
        isf_tab.setOnClickListener {
            processVisibilityOnClick(it)
            localprofile_isf.visibility = View.VISIBLE
        }
        basal_tab.setOnClickListener {
            processVisibilityOnClick(it)
            localprofile_basal.visibility = View.VISIBLE
        }
        target_tab.setOnClickListener {
            processVisibilityOnClick(it)
            localprofile_target.visibility = View.VISIBLE
        }
    }

    fun build() {
        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription ?: return
        val units = if (LocalProfilePlugin.currentProfile().mgdl) Constants.MGDL else Constants.MMOL

        localprofile_name.removeTextChangedListener(textWatch)
        localprofile_name.setText(LocalProfilePlugin.currentProfile().name)
        localprofile_name.addTextChangedListener(textWatch)
        localprofile_dia.setParams(LocalProfilePlugin.currentProfile().dia, HardLimits.MINDIA, HardLimits.MAXDIA, 0.1, DecimalFormat("0.0"), false, localprofile_save, textWatch)
        localprofile_dia.setTag("LP_DIA")
        TimeListEdit(context, view, R.id.localprofile_ic, "IC", MainApp.gs(R.string.nsprofileview_ic_label), LocalProfilePlugin.currentProfile().ic, null, HardLimits.MINIC, HardLimits.MAXIC, 0.1, DecimalFormat("0.0"), save)
        basalView = TimeListEdit(context, view, R.id.localprofile_basal, "BASAL", MainApp.gs(R.string.nsprofileview_basal_label) + ": " + sumLabel(), LocalProfilePlugin.currentProfile().basal, null, pumpDescription.basalMinimumRate, 10.0, 0.01, DecimalFormat("0.00"), save)
        if (units == Constants.MGDL) {
            TimeListEdit(context, view, R.id.localprofile_isf, "ISF", MainApp.gs(R.string.nsprofileview_isf_label), LocalProfilePlugin.currentProfile().isf, null, HardLimits.MINISF, HardLimits.MAXISF, 1.0, DecimalFormat("0"), save)
            TimeListEdit(context, view, R.id.localprofile_target, "TARGET", MainApp.gs(R.string.nsprofileview_target_label), LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), 1.0, DecimalFormat("0"), save)
        } else {
            TimeListEdit(context, view, R.id.localprofile_isf, "ISF", MainApp.gs(R.string.nsprofileview_isf_label), LocalProfilePlugin.currentProfile().isf, null, Profile.fromMgdlToUnits(HardLimits.MINISF, Constants.MMOL), Profile.fromMgdlToUnits(HardLimits.MAXISF, Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, view, R.id.localprofile_target, "TARGET", MainApp.gs(R.string.nsprofileview_target_label), LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), Constants.MMOL), Profile.fromMgdlToUnits(HardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
        }

        // Spinner
        spinner = SpinnerHelper(view?.findViewById(R.id.localprofile_spinner))
        val profileList: ArrayList<CharSequence> = LocalProfilePlugin.profile?.getProfileList()
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
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, MainApp.gs(R.string.doyouwantswitchprofile), Runnable {
                            LocalProfilePlugin.currentProfileIndex = position
                            build()
                        }, Runnable {
                            spinner?.setSelection(LocalProfilePlugin.currentProfileIndex)
                        })
                    }
                } else {
                    LocalProfilePlugin.currentProfileIndex = position
                    build()
                }
            }
        })

        localprofile_profile_add.setOnClickListener {
            if (LocalProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", MainApp.gs(R.string.saveorresetchangesfirst)) }
            } else {
                LocalProfilePlugin.addNewProfile()
                build()
            }
        }

        localprofile_profile_clone.setOnClickListener {
            if (LocalProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", MainApp.gs(R.string.saveorresetchangesfirst)) }
            } else {
                LocalProfilePlugin.cloneProfile()
                build()
            }
        }

        localprofile_profile_remove.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, MainApp.gs(R.string.deletecurrentprofile), Runnable {
                    LocalProfilePlugin.removeCurrentProfile()
                    build()
                }, null)
            }
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) localprofile_basal.visibility = View.GONE

        @Suppress("SETTEXTL18N")
        localprofile_units.text = MainApp.gs(R.string.units_colon) + " " + (if (LocalProfilePlugin.currentProfile().mgdl) MainApp.gs(R.string.mgdl) else MainApp.gs(R.string.mmol))

        localprofile_profileswitch.setOnClickListener {
            // TODO: select in dialog LocalProfilePlugin.currentProfileIndex
            fragmentManager?.let { ProfileSwitchDialog().show(it, "NewNSTreatmentDialog") }
        }

        localprofile_reset.setOnClickListener {
            LocalProfilePlugin.loadSettings()
            @Suppress("SETTEXTL18N")
            localprofile_units.text = MainApp.gs(R.string.units_colon) + " " + (if (LocalProfilePlugin.currentProfile().mgdl) MainApp.gs(R.string.mgdl) else MainApp.gs(R.string.mmol))
            localprofile_dia.setParams(LocalProfilePlugin.currentProfile().dia, MIN_DIA, 12.0, 0.1, DecimalFormat("0.0"), false, localprofile_save, textWatch)
            localprofile_dia.setTag("LP_DIA")
            TimeListEdit(context, view, R.id.localprofile_ic, "IC", MainApp.gs(R.string.nsprofileview_ic_label) + ":", LocalProfilePlugin.currentProfile().ic, null, 0.5, 50.0, 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, view, R.id.localprofile_isf, "ISF", MainApp.gs(R.string.nsprofileview_isf_label) + ":", LocalProfilePlugin.currentProfile().isf, null, 0.5, 500.0, 0.1, DecimalFormat("0.0"), save)
            basalView = TimeListEdit(context, view, R.id.localprofile_basal, "BASAL", MainApp.gs(R.string.nsprofileview_basal_label) + ": " + sumLabel(), LocalProfilePlugin.currentProfile().basal, null, pumpDescription.basalMinimumRate, 10.0, 0.01, DecimalFormat("0.00"), save)
            TimeListEdit(context, view, R.id.localprofile_target, "TARGET", MainApp.gs(R.string.nsprofileview_target_label) + ":", LocalProfilePlugin.currentProfile().targetLow, LocalProfilePlugin.currentProfile().targetHigh, 3.0, 200.0, 0.1, DecimalFormat("0.0"), save)
            updateGUI()
        }

        localprofile_save.setOnClickListener {
            if (!LocalProfilePlugin.isValidEditState()) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            LocalProfilePlugin.storeSettings(activity)
            build()
        }
        updateGUI()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
            .toObservable(EventLocalProfileChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ build() }, { FabricPrivacy.logException(it) })
        )
        build()
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
        if (localprofile_profileswitch == null) return
        val isValid = LocalProfilePlugin.isValidEditState()
        val isEdited = LocalProfilePlugin.isEdited
        if (isValid) {
            this.view?.setBackgroundColor(MainApp.gc(R.color.ok_background))

            if (isEdited) {
                //edited profile -> save first
                localprofile_profileswitch.visibility = View.GONE
                localprofile_save.visibility = View.VISIBLE
            } else {
                localprofile_profileswitch.visibility = View.VISIBLE
                localprofile_save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(MainApp.gc(R.color.error_background))
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

    private fun processVisibilityOnClick(selected: View) {
        dia_tab.setBackgroundColor(MainApp.gc(R.color.defaultbackground))
        ic_tab.setBackgroundColor(MainApp.gc(R.color.defaultbackground))
        isf_tab.setBackgroundColor(MainApp.gc(R.color.defaultbackground))
        basal_tab.setBackgroundColor(MainApp.gc(R.color.defaultbackground))
        target_tab.setBackgroundColor(MainApp.gc(R.color.defaultbackground))
        selected.setBackgroundColor(MainApp.gc(R.color.tabBgColorSelected))
        localprofile_dia_placeholder.visibility = View.GONE
        localprofile_ic.visibility = View.GONE
        localprofile_isf.visibility = View.GONE
        localprofile_basal.visibility = View.GONE
        localprofile_target.visibility = View.GONE
    }
}
