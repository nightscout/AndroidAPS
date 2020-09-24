package info.nightscout.androidaps.utils.wizard

import android.content.Context
import android.content.Intent
import android.text.Spanned
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class BolusWizard @Inject constructor(
    val injector: HasAndroidInjector
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var context: Context
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var loopPlugin: LoopPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var config: Config

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
    var insulinFromBasalsIOB = 0.0
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

    // Input
    lateinit var profile: Profile
    lateinit var profileName: String
    var tempTarget: TempTarget? = null
    var carbs: Int = 0
    var cob: Double = 0.0
    var bg: Double = 0.0
    var correction: Double = 0.0
    private var percentageCorrection: Double = 0.0
    private var useBg: Boolean = false
    private var useCob: Boolean = false
    private var includeBolusIOB: Boolean = false
    private var includeBasalIOB: Boolean = false
    private var useSuperBolus: Boolean = false
    private var useTT: Boolean = false
    private var useTrend: Boolean = false
    var notes: String = ""
    var carbTime: Int = 0

    @JvmOverloads
    fun doCalc(profile: Profile,
               profileName: String,
               tempTarget: TempTarget?,
               carbs: Int,
               cob: Double,
               bg: Double,
               correction: Double,
               percentageCorrection: Double = 100.0,
               useBg: Boolean,
               useCob: Boolean,
               includeBolusIOB: Boolean,
               includeBasalIOB: Boolean,
               useSuperBolus: Boolean,
               useTT: Boolean,
               useTrend: Boolean,
               notes: String = "",
               carbTime: Int = 0
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
        this.notes = notes
        this.carbTime = carbTime

        // Insulin from BG
        sens = Profile.fromMgdlToUnits(profile.isfMgdl, profileFunction.getUnits())
        targetBGLow = Profile.fromMgdlToUnits(profile.targetLowMgdl, profileFunction.getUnits())
        targetBGHigh = Profile.fromMgdlToUnits(profile.targetHighMgdl, profileFunction.getUnits())
        if (useTT && tempTarget != null) {
            targetBGLow = Profile.fromMgdlToUnits(tempTarget.low, profileFunction.getUnits())
            targetBGHigh = Profile.fromMgdlToUnits(tempTarget.high, profileFunction.getUnits())
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
        glucoseStatus = GlucoseStatus(injector).getGlucoseStatusData()
        glucoseStatus?.let {
            if (useTrend) {
                trend = it.short_avgdelta
                insulinFromTrend = Profile.fromMgdlToUnits(trend, profileFunction.getUnits()) * 3 / sens
            }
        }

        // Insulin from carbs
        ic = profile.ic
        insulinFromCarbs = carbs / ic
        insulinFromCOB = if (useCob) (cob / ic) else 0.0

        // Insulin from IOB
        // IOB calculation
        activePlugin.activeTreatments.updateTotalIOBTreatments()
        val bolusIob = activePlugin.activeTreatments.lastCalculationTreatments.round()
        activePlugin.activeTreatments.updateTotalIOBTempBasals()
        val basalIob = activePlugin.activeTreatments.lastCalculationTempBasals.round()

        insulinFromBolusIOB = if (includeBolusIOB) -bolusIob.iob else 0.0
        insulinFromBasalsIOB = if (includeBasalIOB) -basalIob.basaliob else 0.0

        // Insulin from correction
        insulinFromCorrection = correction

        // Insulin from superbolus for 2h. Get basal rate now and after 1h
        if (useSuperBolus) {
            insulinFromSuperBolus = profile.basal
            var timeAfter1h = System.currentTimeMillis()
            timeAfter1h += T.hours(1).msecs()
            insulinFromSuperBolus += profile.getBasal(timeAfter1h)
        }

        // Total
        calculatedTotalInsulin = insulinFromBG + insulinFromTrend + insulinFromCarbs + insulinFromBolusIOB + insulinFromBasalsIOB + insulinFromCorrection + insulinFromSuperBolus + insulinFromCOB

        // Percentage adjustment
        totalBeforePercentageAdjustment = calculatedTotalInsulin
        if (calculatedTotalInsulin > 0) {
            calculatedTotalInsulin = calculatedTotalInsulin * percentageCorrection / 100.0
        }

        if (calculatedTotalInsulin < 0) {
            carbsEquivalent = (-calculatedTotalInsulin) * ic
            calculatedTotalInsulin = 0.0
        }

        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        calculatedTotalInsulin = Round.roundTo(calculatedTotalInsulin, bolusStep)

        insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(calculatedTotalInsulin)).value()

        aapsLogger.debug(this.toString())
        return this
    }

    private fun nsJSON(): JSONObject {
        val bolusCalcJSON = JSONObject()
        try {
            bolusCalcJSON.put("profile", profileName)
            bolusCalcJSON.put("notes", notes)
            bolusCalcJSON.put("eventTime", DateUtil.toISOString(Date()))
            bolusCalcJSON.put("targetBGLow", targetBGLow)
            bolusCalcJSON.put("targetBGHigh", targetBGHigh)
            bolusCalcJSON.put("isf", sens)
            bolusCalcJSON.put("ic", ic)
            bolusCalcJSON.put("iob", -(insulinFromBolusIOB + insulinFromBasalsIOB))
            bolusCalcJSON.put("bolusiob", insulinFromBolusIOB)
            bolusCalcJSON.put("basaliob", insulinFromBasalsIOB)
            bolusCalcJSON.put("bolusiobused", includeBolusIOB)
            bolusCalcJSON.put("basaliobused", includeBasalIOB)
            bolusCalcJSON.put("bg", bg)
            bolusCalcJSON.put("insulinbg", insulinFromBG)
            bolusCalcJSON.put("insulinbgused", useBg)
            bolusCalcJSON.put("bgdiff", bgDiff)
            bolusCalcJSON.put("insulincarbs", insulinFromCarbs)
            bolusCalcJSON.put("carbs", carbs)
            bolusCalcJSON.put("cob", cob)
            bolusCalcJSON.put("cobused", useCob)
            bolusCalcJSON.put("insulincob", insulinFromCOB)
            bolusCalcJSON.put("othercorrection", correction)
            bolusCalcJSON.put("insulinsuperbolus", insulinFromSuperBolus)
            bolusCalcJSON.put("insulintrend", insulinFromTrend)
            bolusCalcJSON.put("insulin", calculatedTotalInsulin)
            bolusCalcJSON.put("superbolusused", useSuperBolus)
            bolusCalcJSON.put("insulinsuperbolus", insulinFromSuperBolus)
            bolusCalcJSON.put("trendused", useTrend)
            bolusCalcJSON.put("insulintrend", insulinFromTrend)
            bolusCalcJSON.put("trend", trend)
            bolusCalcJSON.put("ttused", useTT)
            bolusCalcJSON.put("percentageCorrection", percentageCorrection)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return bolusCalcJSON
    }

    private fun confirmMessageAfterConstraints(pump: PumpInterface): Spanned {

        val actions: LinkedList<String> = LinkedList()
        if (insulinAfterConstraints > 0) {
            val pct = if (percentageCorrection != 100.0) " (" + percentageCorrection.toInt() + "%)" else ""
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + resourceHelper.gs(R.string.formatinsulinunits, insulinAfterConstraints).formatColor(resourceHelper, R.color.bolus) + pct)
        }
        if (carbs > 0) {
            var timeShift = ""
            if (carbTime > 0) {
                timeShift += " (+" + resourceHelper.gs(R.string.mins, carbTime) + ")"
            } else if (carbTime < 0) {
                timeShift += " (-" + resourceHelper.gs(R.string.mins, carbTime) + ")"
            }
            actions.add(resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, carbs).formatColor(resourceHelper, R.color.carbs) + timeShift)
        }
        if (insulinFromCOB > 0) {
            actions.add(resourceHelper.gs(R.string.cobvsiob) + ": " + resourceHelper.gs(R.string.formatsignedinsulinunits, insulinFromBolusIOB + insulinFromBasalsIOB + insulinFromCOB + insulinFromBG).formatColor(resourceHelper, R.color.cobAlert))
            val absorptionRate = iobCobCalculatorPlugin.slowAbsorptionPercentage(60)
            if (absorptionRate > .25)
                actions.add(resourceHelper.gs(R.string.slowabsorptiondetected, resourceHelper.gc(R.color.cobAlert), (absorptionRate * 100).toInt()))
        }
        if (abs(insulinAfterConstraints - calculatedTotalInsulin) > pump.pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
             actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarn, calculatedTotalInsulin, insulinAfterConstraints).formatColor(resourceHelper, R.color.warning))
        if (config.NSCLIENT)
            actions.add(resourceHelper.gs(R.string.bolusrecordedonly).formatColor(resourceHelper, R.color.warning))

        return HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions))
    }

    fun confirmAndExecute(context: Context) {
        val profile = profileFunction.getProfile() ?: return
        val pump = activePlugin.activePump

        if (calculatedTotalInsulin > 0.0 || carbs > 0.0) {
            if (accepted) {
                aapsLogger.debug(LTag.UI, "guarding: already accepted")
                return
            }
            accepted = true

            val confirmMessage = confirmMessageAfterConstraints(pump)

            OKDialog.showConfirmation(context, resourceHelper.gs(R.string.boluswizard), confirmMessage, Runnable {
                if (insulinAfterConstraints > 0 || carbs > 0) {
                    if (useSuperBolus) {
                        aapsLogger.debug("USER ENTRY: SUPERBOLUS TBR")
                        if (loopPlugin.isEnabled(PluginType.LOOP)) {
                            loopPlugin.superBolusTo(System.currentTimeMillis() + 2 * 60L * 60 * 1000)
                            rxBus.send(EventRefreshOverview("WizardDialog"))
                        }

                        if (pump.pumpDescription.tempBasalStyle == PumpDescription.ABSOLUTE) {
                            commandQueue.tempBasalAbsolute(0.0, 120, true, profile, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        val i = Intent(context, ErrorHelperActivity::class.java)
                                        i.putExtra("soundid", R.raw.boluserror)
                                        i.putExtra("status", result.comment)
                                        i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(i)
                                    }
                                }
                            })
                        } else {

                            commandQueue.tempBasalPercent(0, 120, true, profile, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        val i = Intent(context, ErrorHelperActivity::class.java)
                                        i.putExtra("soundid", R.raw.boluserror)
                                        i.putExtra("status", result.comment)
                                        i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(i)
                                    }
                                }
                            })
                        }
                    }
                    val detailedBolusInfo = DetailedBolusInfo()
                    detailedBolusInfo.eventType = CareportalEvent.BOLUSWIZARD
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbs.toDouble()
                    detailedBolusInfo.context = context
                    detailedBolusInfo.glucose = bg
                    detailedBolusInfo.glucoseType = "Manual"
                    detailedBolusInfo.carbTime = carbTime
                    detailedBolusInfo.boluscalc = nsJSON()
                    detailedBolusInfo.source = Source.USER
                    detailedBolusInfo.notes = notes
                    aapsLogger.debug("USER ENTRY: BOLUS insulin $insulinAfterConstraints carbs: $carbs")
                    if (detailedBolusInfo.insulin > 0 || pump.pumpDescription.storesCarbInfo) {
                        commandQueue.bolus(detailedBolusInfo, object : Callback() {
                            override fun run() {
                                if (!result.success) {
                                    val i = Intent(context, ErrorHelperActivity::class.java)
                                    i.putExtra("soundid", R.raw.boluserror)
                                    i.putExtra("status", result.comment)
                                    i.putExtra("title", resourceHelper.gs(R.string.treatmentdeliveryerror))
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(i)
                                }
                            }
                        })
                    } else {
                        activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                    }
                }
            })
        }
    }
}
