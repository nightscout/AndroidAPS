package info.nightscout.androidaps.plugins.profile.local

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.insulin.InsulinOrefBasePlugin.Companion.MIN_DIA
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.localprofile_fragment.*
import java.text.DecimalFormat
import javax.inject.Inject

class LocalProfileFragment : DaggerFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var hardLimits: HardLimits
    @Inject lateinit var dateUtil: DateUtil

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var basalView: TimeListEdit? = null
    private var spinner: SpinnerHelper? = null

    private val save = Runnable {
        doEdit()
        basalView?.updateLabel(resourceHelper.gs(R.string.basal_label) + ": " + sumLabel())
    }

    private val textWatch = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            localProfilePlugin.currentProfile()?.dia = SafeParse.stringToDouble(localprofile_dia.text.toString())
            localProfilePlugin.currentProfile()?.name = localprofile_name.text.toString()
            doEdit()
        }
    }

    private fun sumLabel(): String {
        val profile = localProfilePlugin.createProfileStore().getDefaultProfile()
        val sum = profile?.baseBasalSum() ?: 0.0
        return " ∑" + DecimalFormatter.to2Decimal(sum) + resourceHelper.gs(R.string.insulin_unit_shortname)
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
        val pumpDescription = activePlugin.activePump.pumpDescription
        if (localProfilePlugin.numOfProfiles == 0) localProfilePlugin.addNewProfile()
        val currentProfile = localProfilePlugin.currentProfile() ?: return
        val units = if (currentProfile.mgdl) Constants.MGDL else Constants.MMOL

        localprofile_name.removeTextChangedListener(textWatch)
        localprofile_name.setText(currentProfile.name)
        localprofile_name.addTextChangedListener(textWatch)
        localprofile_dia.setParams(currentProfile.dia, hardLimits.minDia(), hardLimits.maxDia(), 0.1, DecimalFormat("0.0"), false, localprofile_save, textWatch)
        localprofile_dia.tag = "LP_DIA"
        TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_ic, "IC", resourceHelper.gs(R.string.ic_label), currentProfile.ic, null, hardLimits.minIC(), hardLimits.maxIC(), 0.1, DecimalFormat("0.0"), save)
        basalView = TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_basal, "BASAL", resourceHelper.gs(R.string.basal_label) + ": " + sumLabel(), currentProfile.basal, null, pumpDescription.basalMinimumRate, 10.0, 0.01, DecimalFormat("0.00"), save)
        if (units == Constants.MGDL) {
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_isf, "ISF", resourceHelper.gs(R.string.isf_label), currentProfile.isf, null, hardLimits.MINISF, hardLimits.MAXISF, 1.0, DecimalFormat("0"), save)
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_target, "TARGET", resourceHelper.gs(R.string.target_label), currentProfile.targetLow, currentProfile.targetHigh, hardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), hardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), 1.0, DecimalFormat("0"), save)
        } else {
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_isf, "ISF", resourceHelper.gs(R.string.isf_label), currentProfile.isf, null, Profile.fromMgdlToUnits(hardLimits.MINISF, Constants.MMOL), Profile.fromMgdlToUnits(hardLimits.MAXISF, Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
            TimeListEdit(context, aapsLogger, dateUtil, view, R.id.localprofile_target, "TARGET", resourceHelper.gs(R.string.target_label), currentProfile.targetLow, currentProfile.targetHigh, Profile.fromMgdlToUnits(hardLimits.VERY_HARD_LIMIT_TARGET_BG[0].toDouble(), Constants.MMOL), Profile.fromMgdlToUnits(hardLimits.VERY_HARD_LIMIT_TARGET_BG[1].toDouble(), Constants.MMOL), 0.1, DecimalFormat("0.0"), save)
        }

        // Spinner
        spinner = SpinnerHelper(view?.findViewById(R.id.localprofile_spinner))
        val profileList: ArrayList<CharSequence> = localProfilePlugin.profile?.getProfileList()
            ?: ArrayList()
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            spinner?.adapter = adapter
            spinner?.setSelection(localProfilePlugin.currentProfileIndex)
        } ?: return
        spinner?.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (localProfilePlugin.isEdited) {
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.doyouwantswitchprofile), Runnable {
                            localProfilePlugin.currentProfileIndex = position
                            build()
                        }, Runnable {
                            spinner?.setSelection(localProfilePlugin.currentProfileIndex)
                        })
                    }
                } else {
                    localProfilePlugin.currentProfileIndex = position
                    build()
                }
            }
        })

        localprofile_profile_add.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", resourceHelper.gs(R.string.saveorresetchangesfirst)) }
            } else {
                localProfilePlugin.addNewProfile()
                build()
            }
        }

        localprofile_profile_clone.setOnClickListener {
            if (localProfilePlugin.isEdited) {
                activity?.let { OKDialog.show(it, "", resourceHelper.gs(R.string.saveorresetchangesfirst)) }
            } else {
                localProfilePlugin.cloneProfile()
                build()
            }
        }

        localprofile_profile_remove.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.deletecurrentprofile), Runnable {
                    localProfilePlugin.removeCurrentProfile()
                    build()
                }, null)
            }
        }

        // this is probably not possible because it leads to invalid profile
        // if (!pumpDescription.isTempBasalCapable) localprofile_basal.visibility = View.GONE

        @Suppress("SetTextI18n")
        localprofile_units.text = resourceHelper.gs(R.string.units_colon) + " " + (if (currentProfile.mgdl) resourceHelper.gs(R.string.mgdl) else resourceHelper.gs(R.string.mmol))

        localprofile_profileswitch.setOnClickListener {
            ProfileSwitchDialog()
                .also { it.arguments = Bundle().also { bundle -> bundle.putInt("profileIndex", localProfilePlugin.currentProfileIndex) } }
                .show(childFragmentManager, "NewNSTreatmentDialog")
        }

        localprofile_reset.setOnClickListener {
            localProfilePlugin.loadSettings()
            build()
        }

        localprofile_save.setOnClickListener {
            if (!localProfilePlugin.isValidEditState()) {
                return@setOnClickListener  //Should not happen as saveButton should not be visible if not valid
            }
            localProfilePlugin.storeSettings(activity)
            build()
        }
        updateGUI()
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventLocalProfileChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ build() }, { fabricPrivacy.logException(it) }
            )
        build()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    fun doEdit() {
        localProfilePlugin.isEdited = true
        updateGUI()
    }

    fun updateGUI() {
        if (localprofile_profileswitch == null) return
        val isValid = localProfilePlugin.isValidEditState()
        val isEdited = localProfilePlugin.isEdited
        if (isValid) {
            this.view?.setBackgroundColor(resourceHelper.gc(R.color.ok_background))

            if (isEdited) {
                //edited profile -> save first
                localprofile_profileswitch.visibility = View.GONE
                localprofile_save.visibility = View.VISIBLE
            } else {
                localprofile_profileswitch.visibility = View.VISIBLE
                localprofile_save.visibility = View.GONE
            }
        } else {
            this.view?.setBackgroundColor(resourceHelper.gc(R.color.error_background))
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
        dia_tab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        ic_tab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        isf_tab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        basal_tab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        target_tab.setBackgroundColor(resourceHelper.gc(R.color.defaultbackground))
        selected.setBackgroundColor(resourceHelper.gc(R.color.tabBgColorSelected))
        localprofile_dia_placeholder.visibility = View.GONE
        localprofile_ic.visibility = View.GONE
        localprofile_isf.visibility = View.GONE
        localprofile_basal.visibility = View.GONE
        localprofile_target.visibility = View.GONE
    }
}
