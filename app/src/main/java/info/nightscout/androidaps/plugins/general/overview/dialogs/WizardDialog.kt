package info.nightscout.androidaps.plugins.general.overview.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.AdapterView
import android.widget.AdapterView.*
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.events.EventFeatureRunning
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.okcancel.*
import kotlinx.android.synthetic.main.overview_wizard_dialog.*
import org.slf4j.LoggerFactory
import java.text.DecimalFormat
import java.util.*

class WizardDialog : DialogFragment() {
    private val log = LoggerFactory.getLogger(WizardDialog::class.java)

    private var wizard: BolusWizard? = null
    private var parentContext: Context? = null

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

    override fun onAttach(context: Context) {
        super.onAttach(context)
        this.parentContext = context
    }

    override fun onDetach() {
        super.onDetach()
        this.parentContext = null
    }

    override fun onResume() {
        super.onResume()
        MainApp.bus().post(EventFeatureRunning(EventFeatureRunning.Feature.WIZARD))
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("treatments_wizard_bginput", treatments_wizard_bginput.value)
        savedInstanceState.putDouble("treatments_wizard_carbsinput", treatments_wizard_carbsinput.value)
        savedInstanceState.putDouble("treatments_wizard_correctioninput", treatments_wizard_correctioninput.value)
        savedInstanceState.putDouble("treatments_wizard_carbtimeinput", treatments_wizard_carbtimeinput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        return inflater.inflate(R.layout.overview_wizard_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCheckedStates()
        processCobCheckBox()
        treatments_wizard_sbcheckbox.visibility = if (SP.getBoolean(R.string.key_usesuperbolus, false)) View.VISIBLE else View.GONE
        treatments_wizard_notes_layout.visibility = if (SP.getBoolean(R.string.key_show_notes_entry_dialogs, false)) View.VISIBLE else View.GONE

        val maxCarbs = MainApp.getConstraintChecker().maxCarbsAllowed.value()
        val maxCorrection = MainApp.getConstraintChecker().maxBolusAllowed.value()

        treatments_wizard_bginput.setParams(savedInstanceState?.getDouble("treatments_wizard_bginput")
                ?: 0.0, 0.0, 500.0, 0.1, DecimalFormat("0.0"), false, ok, textWatcher)
        treatments_wizard_carbsinput.setParams(savedInstanceState?.getDouble("treatments_wizard_carbsinput")
                ?: 0.0, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false, ok, textWatcher)
        val bolusstep = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription?.bolusStep
                ?: 0.1
        treatments_wizard_correctioninput.setParams(savedInstanceState?.getDouble("treatments_wizard_correctioninput")
                ?: 0.0, -maxCorrection, maxCorrection, bolusstep, DecimalFormatter.pumpSupportedBolusFormat(), false, ok, textWatcher)
        treatments_wizard_carbtimeinput.setParams(savedInstanceState?.getDouble("treatments_wizard_carbtimeinput")
                ?: 0.0, -60.0, 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)
        initDialog()

        treatments_wizard_percent_used.text = SP.getInt(R.string.key_boluswizard_percentage, 100).toString() + "%"
        // ok button
        ok.setOnClickListener {
            if (okClicked) {
                log.debug("guarding: ok already clicked")
            } else {
                okClicked = true
                parentContext?.let { context ->
                    wizard?.confirmAndExecute(context)
                }
            }
            dismiss()
        }
        // cancel button
        cancel.setOnClickListener { dismiss() }
        // checkboxes
        treatments_wizard_bgcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_ttcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_cobcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_basaliobcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_bolusiobcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_bgtrendcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        treatments_wizard_sbcheckbox.setOnCheckedChangeListener { buttonView, _ -> onCheckedChanged(buttonView) }
        // profile spinner
        treatments_wizard_profile.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.noprofileselected))
                ok.visibility = View.GONE
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                calculateInsulin()
                ok.visibility = View.VISIBLE
            }
        }
        // bus
        disposable.add(RxBus
                .toObservable(EventAutosensCalculationFinished::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    activity?.runOnUiThread { calculateInsulin() }
                }, {
                    FabricPrivacy.logException(it)
                })
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }

    fun onCheckedChanged(buttonView: CompoundButton) {
        saveCheckedStates()
        treatments_wizard_ttcheckbox.isEnabled = treatments_wizard_bgcheckbox.isChecked && TreatmentsPlugin.getPlugin().tempTargetFromHistory != null
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
        SP.putBoolean(MainApp.gs(R.string.key_wizard_include_cob), treatments_wizard_cobcheckbox.isChecked)
        SP.putBoolean(MainApp.gs(R.string.key_wizard_include_trend_bg), treatments_wizard_bgtrendcheckbox.isChecked)
    }

    private fun loadCheckedStates() {
        treatments_wizard_bgtrendcheckbox.isChecked = SP.getBoolean(MainApp.gs(R.string.key_wizard_include_trend_bg), false)
        treatments_wizard_cobcheckbox.isChecked = SP.getBoolean(MainApp.gs(R.string.key_wizard_include_cob), false)
    }

    private fun initDialog() {
        val profile = ProfileFunctions.getInstance().profile
        val profileStore = ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile

        if (profile == null || profileStore == null) {
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.noprofile))
            dismiss()
            return
        }

        val profileList: ArrayList<CharSequence>
        profileList = profileStore.profileList
        profileList.add(0, MainApp.gs(R.string.active))
        context?.let { context ->
            val adapter = ArrayAdapter(context, R.layout.spinner_centered, profileList)
            treatments_wizard_profile.adapter = adapter
        } ?: return


        val units = profile.units
        treatments_wizard_bgunits.text = units
        if (units == Constants.MGDL)
            treatments_wizard_bginput.setStep(1.0)
        else
            treatments_wizard_bginput.setStep(0.1)

        // Set BG if not old
        val lastBg = DatabaseHelper.actualBg()

        if (lastBg != null) {
            treatments_wizard_bginput.value = lastBg.valueToUnits(units)
        } else {
            treatments_wizard_bginput.value = 0.0
        }
        treatments_wizard_ttcheckbox.isEnabled = TreatmentsPlugin.getPlugin().tempTargetFromHistory != null

        // IOB calculation
        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments()
        val bolusIob = TreatmentsPlugin.getPlugin().lastCalculationTreatments.round()
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals()
        val basalIob = TreatmentsPlugin.getPlugin().lastCalculationTempBasals.round()

        treatments_wizard_bolusiobinsulin.text = StringUtils.formatInsulin(-bolusIob.iob)
        treatments_wizard_basaliobinsulin.text = StringUtils.formatInsulin(-basalIob.basaliob)

        calculateInsulin()

        treatments_wizard_percent_used.visibility = if (SP.getInt(R.string.key_boluswizard_percentage, 100) != 100) View.VISIBLE else View.GONE
    }

    private fun calculateInsulin() {
        val profileStore = ConfigBuilderPlugin.getPlugin().activeProfileInterface?.profile
        if (treatments_wizard_profile.selectedItem == null || profileStore == null)
            return  // not initialized yet
        var profileName = treatments_wizard_profile.selectedItem.toString()
        val specificProfile: Profile?
        if (profileName == MainApp.gs(R.string.active)) {
            specificProfile = ProfileFunctions.getInstance().profile
            profileName = ProfileFunctions.getInstance().profileName
        } else
            specificProfile = profileStore.getSpecificProfile(profileName)

        if (specificProfile == null) return

        // Entered values
        var c_bg = SafeParse.stringToDouble(treatments_wizard_bginput.text)
        val c_carbs = SafeParse.stringToInt(treatments_wizard_carbsinput.text)
        var c_correction = SafeParse.stringToDouble(treatments_wizard_correctioninput.text)
        val corrAfterConstraint = c_correction
        if (c_correction > 0)
            c_correction = MainApp.getConstraintChecker().applyBolusConstraints(Constraint(c_correction)).value()
        if (Math.abs(c_correction - corrAfterConstraint) > 0.01) { // c_correction != corrAfterConstraint doesn't work
            treatments_wizard_correctioninput.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.bolusconstraintapplied))
            return
        }
        val carbsAfterConstraint = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(c_carbs)).value()
        if (Math.abs(c_carbs - carbsAfterConstraint) > 0.01) {
            treatments_wizard_carbsinput.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.carbsconstraintapplied))
            return
        }

        c_bg = if (treatments_wizard_bgcheckbox.isChecked) c_bg else 0.0
        val tempTarget = if (treatments_wizard_ttcheckbox.isChecked) TreatmentsPlugin.getPlugin().tempTargetFromHistory else null

        // COB
        var c_cob = 0.0
        if (treatments_wizard_cobcheckbox.isChecked) {
            val cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "Wizard COB")
            cobInfo.displayCob?.let { c_cob = it }
        }

        val carbTime = SafeParse.stringToInt(treatments_wizard_carbtimeinput.text)

        wizard = BolusWizard(specificProfile, profileName, tempTarget, carbsAfterConstraint, c_cob, c_bg, corrAfterConstraint,
                SP.getInt(R.string.key_boluswizard_percentage, 100).toDouble(),
                treatments_wizard_bgcheckbox.isChecked,
                treatments_wizard_cobcheckbox.isChecked,
                treatments_wizard_bolusiobcheckbox.isChecked,
                treatments_wizard_basaliobcheckbox.isChecked,
                treatments_wizard_sbcheckbox.isChecked,
                treatments_wizard_ttcheckbox.isChecked,
                treatments_wizard_bgtrendcheckbox.isChecked,
                treatment_wizard_notes.text.toString(), carbTime)

        wizard?.let { wizard ->
            treatments_wizard_bg.text = c_bg.toString() + " ISF: " + DecimalFormatter.to1Decimal(wizard.sens)
            treatments_wizard_bginsulin.text = StringUtils.formatInsulin(wizard.insulinFromBG)

            treatments_wizard_carbs.text = DecimalFormatter.to0Decimal(c_carbs.toDouble()) + "g IC: " + DecimalFormatter.to1Decimal(wizard.ic)
            treatments_wizard_carbsinsulin.text = StringUtils.formatInsulin(wizard.insulinFromCarbs)

            treatments_wizard_bolusiobinsulin.text = StringUtils.formatInsulin(wizard.insulinFromBolusIOB)
            treatments_wizard_basaliobinsulin.text = StringUtils.formatInsulin(wizard.insulinFromBasalsIOB)

            treatments_wizard_correctioninsulin.text = StringUtils.formatInsulin(wizard.insulinFromCorrection)

            // Superbolus
            treatments_wizard_sb.text = if (treatments_wizard_sbcheckbox.isChecked) MainApp.gs(R.string.twohours) else ""
            treatments_wizard_sbinsulin.text = StringUtils.formatInsulin(wizard.insulinFromSuperBolus)

            // Trend
            if (treatments_wizard_bgtrendcheckbox.isChecked && wizard.glucoseStatus != null) {
                treatments_wizard_bgtrend.text = ((if (wizard.trend > 0) "+" else "")
                        + Profile.toUnitsString(wizard.trend * 3, wizard.trend * 3 / Constants.MMOLL_TO_MGDL, specificProfile.units)
                        + " " + specificProfile.units)
            } else {
                treatments_wizard_bgtrend.text = ""
            }
            treatments_wizard_bgtrendinsulin.text = StringUtils.formatInsulin(wizard.insulinFromTrend)

            // COB
            if (treatments_wizard_cobcheckbox.isChecked) {
                treatments_wizard_cob.text = DecimalFormatter.to2Decimal(c_cob) + "g IC: " + DecimalFormatter.to1Decimal(wizard.ic)
                treatments_wizard_cobinsulin.text = StringUtils.formatInsulin(wizard.insulinFromCOB)
            } else {
                treatments_wizard_cob.text = ""
                treatments_wizard_cobinsulin.text = ""
            }

            if (wizard.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0.0) {
                val insulinText = if (wizard.calculatedTotalInsulin > 0.0) DecimalFormatter.toPumpSupportedBolus(wizard.calculatedTotalInsulin) + "U" else ""
                val carbsText = if (carbsAfterConstraint > 0.0) DecimalFormatter.to0Decimal(carbsAfterConstraint.toDouble()) + "g" else ""
                treatments_wizard_total.text = MainApp.gs(R.string.result) + ": " + insulinText + " " + carbsText
                ok.visibility = View.VISIBLE
            } else {
                treatments_wizard_total.text = MainApp.gs(R.string.missing) + " " + DecimalFormatter.to0Decimal(wizard.carbsEquivalent) + "g"
                ok.visibility = View.INVISIBLE
            }
        }

    }
}
