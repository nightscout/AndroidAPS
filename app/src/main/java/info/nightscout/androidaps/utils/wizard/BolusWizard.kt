package info.nightscout.androidaps.utils.wizard

import android.content.Context
import android.content.Intent
import android.text.Spanned
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.BolusCalculatorResult
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.transactions.InsertOrUpdateBolusCalculatorResultTransaction
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.extensions.highValueToUnitsToString
import info.nightscout.androidaps.extensions.lowValueToUnitsToString
import info.nightscout.androidaps.interfaces.*
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatusProvider
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BolusWizard @Inject constructor(
    val injector: HasAndroidInjector
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var loop: Loop
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var carbTimer: CarbTimer
    @Inject lateinit var bolusTimer: BolusTimer
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    init {
        injector.androidInjector().inject(this)
    }

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
    var totalBeforePercentageAdjustment: Double = 0.0
        private set
    var carbsEquivalent: Double = 0.0
        private set
    var insulinAfterConstraints: Double = 0.0
        private set
    var calculatedPercentage: Double = 100.0
        private set
    var calculatedCorrection: Double = 0.0
        private set

    // Input
    lateinit var profile: Profile
    lateinit var profileName: String
    var tempTarget: TemporaryTarget? = null
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
    private var quickWizard: Boolean = true
    var usePercentage: Boolean = false

    fun doCalc(
        profile: Profile,
        profileName: String,
        tempTarget: TemporaryTarget?,
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
        quickWizard: Boolean = false
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
        this.quickWizard = quickWizard
        this.usePercentage = usePercentage
        this.totalPercentage = totalPercentage

        // Insulin from BG
        sens = Profile.fromMgdlToUnits(profile.getIsfMgdl(), profileFunction.getUnits())
        targetBGLow = Profile.fromMgdlToUnits(profile.getTargetLowMgdl(), profileFunction.getUnits())
        targetBGHigh = Profile.fromMgdlToUnits(profile.getTargetHighMgdl(), profileFunction.getUnits())
        if (useTT && tempTarget != null) {
            targetBGLow = Profile.fromMgdlToUnits(tempTarget.lowTarget, profileFunction.getUnits())
            targetBGHigh = Profile.fromMgdlToUnits(tempTarget.highTarget, profileFunction.getUnits())
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
                insulinFromTrend = Profile.fromMgdlToUnits(trend, profileFunction.getUnits()) * 3 / sens
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

        insulinFromBolusIOB = if (includeBolusIOB) -bolusIob.iob else 0.0
        insulinFromBasalIOB = if (includeBasalIOB) -basalIob.basaliob else 0.0

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
        calculatedTotalInsulin = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulinFromBolusIOB + insulinFromBasalIOB + insulinFromCorrection + insulinFromSuperBolus + insulinFromCOB

        var percentage = if (usePercentage) totalPercentage else percentageCorrection.toDouble()

        // Percentage adjustment
        totalBeforePercentageAdjustment = calculatedTotalInsulin
        if (calculatedTotalInsulin >= 0) {
            calculatedTotalInsulin = calculatedTotalInsulin * percentage / 100.0
            if (usePercentage)
                calcCorrectionWithConstraints()
            else
                calcPercentageWithConstraints()
            if (usePercentage)  //Should be updated after calcCorrectionWithConstraints and calcPercentageWithConstraints to have correct synthesis in WizardInfo
                this.percentageCorrection = Round.roundTo(totalPercentage, 1.0).toInt()
        } else {
            carbsEquivalent = (-calculatedTotalInsulin) * ic
            calculatedTotalInsulin = 0.0
            calculatedPercentage = percentageCorrection.toDouble()
            calculatedCorrection = 0.0
        }

        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep)

        insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(calculatedTotalInsulin)).value()

        aapsLogger.debug(this.toString())
        return this
    }

    private fun createBolusCalculatorResult(): BolusCalculatorResult {
        val unit = profileFunction.getUnits()
        return BolusCalculatorResult(
            timestamp = dateUtil.now(),
            targetBGLow = Profile.toMgdl(targetBGLow, unit),
            targetBGHigh = Profile.toMgdl(targetBGHigh, unit),
            isf = Profile.toMgdl(sens, unit),
            ic = ic,
            bolusIOB = insulinFromBolusIOB,
            wasBolusIOBUsed = includeBolusIOB,
            basalIOB = insulinFromBasalIOB,
            wasBasalIOBUsed = includeBasalIOB,
            glucoseValue = Profile.toMgdl(bg, unit),
            wasGlucoseUsed = useBg && bg > 0,
            glucoseDifference = bgDiff,
            glucoseInsulin = insulinFromBG,
            glucoseTrend = Profile.fromMgdlToUnits(trend, unit),
            wasTrendUsed = useTrend,
            trendInsulin = insulinFromTrend,
            cob = cob,
            wasCOBUsed = useCob,
            cobInsulin = insulinFromCOB,
            carbs = carbs.toDouble(),
            wereCarbsUsed = cob > 0,
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

    private fun confirmMessageAfterConstraints(advisor: Boolean): Spanned {

        val actions: LinkedList<String> = LinkedList()
        if (insulinAfterConstraints > 0) {
            val pct = if (percentageCorrection != 100) " ($percentageCorrection%)" else ""
            actions.add(rh.gs(R.string.bolus) + ": " + rh.gs(R.string.formatinsulinunits, insulinAfterConstraints).formatColor(rh, R.color.bolus) + pct)
        }
        if (carbs > 0 && !advisor) {
            var timeShift = ""
            if (carbTime > 0) {
                timeShift += " (+" + rh.gs(R.string.mins, carbTime) + ")"
            } else if (carbTime < 0) {
                timeShift += " (" + rh.gs(R.string.mins, carbTime) + ")"
            }
            actions.add(rh.gs(R.string.carbs) + ": " + rh.gs(R.string.format_carbs, carbs).formatColor(rh, R.color.carbs) + timeShift)
        }
        if (insulinFromCOB > 0) {
            actions.add(
                rh.gs(R.string.cobvsiob) + ": " + rh.gs(R.string.formatsignedinsulinunits, insulinFromBolusIOB + insulinFromBasalIOB + insulinFromCOB + insulinFromBG).formatColor(rh, R.color.cobAlert)
            )
            val absorptionRate = iobCobCalculator.ads.slowAbsorptionPercentage(60)
            if (absorptionRate > .25)
                actions.add(rh.gs(R.string.slowabsorptiondetected, rh.gc(R.color.cobAlert), (absorptionRate * 100).toInt()))
        }
        if (abs(insulinAfterConstraints - calculatedTotalInsulin) > activePlugin.activePump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
            actions.add(rh.gs(R.string.bolusconstraintappliedwarn, calculatedTotalInsulin, insulinAfterConstraints).formatColor(rh, R.color.warning))
        if (config.NSCLIENT && insulinAfterConstraints > 0)
            actions.add(rh.gs(R.string.bolusrecordedonly).formatColor(rh, R.color.warning))
        if (useAlarm && !advisor && carbs > 0 && carbTime > 0)
            actions.add(rh.gs(R.string.alarminxmin, carbTime).formatColor(rh, R.color.info))
        if (advisor)
            actions.add(rh.gs(R.string.advisoralarm).formatColor(rh, R.color.info))

        return HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions))
    }

    fun confirmAndExecute(ctx: Context) {
        if (calculatedTotalInsulin > 0.0 || carbs > 0.0) {
            if (accepted) {
                aapsLogger.debug(LTag.UI, "guarding: already accepted")
                return
            }
            accepted = true
            if (calculatedTotalInsulin > 0.0)
                bolusTimer.removeBolusReminder()
            if (carbs > 0.0)
                carbTimer.removeEatReminder()
            if (sp.getBoolean(R.string.key_usebolusadvisor, false) && Profile.toMgdl(bg, profile.units) > 180 && carbs > 0 && carbTime >= 0)
                OKDialog.showYesNoCancel(ctx, rh.gs(R.string.bolusadvisor), rh.gs(R.string.bolusadvisormessage),
                                         { bolusAdvisorProcessing(ctx) },
                                         { commonProcessing(ctx) }
                )
            else
                commonProcessing(ctx)
        } else {
            OKDialog.show(ctx, rh.gs(R.string.boluswizard), rh.gs(R.string.no_action_selected))
        }
    }

    private fun bolusAdvisorProcessing(ctx: Context) {
        val confirmMessage = confirmMessageAfterConstraints(advisor = true)
        OKDialog.showConfirmation(ctx, rh.gs(R.string.boluswizard), confirmMessage, {
            DetailedBolusInfo().apply {
                eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                insulin = insulinAfterConstraints
                carbs = 0.0
                context = ctx
                mgdlGlucose = Profile.toMgdl(bg, profile.units)
                glucoseType = DetailedBolusInfo.MeterType.MANUAL
                carbTime = 0
                bolusCalculatorResult = createBolusCalculatorResult()
                notes = this@BolusWizard.notes
                uel.log(
                    Action.BOLUS_ADVISOR,
                    if (quickWizard) Sources.QuickWizard else Sources.WizardDialog,
                    notes,
                    ValueWithUnit.TherapyEventType(eventType.toDBbEventType()),
                    ValueWithUnit.Insulin(insulinAfterConstraints)
                )
                if (insulin > 0) {
                    commandQueue.bolus(this, object : Callback() {
                        override fun run() {
                            if (!result.success) {
                                ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                            } else
                                carbTimer.scheduleEatReminder()
                        }
                    })
                }
            }
        })
    }

    fun explainShort(): String {
        var message = rh.gs(R.string.wizard_explain_calc, ic, sens)
        message += "\n" + rh.gs(R.string.wizard_explain_carbs, insulinFromCarbs)
        if (useTT && tempTarget != null) {
            val tt = if (tempTarget?.lowTarget == tempTarget?.highTarget) tempTarget?.lowValueToUnitsToString(profile.units)
            else rh.gs(R.string.wizard_explain_tt_to, tempTarget?.lowValueToUnitsToString(profile.units), tempTarget?.highValueToUnitsToString(profile.units))
            message += "\n" + rh.gs(R.string.wizard_explain_tt, tt)
        }
        if (useCob) message += "\n" + rh.gs(R.string.wizard_explain_cob, cob, insulinFromCOB)
        if (useBg) message += "\n" + rh.gs(R.string.wizard_explain_bg, insulinFromBG)
        if (includeBolusIOB) message += "\n" + rh.gs(R.string.wizard_explain_bolus_iob, insulinFromBolusIOB)
        if (includeBasalIOB) message += "\n" + rh.gs(R.string.wizard_explain_basal_iob, insulinFromBasalIOB)
        if (useTrend) message += "\n" + rh.gs(R.string.wizard_explain_trend, insulinFromTrend)
        if (useSuperBolus) message += "\n" + rh.gs(R.string.wizard_explain_superbolus, insulinFromSuperBolus)
        if (percentageCorrection != 100) {
            message += "\n" + rh.gs(R.string.wizard_explain_percent, totalBeforePercentageAdjustment, percentageCorrection, calculatedTotalInsulin)
        }
        return message
    }

    private fun commonProcessing(ctx: Context) {
        val profile = profileFunction.getProfile() ?: return
        val pump = activePlugin.activePump

        val confirmMessage = confirmMessageAfterConstraints(advisor = false)
        OKDialog.showConfirmation(ctx, rh.gs(R.string.boluswizard), confirmMessage, {
            if (insulinAfterConstraints > 0 || carbs > 0) {
                if (useSuperBolus) {
                    uel.log(Action.SUPERBOLUS_TBR, Sources.WizardDialog)
                    if ((loop as PluginBase).isEnabled()) {
                        loop.goToZeroTemp(2 * 60, profile, OfflineEvent.Reason.SUPER_BOLUS)
                        rxBus.send(EventRefreshOverview("WizardDialog"))
                    }

                    if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                        commandQueue.tempBasalAbsolute(0.0, 120, true, profile, PumpSync.TemporaryBasalType.NORMAL, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                                }
                            }
                        })
                    } else {
                        commandQueue.tempBasalPercent(0, 120, true, profile, PumpSync.TemporaryBasalType.NORMAL, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    val i = Intent(ctx, ErrorHelperActivity::class.java)
                                    i.putExtra(ErrorHelperActivity.SOUND_ID, R.raw.boluserror)
                                    i.putExtra(ErrorHelperActivity.STATUS, result.comment)
                                    i.putExtra(ErrorHelperActivity.TITLE, rh.gs(R.string.tempbasaldeliveryerror))
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    ctx.startActivity(i)
                                }
                            }
                        })
                    }
                }
                DetailedBolusInfo().apply {
                    eventType = DetailedBolusInfo.EventType.BOLUS_WIZARD
                    insulin = insulinAfterConstraints
                    carbs = this@BolusWizard.carbs.toDouble()
                    context = ctx
                    mgdlGlucose = Profile.toMgdl(bg, profile.units)
                    glucoseType = DetailedBolusInfo.MeterType.MANUAL
                    carbsTimestamp = dateUtil.now() + T.mins(this@BolusWizard.carbTime.toLong()).msecs()
                    bolusCalculatorResult = createBolusCalculatorResult()
                    notes = this@BolusWizard.notes
                    if (insulin > 0 || carbs > 0) {
                        val action = when {
                            insulinAfterConstraints.equals(0.0) -> Action.CARBS
                            carbs.equals(0.0)                   -> Action.BOLUS
                            else                                -> Action.TREATMENT
                        }
                        uel.log(action, if (quickWizard) Sources.QuickWizard else Sources.WizardDialog,
                                notes,
                                ValueWithUnit.TherapyEventType(eventType.toDBbEventType()),
                                ValueWithUnit.Insulin(insulinAfterConstraints).takeIf { insulinAfterConstraints != 0.0 },
                                ValueWithUnit.Gram(this@BolusWizard.carbs).takeIf { this@BolusWizard.carbs != 0 },
                                ValueWithUnit.Minute(carbTime).takeIf { carbTime != 0 })
                        commandQueue.bolus(this, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                                }
                            }
                        })
                    }
                    disposable += repository.runTransactionForResult(InsertOrUpdateBolusCalculatorResultTransaction(bolusCalculatorResult!!))
                        .subscribe(
                            { result -> result.inserted.forEach { inserted -> aapsLogger.debug(LTag.DATABASE, "Inserted bolusCalculatorResult $inserted") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while saving bolusCalculatorResult", it) }
                        )

                }
                if (useAlarm && carbs > 0 && carbTime > 0) {
                    carbTimer.scheduleReminder(T.mins(carbTime.toLong()).secs().toInt())
                }
            }
        })
    }

    private fun calcPercentageWithConstraints() {
        calculatedPercentage = 100.0
        if (totalBeforePercentageAdjustment != insulinFromCorrection)
            calculatedPercentage = calculatedTotalInsulin / (totalBeforePercentageAdjustment - insulinFromCorrection) * 100
        calculatedPercentage = max(calculatedPercentage, 10.0)
        calculatedPercentage = min(calculatedPercentage, 250.0)
    }

    private fun calcCorrectionWithConstraints() {
        calculatedCorrection = totalBeforePercentageAdjustment * totalPercentage / percentageCorrection - totalBeforePercentageAdjustment
        //Apply constraints
        calculatedCorrection = min(constraintChecker.getMaxBolusAllowed().value(), calculatedCorrection)
        calculatedCorrection = max(-constraintChecker.getMaxBolusAllowed().value(), calculatedCorrection)
    }

}
