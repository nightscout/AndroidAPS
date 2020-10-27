package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.fragment.app.FragmentManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.BgReading
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.BolusWizard
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_wizard.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class WizardDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    private var wizard: BolusWizard? = null

    //one shot guards
    private var okClicked: Boolean = false

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            calculateInsulin()
        }
    }

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("treatments_wizard_bg_input", treatments_wizard_bg_input.value)
        savedInstanceState.putDouble("treatments_wizard_carbs_input", treatments_wizard_carbs_input.value)
        savedInstanceState.putDouble("treatments_wizard_correction_input", treatments_wizard_correction_input.value)
        savedInstanceState.putDouble("treatments_wizard_carb_time_input", treatments_wizard_carb_time_input.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        return inflater.inflate(R.layout.dialog_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCheckedStates()
        processCobCheckBox()
        treatments_wizard_sbcheckbox.visibility = sp.getBoolean(R.string.key_usesuperbolus, false).toVisibility()
        treatments_wizard_notes_layout.visibility = sp.getBoolean(R.string.key_show_notes_entry_dialogs, false).toVisibility()

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val maxCorrection = constraintChecker.getMaxBolusAllowed().value()

        if (profileFunction.getUnits() == Constants.MGDL)
            treatments_wizard_bg_input.setParams(savedInstanceState?.getDouble("treatments_wizard_bg_input")
                ?: 0.0, 0.0, 500.0, 1.0, DecimalFormat("0"), false, ok, textWatcher)
        else
            treatments_wizard_bg_input.setParams(savedInstanceState?.getDouble("treatments_wizard_bg_input")
                ?: 0.0, 0.0, 30.0, 0.1, DecimalFormat("0.0"), false, ok, textWatcher)
        treatments_wizard_carbs_input.setParams(savedInstanceState?.getDouble("treatments_wizard_carbs_input")
            ?: 0.0, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false, ok, textWatcher)
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        treatments_wizard_correction_input.setParams(savedInstanceState?.getDouble("treatments_wizard_correction_input")
            ?: 0.0, -maxCorrection, maxCorrection, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, ok, textWatcher)
        treatments_wizard_carb_time_input.setParams(savedInstanceState?.getDouble("treatments_wizard_carb_time_input")
            ?: 0.0, -60.0, 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)
        initDialog()

        treatments_wizard_percent_used.text = resourceHelper.gs(R.string.format_percent, sp.getInt(R.string.key_boluswizard_percentage, 100))
        // ok button
        ok.setOnClickListener {
            if (okClicked) {
                aapsLogger.debug(LTag.UI, "guarding: ok already clicked")
            } else {
                okClicked = true
                calculateInsulin()
                context?.let { context ->
                    wizard?.confirmAndExecute(context)
                }
            }
            dismiss()
        }
        // cancel button
        cancel.setOnClickListener { dismiss() }
        // checkboxes
        treatments_wizard_bgcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_ttcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_cobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_basaliobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_bolusiobcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_bgtrendcheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        treatments_wizard_sbcheckbox.setOnCheckedChangeListener(::onCheckedChanged)

        val showCalc = sp.getBoolean(resourceHelper.gs(R.string.key_wizard_calculation_visible), false)
        treatments_wizard_delimiter.visibility = showCalc.toVisibility()
        treatments_wizard_resulttable.visibility = showCalc.toVisibility()
        treatments_wizard_calculationcheckbox.isChecked = showCalc
        treatments_wizard_calculationcheckbox.setOnCheckedChangeListener { _, isChecked ->
            run {
                sp.putBoolean(resourceHelper.gs(R.string.key_wizard_calculation_visible), isChecked)
                treatments_wizard_delimiter.visibility = isChecked.toVisibility()
                treatments_wizard_resulttable.visibility = isChecked.toVisibility()
            }
        }
        // profile spinner
        treatments_wizard_profile.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.noprofileselected))
                ok.visibility = View.GONE
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateInsulin()
                ok.visibility = View.VISIBLE
            }
        }
        // bus
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                activity?.runOnUiThread { calculateInsulin() }
            }, { fabricPrivacy.logException(it) })
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    private fun onCheckedChanged(buttonView: CompoundButton, @Suppress("UNUSED_PARAMETER") state: Boolean) {
        saveCheckedStates()
        treatments_wizard_ttcheckbox.isEnabled = treatments_wizard_bgcheckbox.isChecked && treatmentsPlugin.tempTargetFromHistory != null
        if (buttonView.id == treatments_wizard_cobcheckbox.id)
            processCobCheckBox()
        calculateInsulin()
    }

    private fun processCobCheckBox() {
        if (treatments_wizard_cobcheckbox.isChecked) {
            treatments_wizard_bolusiobcheckbox.isEnabled = false
            treatments_wizard_basaliobcheckbox.isEnabled = false
            treatments_wizard_bolusiobcheckbox.isChecked = true
            treatments_wizard_basaliobcheckbox.isChecked = true
        } else {
            treatments_wizard_bolusiobcheckbox.isEnabled = true
            treatments_wizard_basaliobcheckbox.isEnabled = true
        }
    }

    private fun saveCheckedStates() {
        sp.putBoolean(resourceHelper.gs(R.string.key_wizard_include_cob), treatments_wizard_cobcheckbox.isChecked)
        sp.putBoolean(resourceHelper.gs(R.string.key_wizard_include_trend_bg), treatments_wizard_bgtrendcheckbox.isChecked)
    }

    private fun loadCheckedStates() {
        treatments_wizard_bgtrendcheckbox.isChecked = sp.getBoolean(resourceHelper.gs(R.string.key_wizard_include_trend_bg), false)
        treatments_wizard_cobcheckbox.isChecked = sp.getBoolean(resourceHelper.gs(R.string.key_wizard_include_cob), false)
    }

    private fun initDialog() {
        val profile = profileFunction.getProfile()
        val profileStore = activePlugin.activeProfileInterface.profile

        if (profile == null || profileStore == null) {
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.noprofile))
            dismiss()
            return
        }

        val profileList: ArrayList<CharSequence>
        profileList = profileStore.getProfileList()
        profileList.add(0, resourceHelper.gs(R.string.active))
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            treatments_wizard_profile.adapter = adapter
        } ?: return

        val units = profileFunction.getUnits()
        treatments_wizard_bgunits.text = units
        if (units == Constants.MGDL)
            treatments_wizard_bg_input.setStep(1.0)
        else
            treatments_wizard_bg_input.setStep(0.1)

        // Set BG if not old
        val lastBg = iobCobCalculatorPlugin.actualBg()

        if (lastBg != null) {
            treatments_wizard_bg_input.value = lastBg.valueToUnits(units)
        } else {
            treatments_wizard_bg_input.value = 0.0
        }
        treatments_wizard_ttcheckbox.isEnabled = treatmentsPlugin.tempTargetFromHistory != null

        // IOB calculation
        treatmentsPlugin.updateTotalIOBTreatments()
        val bolusIob = treatmentsPlugin.lastCalculationTreatments.round()
        treatmentsPlugin.updateTotalIOBTempBasals()
        val basalIob = treatmentsPlugin.lastCalculationTempBasals.round()

        treatments_wizard_bolusiobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, -bolusIob.iob)
        treatments_wizard_basaliobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, -basalIob.basaliob)

        calculateInsulin()

        treatments_wizard_percent_used.visibility = (sp.getInt(R.string.key_boluswizard_percentage, 100) != 100).toVisibility()
    }

    private fun calculateInsulin() {
        val profileStore = activePlugin.activeProfileInterface.profile
        if (treatments_wizard_profile?.selectedItem == null || profileStore == null)
            return  // not initialized yet
        var profileName = treatments_wizard_profile.selectedItem.toString()
        val specificProfile: Profile?
        if (profileName == resourceHelper.gs(R.string.active)) {
            specificProfile = profileFunction.getProfile()
            profileName = profileFunction.getProfileName()
        } else
            specificProfile = profileStore.getSpecificProfile(profileName)

        if (specificProfile == null) return

        // Entered values
        var bg = SafeParse.stringToDouble(treatments_wizard_bg_input.text)
        val carbs = SafeParse.stringToInt(treatments_wizard_carbs_input.text)
        val correction = SafeParse.stringToDouble(treatments_wizard_correction_input.text)
        val carbsAfterConstraint = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
        if (abs(carbs - carbsAfterConstraint) > 0.01) {
            treatments_wizard_carbs_input.value = 0.0
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.carbsconstraintapplied))
            return
        }

        bg = if (treatments_wizard_bgcheckbox.isChecked) bg else 0.0
        val tempTarget = if (treatments_wizard_ttcheckbox.isChecked) treatmentsPlugin.tempTargetFromHistory else null

        // COB
        var cob = 0.0
        if (treatments_wizard_cobcheckbox.isChecked) {
            val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Wizard COB")
            cobInfo.displayCob?.let { cob = it }
        }

        val carbTime = SafeParse.stringToInt(treatments_wizard_carb_time_input.text)

        wizard = BolusWizard(mainApp).doCalc(specificProfile, profileName, tempTarget, carbsAfterConstraint, cob, bg, correction,
            sp.getInt(R.string.key_boluswizard_percentage, 100).toDouble(),
            treatments_wizard_bgcheckbox.isChecked,
            treatments_wizard_cobcheckbox.isChecked,
            treatments_wizard_bolusiobcheckbox.isChecked,
            treatments_wizard_basaliobcheckbox.isChecked,
            treatments_wizard_sbcheckbox.isChecked,
            treatments_wizard_ttcheckbox.isChecked,
            treatments_wizard_bgtrendcheckbox.isChecked,
            treatment_wizard_notes.text.toString(), carbTime)

        wizard?.let { wizard ->
            treatments_wizard_bg.text = String.format(resourceHelper.gs(R.string.format_bg_isf), BgReading().value(Profile.toMgdl(bg, profileFunction.getUnits())).valueToUnitsToString(profileFunction.getUnits()), wizard.sens)
            treatments_wizard_bginsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromBG)

            treatments_wizard_carbs.text = String.format(resourceHelper.gs(R.string.format_carbs_ic), carbs.toDouble(), wizard.ic)
            treatments_wizard_carbsinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromCarbs)

            treatments_wizard_bolusiobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromBolusIOB)
            treatments_wizard_basaliobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromBasalsIOB)

            treatments_wizard_correctioninsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromCorrection)

            // Superbolus
            treatments_wizard_sb.text = if (treatments_wizard_sbcheckbox.isChecked) resourceHelper.gs(R.string.twohours) else ""
            treatments_wizard_sbinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromSuperBolus)

            // Trend
            if (treatments_wizard_bgtrendcheckbox.isChecked && wizard.glucoseStatus != null) {
                treatments_wizard_bgtrend.text = ((if (wizard.trend > 0) "+" else "")
                    + Profile.toUnitsString(wizard.trend * 3, wizard.trend * 3 / Constants.MMOLL_TO_MGDL, profileFunction.getUnits())
                    + " " + profileFunction.getUnits())
            } else {
                treatments_wizard_bgtrend.text = ""
            }
            treatments_wizard_bgtrendinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromTrend)

            // COB
            if (treatments_wizard_cobcheckbox.isChecked) {
                treatments_wizard_cob.text = String.format(resourceHelper.gs(R.string.format_cob_ic), cob, wizard.ic)
                treatments_wizard_cobinsulin.text = resourceHelper.gs(R.string.formatinsulinunits, wizard.insulinFromCOB)
            } else {
                treatments_wizard_cob.text = ""
                treatments_wizard_cobinsulin.text = ""
            }

            if (wizard.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0.0) {
                val insulinText = if (wizard.calculatedTotalInsulin > 0.0) resourceHelper.gs(R.string.formatinsulinunits, wizard.calculatedTotalInsulin) else ""
                val carbsText = if (carbsAfterConstraint > 0.0) resourceHelper.gs(R.string.format_carbs, carbsAfterConstraint) else ""
                treatments_wizard_total.text = resourceHelper.gs(R.string.result_insulin_carbs, insulinText, carbsText)
                ok.visibility = View.VISIBLE
            } else {
                treatments_wizard_total.text = resourceHelper.gs(R.string.missing_carbs, wizard.carbsEquivalent.toInt())
                ok.visibility = View.INVISIBLE
            }
        }

    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage)
        }
    }
}
