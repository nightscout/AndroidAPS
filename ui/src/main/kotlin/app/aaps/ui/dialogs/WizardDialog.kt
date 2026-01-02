package app.aaps.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.fragment.app.FragmentManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.valueToUnits
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogWizardBinding
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs

class WizardDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var ctx: Context
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var bolusWizardProvider: Provider<BolusWizard>
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var queryingProtection = false
    private var wizard: BolusWizard? = null
    private var calculatedPercentage = 100
    private var calculatedCorrection = 0.0
    private var usePercentage = false
    private var carbsPassedIntoWizard = 0.0
    private var notesPassedIntoWizard = ""
    private var okClicked: Boolean = false // one shot guards
    private var disposable: CompositeDisposable = CompositeDisposable()
    private var bolusStep = 0.0
    private var _binding: DialogWizardBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val textWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            calculateInsulin()
        }
    }

    private val timeTextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            _binding?.let { binding ->
                binding.alarm.isChecked = binding.carbTimeInput.value > 0
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            calculateInsulin()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        aapsLogger.debug(LTag.APS, "Dialog opened: ${this.javaClass.simpleName}")
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("bg_input", binding.bgInput.value)
        savedInstanceState.putDouble("carbs_input", binding.carbsInput.value)
        savedInstanceState.putDouble("correction_input", binding.correctionInput.value)
        savedInstanceState.putDouble("carb_time_input", binding.carbTimeInput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        this.arguments?.let { bundle ->
            carbsPassedIntoWizard = bundle.getDouble("carbs_input")
            notesPassedIntoWizard = bundle.getString("notes_input") ?: ""
        }

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        _binding = DialogWizardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadCheckedStates()
        processCobCheckBox()
        val useSuperBolus = preferences.get(BooleanKey.OverviewUseSuperBolus)
        binding.sbCheckbox.visibility = useSuperBolus.toVisibility()
        binding.superBolusRow.visibility = useSuperBolus.toVisibility()
        binding.notesLayout.root.visibility = preferences.get(BooleanKey.OverviewShowNotesInDialogs).toVisibility()

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value()
        val maxCorrection = constraintChecker.getMaxBolusAllowed().value()
        bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        if (profileFunction.getUnits() == GlucoseUnit.MGDL) {
            binding.bgInput.setParams(
                savedInstanceState?.getDouble("bg_input")
                    ?: 0.0, 0.0, 500.0, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, timeTextWatcher
            )
        } else {
            binding.bgInput.setParams(
                savedInstanceState?.getDouble("bg_input")
                    ?: 0.0, 0.0, 30.0, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok, textWatcher
            )
        }
        binding.carbsInput.setParams(
            savedInstanceState?.getDouble("carbs_input")
                ?: 0.0, 0.0, maxCarbs.toDouble(), 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        // If there is no BG using % lower that 100% leads to high BGs
        // because loop doesn't add missing insulin
        var percentage = preferences.get(IntKey.OverviewBolusPercentage)
        val time = preferences.get(IntKey.OverviewResetBolusPercentageTime).toLong()
        persistenceLayer.getLastGlucoseValue().let {
            // if last value is older or there is no bg
            if (it != null) {
                if (it.timestamp < dateUtil.now() - T.mins(time).msecs())
                    percentage = 100
            } else percentage = 100
        }

        if (usePercentage) {
            calculatedPercentage = percentage
            binding.correctionInput.setParams(calculatedPercentage.toDouble(), 10.0, 200.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher)
            binding.correctionInput.value = calculatedPercentage.toDouble()
            binding.correctionUnit.text = "%"
        } else {
            binding.correctionInput.setParams(
                savedInstanceState?.getDouble("correction_input")
                    ?: 0.0,
                -maxCorrection,
                maxCorrection,
                bolusStep,
                decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep),
                false,
                binding.okcancel.ok,
                textWatcher
            )
            binding.correctionUnit.text = rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
        }
        binding.carbTimeInput.setParams(
            savedInstanceState?.getDouble("carb_time_input")
                ?: 0.0, -60.0, 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, timeTextWatcher
        )
        handler.post { initDialog() }
        calculatedPercentage = preferences.get(IntKey.OverviewBolusPercentage)
        binding.percentUsed.text = rh.gs(app.aaps.core.ui.R.string.format_percent, calculatedPercentage)
        binding.percentUsed.visibility = (calculatedPercentage != 100 || usePercentage).toVisibility()
        // ok button
        binding.okcancel.ok.setOnClickListener {
            if (okClicked) {
                aapsLogger.debug(LTag.UI, "guarding: ok already clicked")
            } else {
                okClicked = true
                calculateInsulin()
                context?.let { context ->
                    wizard?.confirmAndExecute(context)
                }
                aapsLogger.debug(LTag.APS, "Dialog ok pressed: ${this.javaClass.simpleName}")
            }
            dismiss()
        }
        binding.bgCheckboxIcon.setOnClickListener { binding.bgCheckbox.isChecked = !binding.bgCheckbox.isChecked }
        binding.ttCheckboxIcon.setOnClickListener { binding.ttCheckbox.isChecked = !binding.ttCheckbox.isChecked }
        binding.trendCheckboxIcon.setOnClickListener { binding.bgTrendCheckbox.isChecked = !binding.bgTrendCheckbox.isChecked }
        binding.cobCheckboxIcon.setOnClickListener { binding.cobCheckbox.isChecked = !binding.cobCheckbox.isChecked; processCobCheckBox(); }
        binding.iobCheckboxIcon.setOnClickListener { binding.iobCheckbox.isChecked = !binding.iobCheckbox.isChecked; processIobCheckBox(); }
        // cancel button
        binding.okcancel.cancel.setOnClickListener {
            aapsLogger.debug(LTag.APS, "Dialog canceled: ${this.javaClass.simpleName}")
            dismiss()
        }
        // checkboxes
        binding.bgCheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.ttCheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.cobCheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.iobCheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.bgTrendCheckbox.setOnCheckedChangeListener(::onCheckedChanged)
        binding.sbCheckbox.setOnCheckedChangeListener(::onCheckedChanged)

        val showCalc = preferences.get(BooleanKey.WizardCalculationVisible)
        binding.delimiter.visibility = showCalc.toVisibility()
        binding.result.visibility = showCalc.toVisibility()
        binding.calculationCheckbox.isChecked = showCalc
        binding.calculationCheckbox.setOnCheckedChangeListener { _, isChecked ->
            run {
                preferences.put(BooleanKey.WizardCalculationVisible, isChecked)
                binding.delimiter.visibility = isChecked.toVisibility()
                binding.result.visibility = isChecked.toVisibility()
                processEnabledIcons()
            }
        }

        processEnabledIcons()

        binding.correctionPercent.setOnCheckedChangeListener { _, isChecked ->
            run {
                preferences.put(BooleanKey.WizardCorrectionPercent, isChecked)
                binding.correctionUnit.text = if (isChecked) "%" else rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname)
                usePercentage = binding.correctionPercent.isChecked
                if (usePercentage) {
                    binding.correctionInput.setParams(calculatedPercentage.toDouble(), 10.0, 200.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher)
                    binding.correctionInput.customContentDescription = rh.gs(R.string.a11_correction_percentage)
                } else {
                    binding.correctionInput.setParams(
                        savedInstanceState?.getDouble("correction_input")
                            ?: 0.0, -maxCorrection, maxCorrection, bolusStep, decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep), false, binding.okcancel.ok,
                        textWatcher
                    )
                    binding.correctionInput.customContentDescription = rh.gs(R.string.a11_correction_units)
                }
                binding.correctionInput.updateA11yDescription()
                binding.correctionInput.value = if (usePercentage) calculatedPercentage.toDouble() else Round.roundTo(calculatedCorrection, bolusStep)
            }
        }
        // profile
        binding.profileList.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ -> calculateInsulin() }
        // bus
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ calculateInsulin() }, fabricPrivacy::logException)
        setA11yLabels()
    }

    private fun setA11yLabels() {
        binding.bgInputLabel.labelFor = binding.bgInput.editTextId
        binding.carbsInputLabel.labelFor = binding.carbsInput.editTextId
        binding.correctionInputLabel.labelFor = binding.correctionInput.editTextId
        binding.carbTimeInputLabel.labelFor = binding.carbTimeInput.editTextId
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun onCheckedChanged(buttonView: CompoundButton, @Suppress("unused") state: Boolean) {
        saveCheckedStates()
        binding.ttCheckbox.isEnabled = binding.bgCheckbox.isChecked && persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) != null
        binding.ttCheckboxIcon.visibility = binding.ttCheckbox.isEnabled.toVisibility()
        if (buttonView.id == binding.cobCheckbox.id)
            processCobCheckBox()
        if (buttonView.id == binding.iobCheckbox.id)
            processIobCheckBox()
        processEnabledIcons()
        calculateInsulin()
    }

    private fun processCobCheckBox() {
        if (binding.cobCheckbox.isChecked) {
            binding.iobCheckbox.isChecked = true
        }
    }

    private fun processIobCheckBox() {
        if (!binding.iobCheckbox.isChecked) {
            binding.cobCheckbox.isChecked = false
        }
    }

    private fun processEnabledIcons() {
        binding.bgCheckboxIcon.isChecked = binding.bgCheckbox.isChecked
        binding.ttCheckboxIcon.isChecked = binding.ttCheckbox.isChecked
        binding.trendCheckboxIcon.isChecked = binding.bgTrendCheckbox.isChecked
        binding.iobCheckboxIcon.isChecked = binding.iobCheckbox.isChecked
        binding.cobCheckboxIcon.isChecked = binding.cobCheckbox.isChecked

        binding.bgCheckboxIcon.alpha = if (binding.bgCheckbox.isChecked) 1.0f else 0.2f
        binding.ttCheckboxIcon.alpha = if (binding.ttCheckbox.isChecked) 1.0f else 0.2f
        binding.trendCheckboxIcon.alpha = if (binding.bgTrendCheckbox.isChecked) 1.0f else 0.2f
        binding.iobCheckboxIcon.alpha = if (binding.iobCheckbox.isChecked) 1.0f else 0.2f
        binding.cobCheckboxIcon.alpha = if (binding.cobCheckbox.isChecked) 1.0f else 0.2f

        binding.bgCheckboxIcon.visibility = binding.calculationCheckbox.isChecked.not().toVisibility()
        binding.ttCheckboxIcon.visibility = (binding.calculationCheckbox.isChecked.not() && binding.ttCheckbox.isEnabled).toVisibility()
        binding.trendCheckboxIcon.visibility = binding.calculationCheckbox.isChecked.not().toVisibility()
        binding.iobCheckboxIcon.visibility = binding.calculationCheckbox.isChecked.not().toVisibility()
        binding.cobCheckboxIcon.visibility = binding.calculationCheckbox.isChecked.not().toVisibility()
        binding.checkboxRow.visibility = binding.calculationCheckbox.isChecked.not().toVisibility()
    }

    private fun saveCheckedStates() {
        preferences.put(BooleanKey.WizardIncludeCob, binding.cobCheckbox.isChecked)
        preferences.put(BooleanKey.WizardIncludeTrend, binding.bgTrendCheckbox.isChecked)
        preferences.put(BooleanKey.WizardCorrectionPercent, binding.correctionPercent.isChecked)
    }

    private fun loadCheckedStates() {
        binding.bgTrendCheckbox.isChecked = preferences.get(BooleanKey.WizardIncludeTrend)
        binding.cobCheckbox.isChecked = preferences.get(BooleanKey.WizardIncludeCob)
        usePercentage = preferences.get(BooleanKey.WizardCorrectionPercent)
        binding.correctionPercent.isChecked = usePercentage
    }

    private fun valueToUnitsToString(value: Double, units: String): String =
        if (units == GlucoseUnit.MGDL.asText) decimalFormatter.to0Decimal(value)
        else decimalFormatter.to1Decimal(value * Constants.MGDL_TO_MMOLL)

    private fun initDialog() {
        val profile = profileFunction.getProfile()
        val profileStore = activePlugin.activeProfileSource.profile
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())

        if (profile == null || profileStore == null) {
            ToastUtils.errorToast(ctx, app.aaps.core.ui.R.string.noprofile)
            dismiss()
            return
        }

        // IOB calculation
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()

        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (carbsPassedIntoWizard != 0.0) {
                binding.carbsInput.value = carbsPassedIntoWizard
            }
            if (notesPassedIntoWizard.isNotBlank()) {
                binding.notesLayout.notes.setText(notesPassedIntoWizard)
            }

            val profileList: ArrayList<CharSequence> = profileStore.getProfileList()
            profileList.add(0, rh.gs(app.aaps.core.ui.R.string.active))
            context?.let { context ->
                binding.profileList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, profileList))
                binding.profileList.setText(profileList[0], false)
            }

            val units = profileFunction.getUnits()
            binding.bgUnits.text = units.asText
            binding.bgInput.step = if (units == GlucoseUnit.MGDL) 1.0 else 0.1

            // Set BG if not old
            binding.bgInput.value = iobCobCalculator.ads.actualBg()?.valueToUnits(units) ?: 0.0

            binding.ttCheckbox.isEnabled = tempTarget != null
            binding.ttCheckboxIcon.visibility = binding.ttCheckbox.isEnabled.toVisibility()
            binding.iobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, -bolusIob.iob - basalIob.basaliob)

            calculateInsulin()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun calculateInsulin() {
        val profileStore = activePlugin.activeProfileSource.profile ?: return // not initialized yet
        var profileName = binding.profileList.text.toString()
        val specificProfile: Profile?
        if (profileName == rh.gs(app.aaps.core.ui.R.string.active)) {
            specificProfile = profileFunction.getProfile()
            profileName = profileFunction.getProfileName()
        } else
            specificProfile = profileStore.getSpecificProfile(profileName)?.let { ProfileSealed.Pure(it, activePlugin) }

        if (specificProfile == null) return

        // Entered values
        val usePercentage = binding.correctionPercent.isChecked
        var bg = SafeParse.stringToDouble(binding.bgInput.text)
        val carbs = SafeParse.stringToInt(binding.carbsInput.text)
        val correction = if (!usePercentage) {
            if (Round.roundTo(calculatedCorrection, bolusStep) == SafeParse.stringToDouble(binding.correctionInput.text))
                calculatedCorrection
            else
                SafeParse.stringToDouble(binding.correctionInput.text)
        } else
            0.0
        val percentageCorrection = if (usePercentage) {
            if (calculatedPercentage == SafeParse.stringToInt(binding.correctionInput.text))
                calculatedPercentage
            else
                SafeParse.stringToInt(binding.correctionInput.text)
        } else
            preferences.get(IntKey.OverviewBolusPercentage).toDouble()
        val carbsAfterConstraint = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()
        if (abs(carbs - carbsAfterConstraint) > 0.01) {
            binding.carbsInput.value = 0.0
            ToastUtils.warnToast(ctx, R.string.carbs_constraint_applied)
            return
        }

        bg = if (binding.bgCheckbox.isChecked) bg else 0.0
        val tempTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())

        // COB
        var cob = 0.0
        if (binding.cobCheckbox.isChecked) {
            val cobInfo = iobCobCalculator.getCobInfo("Wizard COB")
            cobInfo.displayCob?.let { cob = it }
        }

        val carbTime = SafeParse.stringToInt(binding.carbTimeInput.text)

        wizard = bolusWizardProvider.get().doCalc(
            specificProfile, profileName, tempTarget, carbsAfterConstraint, cob, bg, correction, preferences.get(IntKey.OverviewBolusPercentage),
            binding.bgCheckbox.isChecked,
            binding.cobCheckbox.isChecked,
            binding.iobCheckbox.isChecked,
            binding.iobCheckbox.isChecked,
            binding.sbCheckbox.isChecked,
            binding.ttCheckbox.isChecked,
            binding.bgTrendCheckbox.isChecked,
            binding.alarm.isChecked,
            binding.notesLayout.notes.text.toString(),
            carbTime,
            usePercentage = usePercentage,
            totalPercentage = percentageCorrection.toDouble()
        )

        wizard?.let { wizard ->
            binding.bg.text = rh.gs(R.string.format_bg_isf, valueToUnitsToString(profileUtil.convertToMgdl(bg, profileFunction.getUnits()), profileFunction.getUnits().asText), wizard.sens)
            binding.bgInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromBG)

            binding.carbs.text = rh.gs(R.string.format_carbs_ic, carbs.toDouble(), wizard.ic)
            binding.carbsInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromCarbs)

            binding.iobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, -wizard.insulinFromBolusIOB - wizard.insulinFromBasalIOB)

            binding.correctionInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromCorrection)

            // Superbolus
            binding.sb.text = if (binding.sbCheckbox.isChecked) rh.gs(R.string.two_hours) else ""
            binding.sbInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromSuperBolus)

            // Trend
            if (binding.bgTrendCheckbox.isChecked && wizard.glucoseStatus != null) {
                binding.bgTrend.text = ((if (wizard.trend > 0) "+" else "")
                    + profileUtil.fromMgdlToStringInUnits(wizard.trend * 3)
                    + " " + profileFunction.getUnits())
            } else {
                binding.bgTrend.text = ""
            }
            binding.bgTrendInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromTrend)

            // COB
            if (binding.cobCheckbox.isChecked) {
                binding.cob.text = rh.gs(R.string.format_cob_ic, cob, wizard.ic)
                binding.cobInsulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.insulinFromCOB)
            } else {
                binding.cob.text = ""
                binding.cobInsulin.text = ""
            }

            if (wizard.calculatedTotalInsulin > 0.0 || carbsAfterConstraint > 0.0) {
                val insulinText =
                    if (wizard.calculatedTotalInsulin > 0.0) rh.gs(app.aaps.core.ui.R.string.format_insulin_units, wizard.calculatedTotalInsulin)
                        .formatColor(context, rh, app.aaps.core.ui.R.attr.bolusColor) else ""
                val carbsText = if (carbsAfterConstraint > 0.0) rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraint).formatColor(
                    context, rh, app.aaps.core.ui.R.attr
                        .carbsColor
                ) else ""
                binding.total.text = HtmlHelper.fromHtml(rh.gs(R.string.result_insulin_carbs, insulinText, carbsText))
                binding.okcancel.ok.visibility = View.VISIBLE
            } else {
                binding.total.text = HtmlHelper.fromHtml(rh.gs(R.string.missing_carbs, wizard.carbsEquivalent.toInt()).formatColor(context, rh, app.aaps.core.ui.R.attr.carbsColor))
                binding.okcancel.ok.visibility = View.INVISIBLE
            }
            binding.percentUsed.text = rh.gs(app.aaps.core.ui.R.string.format_percent, wizard.percentageCorrection)
            calculatedPercentage = wizard.calculatedPercentage
            calculatedCorrection = wizard.calculatedCorrection
        }

    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: "")
        }
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}