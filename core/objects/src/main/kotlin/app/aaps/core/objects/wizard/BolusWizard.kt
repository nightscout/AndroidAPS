package app.aaps.core.objects.wizard

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.BolusWizardData
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.data.ui.ConfirmationLine
import app.aaps.core.data.ui.ConfirmationRole
import app.aaps.core.data.ui.confirmationLines
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.highValueToUnitsToString
import app.aaps.core.objects.extensions.lowValueToUnitsToString
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.runningMode.PumpCommandGate
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.utils.JsonHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.Calendar
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BolusWizard @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val preferences: Preferences,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val constraintChecker: ConstraintsChecker,
    private val activePlugin: ActivePlugin,
    private val loop: Loop,
    private val iobCobCalculator: IobCobCalculator,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val uel: UserEntryLogger,
    private val automation: Automation,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val uiInteraction: UiInteraction,
    private val persistenceLayer: PersistenceLayer,
    private val decimalFormatter: DecimalFormatter,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val runningModeGuard: RunningModeGuard,
    private val activeInsulin: Insulin,
    private val ch: ConcentrationHelper,
    private val wizardBolusExecutor: WizardBolusExecutor,
    @ApplicationScope private val appScope: CoroutineScope
) {

    var timeStamp = dateUtil.now()

    // Intermediate
    var sens = 0.0
        private set
    var ic = 0.0
        private set
    var glucoseStatus: GlucoseStatus? = null
        private set
    private var targetBGLow = 0.0
    private var targetBGHigh = 0.0
    private var bgDiff = 0.0
    var insulinFromBG = 0.0
        private set
    var insulinFromCarbs = 0.0
        private set
    var insulinFromBolusIOB = 0.0
        private set
    var insulinFromBasalIOB = 0.0
        private set
    var insulinFromCorrection = 0.0
        private set
    var insulinFromSuperBolus = 0.0
        private set
    var insulinFromCOB = 0.0
        private set
    var insulinFromTrend = 0.0
        private set
    var trend = 0.0
        private set

    private var accepted = false

    // Result
    var calculatedTotalInsulin: Double = 0.0
        private set
    // Raw sum before the negative-total clamp (calculatedTotalInsulin = 0.0 branch); equals
    // calculatedTotalInsulin when non-negative. Used by the wear correction buttons so they
    // spend the right number of steps recovering to 0 before going positive.
    var unclampedCalculatedInsulin: Double = 0.0
        private set
    var totalBeforePercentageAdjustment: Double = 0.0
        private set
    var carbsEquivalent: Double = 0.0
        private set
    var insulinAfterConstraints: Double = 0.0
        private set
    var calculatedPercentage: Int = 100
        private set
    var calculatedCorrection: Double = 0.0
        private set

    /** Immutable snapshot of the computed result, built at the end of [doCalc] (additive — the legacy
     *  fields above stay). The shared bolus path consumes this instead of reaching into individual fields. */
    lateinit var data: BolusWizardData
        private set

    // Input
    lateinit var profile: Profile
    lateinit var profileName: String
    var tempTarget: TT? = null
    var carbs: Int = 0
    var cob: Double = 0.0
    var bg: Double = 0.0
    private var correction: Double = 0.0
    var percentageCorrection: Int = 100
    private var totalPercentage: Double = 100.0
    private var useBg: Boolean = false
    private var useCob: Boolean = false
    private var includeBolusIOB: Boolean = false
    private var includeBasalIOB: Boolean = false
    private var useSuperBolus: Boolean = false
    private var useTT: Boolean = false
    private var useTrend: Boolean = false
    private var useAlarm = false
    var notes: String = ""
    private var carbTime: Int = 0
    var usePercentage: Boolean = false
    var positiveIOBOnly: Boolean = false
    private var source: Sources = Sources.WizardDialog

    suspend fun doCalc(
        profile: Profile,
        profileName: String,
        tempTarget: TT?,
        carbs: Int,
        cob: Double,
        bg: Double,
        correction: Double,
        percentageCorrection: Int = 100,
        useBg: Boolean,
        useCob: Boolean,
        includeBolusIOB: Boolean,
        includeBasalIOB: Boolean,
        useSuperBolus: Boolean,
        useTT: Boolean,
        useTrend: Boolean,
        useAlarm: Boolean,
        notes: String = "",
        carbTime: Int = 0,
        usePercentage: Boolean = false,
        totalPercentage: Double = 100.0,
        positiveIOBOnly: Boolean = false,
        source: Sources = Sources.WizardDialog
    ): BolusWizard {

        this.profile = profile
        this.profileName = profileName
        this.tempTarget = tempTarget
        this.carbs = carbs
        this.cob = cob
        this.bg = bg
        this.correction = correction
        this.percentageCorrection = percentageCorrection
        this.useBg = useBg
        this.useCob = useCob
        this.includeBolusIOB = includeBolusIOB
        this.includeBasalIOB = includeBasalIOB
        this.useSuperBolus = useSuperBolus
        this.useTT = useTT
        this.useTrend = useTrend
        this.useAlarm = useAlarm
        this.notes = notes
        this.carbTime = carbTime
        this.usePercentage = usePercentage
        this.totalPercentage = totalPercentage
        this.positiveIOBOnly = positiveIOBOnly
        this.source = source

        // Insulin from BG
        sens = profileUtil.fromMgdlToUnits(profile.getIsfMgdlForCarbs(dateUtil.now(), "BolusWizard", config, processedDeviceStatusData))
        targetBGLow = profileUtil.fromMgdlToUnits(profile.getTargetLowMgdl())
        targetBGHigh = profileUtil.fromMgdlToUnits(profile.getTargetHighMgdl())
        if (useTT && tempTarget != null) {
            targetBGLow = profileUtil.fromMgdlToUnits(tempTarget.lowTarget)
            targetBGHigh = profileUtil.fromMgdlToUnits(tempTarget.highTarget)
        }
        if (useBg && bg > 0) {
            bgDiff = when {
                bg in targetBGLow..targetBGHigh -> 0.0
                bg <= targetBGLow               -> bg - targetBGLow
                else                            -> bg - targetBGHigh
            }
            insulinFromBG = bgDiff / sens
        }

        // Insulin from 15 min trend
        glucoseStatus = glucoseStatusProvider.glucoseStatusData
        glucoseStatus?.let {
            if (useTrend) {
                trend = it.shortAvgDelta
                insulinFromTrend = profileUtil.fromMgdlToUnits(trend) * 3 / sens
            }
        }

        // Insulin from carbs
        ic = profile.getIc()
        insulinFromCarbs = carbs / ic
        insulinFromCOB = if (useCob) (cob / ic) else 0.0

        // Insulin from IOB
        // IOB calculation
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()

        insulinFromBolusIOB = if (includeBolusIOB) bolusIob.iob else 0.0
        insulinFromBasalIOB = if (includeBasalIOB) basalIob.basaliob else 0.0

        var calculatedTotalIOB = insulinFromBolusIOB + insulinFromBasalIOB
        calculatedTotalIOB = if (positiveIOBOnly && calculatedTotalIOB < 0.0) 0.0 else -calculatedTotalIOB

        // Insulin from correction
        insulinFromCorrection = if (usePercentage) 0.0 else correction

        // Insulin from superbolus for 2h. Get basal rate now and after 1h
        if (useSuperBolus) {
            insulinFromSuperBolus = profile.getBasal()
            var timeAfter1h = System.currentTimeMillis()
            timeAfter1h += T.hours(1).msecs()
            insulinFromSuperBolus += profile.getBasal(timeAfter1h)
        }

        // Total
        // Suggestion components (BG, Trend, Carbs, COB) are scaled by percentage.
        // Fact components (IOB, Direct Correction) are NOT scaled — they represent
        // actual measured or explicit values, not wizard suggestions.
        // This prevents the compounding overdose bug when splitting carb entries (#2561):
        // previously, IOB was scaled down by percentage, under-counting active insulin.
        val scaledComponents = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulinFromCOB
        val unscaledComponents = calculatedTotalIOB + insulinFromCorrection

        val percentage = if (usePercentage) totalPercentage else percentageCorrection.toDouble()

        totalBeforePercentageAdjustment = scaledComponents + unscaledComponents
        calculatedTotalInsulin = scaledComponents * percentage / 100.0 + unscaledComponents
        var preClamp = calculatedTotalInsulin  // save before constraint calcs or negative clamp

        // Percentage adjustment
        if (calculatedTotalInsulin >= 0) {
            if (usePercentage)
                calcCorrectionWithConstraints()
            else
                calcPercentageWithConstraints()
            if (usePercentage)  //Should be updated after calcCorrectionWithConstraints and calcPercentageWithConstraints to have correct synthesis in WizardInfo
                this.percentageCorrection = Round.roundTo(totalPercentage, 1.0).toInt()
            preClamp = calculatedTotalInsulin  // update after constraint calcs (may have changed)
        } else {
            carbsEquivalent = (-calculatedTotalInsulin) * ic
            calculatedTotalInsulin = 0.0
            calculatedPercentage = percentageCorrection
            calculatedCorrection = 0.0
            // preClamp stays as the original negative value
        }

        // Amount-aware (Insight) + concentration-adjusted deliverable step, so the rounded value matches the pump grid.
        val bolusStep = ch.bolusStep(calculatedTotalInsulin)
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep)
        unclampedCalculatedInsulin = Round.roundTo(preClamp, bolusStep)

        insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(calculatedTotalInsulin, aapsLogger)).value()

        data = BolusWizardData(
            timeStamp = timeStamp,
            carbs = carbs,
            cob = cob,
            sens = sens,
            ic = ic,
            trend = trend,
            insulinFromBG = insulinFromBG,
            insulinFromCarbs = insulinFromCarbs,
            insulinFromBolusIOB = insulinFromBolusIOB,
            insulinFromBasalIOB = insulinFromBasalIOB,
            insulinFromCorrection = insulinFromCorrection,
            insulinFromSuperBolus = insulinFromSuperBolus,
            insulinFromCOB = insulinFromCOB,
            insulinFromTrend = insulinFromTrend,
            calculatedTotalInsulin = calculatedTotalInsulin,
            totalBeforePercentageAdjustment = totalBeforePercentageAdjustment,
            carbsEquivalent = carbsEquivalent,
            insulinAfterConstraints = insulinAfterConstraints,
            calculatedPercentage = calculatedPercentage,
            calculatedCorrection = calculatedCorrection,
            percentageCorrection = percentageCorrection,
            useBg = useBg,
            useCob = useCob,
            includeBolusIOB = includeBolusIOB,
            includeBasalIOB = includeBasalIOB,
            useTT = useTT,
            useTrend = useTrend,
            useSuperBolus = useSuperBolus
        )

        aapsLogger.debug(this.toString())
        return this
    }

    fun createBolusCalculatorResult(): BCR {
        val unit = profileFunction.getUnits()
        return BCR(
            timestamp = dateUtil.now(),
            targetBGLow = profileUtil.convertToMgdl(targetBGLow, unit),
            targetBGHigh = profileUtil.convertToMgdl(targetBGHigh, unit),
            isf = profileUtil.convertToMgdl(sens, unit),
            ic = ic,
            bolusIOB = insulinFromBolusIOB,
            wasBolusIOBUsed = includeBolusIOB,
            basalIOB = insulinFromBasalIOB,
            wasBasalIOBUsed = includeBasalIOB,
            glucoseValue = profileUtil.convertToMgdl(bg, unit),
            wasGlucoseUsed = useBg && bg > 0,
            glucoseDifference = bgDiff,
            glucoseInsulin = insulinFromBG,
            glucoseTrend = profileUtil.fromMgdlToUnits(trend, unit),
            wasTrendUsed = useTrend,
            trendInsulin = insulinFromTrend,
            cob = cob,
            wasCOBUsed = useCob,
            cobInsulin = insulinFromCOB,
            carbs = carbs.toDouble(),
            wereCarbsUsed = carbs > 0,
            carbsInsulin = insulinFromCarbs,
            otherCorrection = correction,
            wasSuperbolusUsed = useSuperBolus,
            superbolusInsulin = insulinFromSuperBolus,
            wasTempTargetUsed = useTT,
            totalInsulin = calculatedTotalInsulin,
            percentageCorrection = percentageCorrection,
            profileName = profileName,
            note = notes
        )
    }

    fun buildConfirmationLines(
        advisor: Boolean,
        quickWizardEntry: QuickWizardEntry? = null,
        eCarbsGrams: Int = 0,
        eCarbsDelayMinutes: Int = 0,
        eCarbsDurationHours: Int = 0,
        forcedRecordOnly: Boolean = false
    ): List<ConfirmationLine> =
        confirmationLines {
            if (insulinAfterConstraints > 0) {
                val pct = if (percentageCorrection != 100) " ($percentageCorrection%)" else ""
                line(
                    ConfirmationRole.BOLUS,
                    rh.gs(
                        app.aaps.core.ui.R.string.confirmation_line,
                        rh.gs(app.aaps.core.ui.R.string.bolus),
                        rh.gs(app.aaps.core.ui.R.string.format_insulin_units, insulinAfterConstraints) + pct
                    )
                )
            }
            if (carbs > 0 && !advisor) {
                val timeShift = when {
                    carbTime > 0 -> " (+" + rh.gs(app.aaps.core.ui.R.string.mins, carbTime) + ")"
                    carbTime < 0 -> " (" + rh.gs(app.aaps.core.ui.R.string.mins, carbTime) + ")"
                    else         -> ""
                }
                line(
                    ConfirmationRole.CARBS,
                    rh.gs(
                        app.aaps.core.ui.R.string.confirmation_line,
                        rh.gs(app.aaps.core.ui.R.string.carbs),
                        rh.gs(app.aaps.core.ui.R.string.format_carbs, carbs) + timeShift
                    )
                )
            }
            if (insulinFromCOB > 0) {
                line(
                    ConfirmationRole.COB,
                    rh.gs(
                        app.aaps.core.ui.R.string.confirmation_line,
                        rh.gs(app.aaps.core.ui.R.string.cobvsiob),
                        rh.gs(
                            app.aaps.core.ui.R.string.formatsignedinsulinunits,
                            -insulinFromBolusIOB - insulinFromBasalIOB + insulinFromCOB + insulinFromBG
                        )
                    )
                )
                val absorptionRate = iobCobCalculator.ads.slowAbsorptionPercentage(60)
                if (absorptionRate > .25) {
                    line(ConfirmationRole.COB, rh.gs(app.aaps.core.ui.R.string.slowabsorptiondetected_plain, (absorptionRate * 100).toInt()))
                }
            }
            if (abs(insulinAfterConstraints - calculatedTotalInsulin) > ch.bolusStep(insulinAfterConstraints)) {
                line(ConfirmationRole.WARNING, rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, calculatedTotalInsulin, insulinAfterConstraints))
            }
            if ((config.AAPSCLIENT || forcedRecordOnly) && insulinAfterConstraints > 0) {
                line(ConfirmationRole.WARNING, rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only))
            }
            if (useAlarm && !advisor && carbs > 0 && carbTime > 0) {
                line(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.alarminxmin, carbTime))
            }
            if (advisor) {
                line(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.advisoralarm))
            }

            if (quickWizardEntry != null) {
                val eCarbsYesNo = JsonHelper.safeGetInt(quickWizardEntry.storage, "useEcarbs", QuickWizardEntry.NO)
                if (eCarbsYesNo == QuickWizardEntry.YES) {
                    val timeOffset = JsonHelper.safeGetInt(quickWizardEntry.storage, "time", 0)
                    val duration = JsonHelper.safeGetInt(quickWizardEntry.storage, "duration", 0)
                    val carbs2 = JsonHelper.safeGetInt(quickWizardEntry.storage, "carbs2", 0)
                    if (carbs2 > 0) {
                        val ecarbsMessage = rh.gs(app.aaps.core.ui.R.string.format_carbs, carbs2) + "/" + duration + "h (+" + timeOffset + "min)"
                        line(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.confirmation_line, rh.gs(app.aaps.core.ui.R.string.uel_extended_carbs), ecarbsMessage))
                    }
                }
            }
            if (eCarbsGrams > 0) {
                line(ConfirmationRole.INFO, rh.gs(app.aaps.core.ui.R.string.wizard_ecarbs, eCarbsGrams, eCarbsDurationHours, eCarbsDelayMinutes))
            }
        }

    fun buildWizardDetail(): EventData.WizardDetail {
        val ttLabel = if (useTT && tempTarget != null) {
            val fmt = DecimalFormat("0.0#")
            val low = fmt.format(profileUtil.fromMgdlToUnits(tempTarget!!.lowTarget))
            val high = fmt.format(profileUtil.fromMgdlToUnits(tempTarget!!.highTarget))
            if (low == high) low else "$low-$high"
        } else null
        return EventData.WizardDetail(
            totalInsulin = calculatedTotalInsulin,
            unclampedInsulin = unclampedCalculatedInsulin,
            carbs = carbs,
            insulinFromBG = insulinFromBG,
            insulinFromTrend = insulinFromTrend,
            insulinFromCOB = insulinFromCOB,
            insulinFromCarbs = insulinFromCarbs,
            insulinFromBolusIOB = insulinFromBolusIOB,
            insulinFromBasalIOB = insulinFromBasalIOB,
            includeBolusIOB = includeBolusIOB,
            includeBasalIOB = includeBasalIOB,
            percentageCorrection = percentageCorrection,
            cob = cob,
            tempTargetLabel = ttLabel,
            ic = ic,
            sens = sens,
        )
    }

    private fun calcPercentageWithConstraints() {
        calculatedPercentage = 100
        if (totalBeforePercentageAdjustment != insulinFromCorrection)
            calculatedPercentage = (calculatedTotalInsulin / (totalBeforePercentageAdjustment - insulinFromCorrection) * 100).toInt()
        calculatedPercentage = max(calculatedPercentage, 10)
        calculatedPercentage = min(calculatedPercentage, 250)
    }

    private fun calcCorrectionWithConstraints() {
        calculatedCorrection = totalBeforePercentageAdjustment * totalPercentage / percentageCorrection - totalBeforePercentageAdjustment
        //Apply constraints
        calculatedCorrection = min(constraintChecker.getMaxBolusAllowed().value(), calculatedCorrection)
        calculatedCorrection = max(-constraintChecker.getMaxBolusAllowed().value(), calculatedCorrection)
    }

    // --- Compose-friendly methods (no Context/attrs dependency) ---

    /**
     * Pure-logic SSOT for whether the high-BG bolus advisor ("correct now, eat later?") should be offered:
     * the pref is on, BG > 180 mg/dL, carbs are present, and the carb time isn't in the past. Used by the
     * wizard-dialog / quick-wizard prepare path on both master and client.
     */
    fun needsBolusAdvisor(): Boolean =
        preferences.get(BooleanKey.OverviewUseBolusAdvisor) &&
            profileUtil.convertToMgdl(bg, profile.units) > 180 &&
            carbs > 0 &&
            carbTime >= 0

    /**
     * Execute normal bolus wizard flow (bolus + carbs + superbolus + BCR save).
     * No UI dependency — errors reported via [onError] callback.
     */
    suspend fun executeNormal(onError: (String) -> Unit, quickWizardEntry: QuickWizardEntry? = null, eCarbsGrams: Int = 0, eCarbsDelayMinutes: Int = 0, eCarbsDurationHours: Int = 0, forcedRecordOnly: Boolean = false) {
        if (accepted) {
            aapsLogger.debug(LTag.UI, "guarding: already accepted")
            return
        }
        // Pre-check: if the mode forbids a new bolus, show a snackbar and skip without ever
        // reaching the delivery path (avoids the alarm-on-failure callback path).
        // When forcedRecordOnly is true the caller has already shown a banner and confirmation
        // line stating the entry will be recorded only — we bypass the snackbar guard and route
        // to the record-only path below.
        if (!forcedRecordOnly && calculatedTotalInsulin > 0.0 &&
            runningModeGuard.checkWithSnackbar(PumpCommandGate.CommandKind.BOLUS)
        ) return
        accepted = true
        if (calculatedTotalInsulin > 0.0)
            automation.removeAutomationEventBolusReminder()
        if (carbs > 0.0)
            automation.removeAutomationEventEatReminder()

        val profile = profileFunction.getProfile() ?: return
        val now = dateUtil.now()

        if (insulinAfterConstraints > 0 || carbs > 0) {
            if (useSuperBolus && !forcedRecordOnly) {
                // Writing the SUPER_BOLUS row is enough — RunningModeReconciler observes the
                // change and issues the zero-TBR.
                if (loop.allowedNextModes().contains(RM.Mode.SUPER_BOLUS)) {
                    loop.handleRunningModeChange(
                        durationInMinutes = 2 * 60,
                        profile = profile,
                        newRM = RM.Mode.SUPER_BOLUS,
                        action = Action.SUPERBOLUS_TBR,
                        source = source
                    )
                    rxBus.send(EventRefreshOverview("WizardDialog"))
                }
            }
            val action = when {
                insulinAfterConstraints == 0.0 -> Action.CARBS
                carbs == 0                     -> Action.BOLUS
                else                           -> Action.TREATMENT
            }
            val bolusCalculatorResult = createBolusCalculatorResult()
            quickWizardEntry?.markAsUsed()
            // Schedule carb timer before bolus delivery. Scheduling in the bolus completion callback
            // fails when the screen is off because Android blocks startActivity() from the background.
            if (useAlarm && carbs > 0 && carbTime > 0) {
                automation.scheduleTimeToEatReminder(T.mins(carbTime.toLong()).secs().toInt())
            }
            if (forcedRecordOnly) {
                uel.log(
                    action = action,
                    source = source,
                    note = notes,
                    listValues = listOfNotNull(
                        ValueWithUnit.TEType(TE.Type.BOLUS_WIZARD),
                        ValueWithUnit.Insulin(insulinAfterConstraints).takeIf { insulinAfterConstraints != 0.0 },
                        ValueWithUnit.Gram(carbs).takeIf { carbs != 0 },
                        ValueWithUnit.Minute(carbTime).takeIf { carbTime != 0 }
                    )
                )
                val recordIcfg = this.profile.iCfg ?: activeInsulin.iCfg
                val detailedBolusInfo = DetailedBolusInfo().apply {
                    insulin = insulinAfterConstraints
                    carbs = this@BolusWizard.carbs.toDouble()
                    carbsTimestamp = now + T.mins(this@BolusWizard.carbTime.toLong()).msecs()
                    notes = this@BolusWizard.notes
                }
                if (insulinAfterConstraints > 0) {
                    persistenceLayer.insertOrUpdateBolus(
                        bolus = detailedBolusInfo.createBolus(recordIcfg),
                        action = action,
                        source = source,
                        note = rh.gs(app.aaps.core.ui.R.string.record) + if (notes.isNotEmpty()) ": $notes" else ""
                    )
                }
                if (carbs > 0) {
                    persistenceLayer.insertOrUpdateCarbs(
                        carbs = detailedBolusInfo.createCarbs(),
                        action = action,
                        source = source,
                        note = notes.ifEmpty { rh.gs(app.aaps.core.ui.R.string.record) }
                    )
                }
                persistenceLayer.insertOrUpdateBolusCalculatorResult(bolusCalculatorResult)
            } else {
                // Phone wizard bolus now rides the shared executor's canonical BOLUS_WIZARD entry point —
                // identical end state to the watch, differing only in [source]. The executor logs the UEL,
                // delivers via the one audited pump path, and persists the BCR.
                wizardBolusExecutor.deliverWizardBolus(
                    insulin = insulinAfterConstraints,
                    carbs = carbs,
                    carbTimeMinutes = carbTime,
                    mgdlGlucose = profileUtil.convertToMgdl(bg, this.profile.units),
                    bolusCalculatorResult = bolusCalculatorResult,
                    notes = notes,
                    source = source,
                    onError = onError
                )
            }
        }
        if (quickWizardEntry != null) {
            scheduleECarbsFromQuickWizardCompose(quickWizardEntry, onError, forcedRecordOnly)
        }
        if (eCarbsGrams > 0) {
            scheduleECarbs(eCarbsGrams, eCarbsDelayMinutes, eCarbsDurationHours, onError, forcedRecordOnly)
        }
    }

    /**
     * Execute bolus advisor flow (correction-only bolus, no carbs, eat reminder).
     * No UI dependency — errors reported via [onError] callback.
     */
    suspend fun executeBolusAdvisor(onError: (String) -> Unit, eCarbsGrams: Int = 0, eCarbsDelayMinutes: Int = 0, eCarbsDurationHours: Int = 0, forcedRecordOnly: Boolean = false) {
        if (accepted) {
            aapsLogger.debug(LTag.UI, "guarding: already accepted")
            return
        }
        if (!forcedRecordOnly && calculatedTotalInsulin > 0.0 &&
            runningModeGuard.checkWithSnackbar(PumpCommandGate.CommandKind.BOLUS)
        ) return
        accepted = true
        if (calculatedTotalInsulin > 0.0)
            automation.removeAutomationEventBolusReminder()
        if (carbs > 0.0)
            automation.removeAutomationEventEatReminder()

        if (insulinAfterConstraints > 0) {
            if (forcedRecordOnly) {
                uel.log(
                    action = Action.BOLUS_ADVISOR,
                    source = source,
                    note = notes,
                    listValues = listOf(
                        ValueWithUnit.TEType(TE.Type.CORRECTION_BOLUS),
                        ValueWithUnit.Insulin(insulinAfterConstraints)
                    )
                )
                val recordIcfg = this.profile.iCfg ?: activeInsulin.iCfg
                val detailedBolusInfo = DetailedBolusInfo().apply {
                    eventType = TE.Type.CORRECTION_BOLUS
                    insulin = insulinAfterConstraints
                    carbs = 0.0
                    notes = this@BolusWizard.notes
                }
                appScope.launch {
                    persistenceLayer.insertOrUpdateBolus(
                        bolus = detailedBolusInfo.createBolus(recordIcfg),
                        action = Action.BOLUS_ADVISOR,
                        source = source,
                        note = rh.gs(app.aaps.core.ui.R.string.record) + if (notes.isNotEmpty()) ": $notes" else ""
                    )
                }
                automation.scheduleAutomationEventEatReminder()
            } else {
                // Advisor bolus rides the shared canonical executor entry point (one audited path); the
                // executor logs the BOLUS_ADVISOR entry, delivers, and schedules the eat reminder on success.
                wizardBolusExecutor.deliverBolusAdvisor(
                    insulin = insulinAfterConstraints,
                    mgdlGlucose = profileUtil.convertToMgdl(bg, this.profile.units),
                    bolusCalculatorResult = createBolusCalculatorResult(),
                    notes = notes,
                    source = source,
                    onError = onError
                )
            }
        }
        if (eCarbsGrams > 0) {
            scheduleECarbs(eCarbsGrams, eCarbsDelayMinutes, eCarbsDurationHours, onError, forcedRecordOnly)
        }
    }

    private fun scheduleECarbs(eCarbsGrams: Int, delayMinutes: Int, durationHours: Int, onError: (String) -> Unit, forcedRecordOnly: Boolean = false) {
        // delayMinutes is already the total delay from now — the caller folds the meal carbTime into it.
        // Do NOT add carbTime again here or the eCarbs record lands carbTime minutes too late.
        val totalDelayMinutes = delayMinutes
        val eventTime = Calendar.getInstance().timeInMillis + (totalDelayMinutes * 60000L)
        if (forcedRecordOnly) {
            uel.log(
                action = Action.EXTENDED_CARBS,
                source = Sources.WizardDialog,
                note = notes,
                listValues = listOfNotNull(
                    ValueWithUnit.Timestamp(eventTime),
                    ValueWithUnit.Gram(eCarbsGrams),
                    ValueWithUnit.Minute(totalDelayMinutes).takeIf { totalDelayMinutes != 0 },
                    ValueWithUnit.Hour(durationHours).takeIf { durationHours != 0 }
                )
            )
            val detailedBolusInfo = DetailedBolusInfo().apply {
                carbs = eCarbsGrams.toDouble()
                carbsDuration = T.hours(durationHours.toLong()).msecs()
                carbsTimestamp = eventTime
                notes = this@BolusWizard.notes
            }
            appScope.launch {
                persistenceLayer.insertOrUpdateCarbs(
                    carbs = detailedBolusInfo.createCarbs(),
                    action = Action.EXTENDED_CARBS,
                    source = Sources.WizardDialog,
                    note = notes.ifEmpty { rh.gs(app.aaps.core.ui.R.string.record) }
                )
            }
        } else {
            // eCarbs delivery now rides the shared executor (one audited path).
            appScope.launch {
                wizardBolusExecutor.deliverECarbs(eCarbsGrams, eventTime, durationHours, totalDelayMinutes, notes, Sources.WizardDialog, onError)
            }
        }
    }

    private fun scheduleECarbsFromQuickWizardCompose(quickWizardEntry: QuickWizardEntry, onError: (String) -> Unit, forcedRecordOnly: Boolean = false) {
        val eCarbsYesNo = JsonHelper.safeGetInt(quickWizardEntry.storage, "useEcarbs", QuickWizardEntry.NO)
        if (eCarbsYesNo == QuickWizardEntry.YES) {
            val timeOffset = JsonHelper.safeGetInt(quickWizardEntry.storage, "time", 0)
            val duration = JsonHelper.safeGetInt(quickWizardEntry.storage, "duration", 0)
            val carbs2 = JsonHelper.safeGetInt(quickWizardEntry.storage, "carbs2", 0)

            val currentTime = Calendar.getInstance().timeInMillis
            val eventTime: Long = currentTime + (timeOffset * 60000)

            if (carbs2 > 0) {
                val buttonText = quickWizardEntry.storage.get("buttonText").toString()
                if (forcedRecordOnly) {
                    uel.log(
                        action = Action.EXTENDED_CARBS,
                        source = Sources.QuickWizard,
                        note = buttonText,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(eventTime),
                            ValueWithUnit.Gram(carbs2),
                            ValueWithUnit.Minute(timeOffset).takeIf { timeOffset != 0 },
                            ValueWithUnit.Hour(duration).takeIf { duration != 0 }
                        )
                    )
                    val detailedBolusInfo = DetailedBolusInfo().apply {
                        carbs = carbs2.toDouble()
                        notes = buttonText
                        carbsDuration = T.hours(duration.toLong()).msecs()
                        carbsTimestamp = eventTime
                    }
                    appScope.launch {
                        persistenceLayer.insertOrUpdateCarbs(
                            carbs = detailedBolusInfo.createCarbs(),
                            action = Action.EXTENDED_CARBS,
                            source = Sources.QuickWizard,
                            note = buttonText
                        )
                    }
                } else {
                    // eCarbs delivery now rides the shared executor (one audited path).
                    appScope.launch {
                        wizardBolusExecutor.deliverECarbs(carbs2, eventTime, duration, timeOffset, buttonText, Sources.QuickWizard, onError)
                    }
                }
            }
        }
    }
}
