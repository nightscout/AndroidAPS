package info.nightscout.androidaps.plugins.general.wear

import android.app.NotificationManager
import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.TotalDailyDose
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import info.nightscout.androidaps.database.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import info.nightscout.androidaps.extensions.total
import info.nightscout.androidaps.extensions.valueToUnits
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.wear.events.EventWearConfirmAction
import info.nightscout.androidaps.plugins.general.wear.events.EventWearInitiateAction
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.BolusWizard
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.min

@Suppress("SpellCheckingInspection")
@Singleton
class ActionStringHandler @Inject constructor(
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val resourceHelper: ResourceHelper,
    private val injector: HasAndroidInjector,
    private val context: Context,
    private val constraintChecker: ConstraintChecker,
    private val profileFunction: ProfileFunction,
    private val loopPlugin: LoopPlugin,
    private val wearPlugin: WearPlugin,
    private val fabricPrivacy: FabricPrivacy,
    private val commandQueue: CommandQueueProvider,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val localInsightPlugin: LocalInsightPlugin,
    private val danaRPlugin: DanaRPlugin,
    private val danaRKoreanPlugin: DanaRKoreanPlugin,
    private val danaRv2Plugin: DanaRv2Plugin,
    private val danaRSPlugin: DanaRSPlugin,
    private val danaPump: DanaPump,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val repository: AppRepository,
    private val uel: UserEntryLogger
) {

    private val timeout = 65 * 1000
    private var lastSentTimestamp: Long = 0
    private var lastConfirmActionString: String? = null
    private var lastBolusWizard: BolusWizard? = null

    private val disposable = CompositeDisposable()

    fun setup() {
        disposable += rxBus
            .toObservable(EventWearInitiateAction::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ handleInitiate(it.action) }, fabricPrivacy::logException)

        disposable += rxBus
            .toObservable(EventWearConfirmAction::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ handleConfirmation(it.action) }, fabricPrivacy::logException)
    }

    fun tearDown() {
        disposable.clear()
    }

    @Synchronized
    private fun handleInitiate(actionString: String) {
        if (!sp.getBoolean(R.string.key_wear_control, false)) return
        lastBolusWizard = null
        var rTitle = "CONFIRM" //TODO: i18n
        var rMessage = ""
        var rAction = ""
        // do the parsing and check constraints
        val act = actionString.split("\\s+".toRegex()).toTypedArray()
        if ("fillpreset" == act[0]) { ///////////////////////////////////// PRIME/FILL
            val amount: Double = when {
                "1" == act[1] -> sp.getDouble("fill_button1", 0.3)
                "2" == act[1] -> sp.getDouble("fill_button2", 0.0)
                "3" == act[1] -> sp.getDouble("fill_button3", 0.0)
                else          -> return
            }
            val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
            rMessage += resourceHelper.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U"
            if (insulinAfterConstraints - amount != 0.0) rMessage += "\n" + resourceHelper.gs(R.string.constraintapllied)
            rAction += "fill $insulinAfterConstraints"
        } else if ("fill" == act[0]) { ////////////////////////////////////////////// PRIME/FILL
            val amount = SafeParse.stringToDouble(act[1])
            val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
            rMessage += resourceHelper.gs(R.string.primefill) + ": " + insulinAfterConstraints + "U"
            if (insulinAfterConstraints - amount != 0.0) rMessage += "\n" + resourceHelper.gs(R.string.constraintapllied)
            rAction += "fill $insulinAfterConstraints"
        } else if ("bolus" == act[0]) { ////////////////////////////////////////////// BOLUS
            val insulin = SafeParse.stringToDouble(act[1])
            val carbs = SafeParse.stringToInt(act[2])
            val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
            val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
            rMessage += resourceHelper.gs(R.string.bolus) + ": " + insulinAfterConstraints + "U\n"
            rMessage += resourceHelper.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g"
            if (insulinAfterConstraints - insulin != 0.0 || carbsAfterConstraints - carbs != 0) {
                rMessage += "\n" + resourceHelper.gs(R.string.constraintapllied)
            }
            rAction += "bolus $insulinAfterConstraints $carbsAfterConstraints"
        } else if ("temptarget" == act[0]) { ///////////////////////////////////////////////////////// TEMPTARGET
            val isMGDL = java.lang.Boolean.parseBoolean(act[1])
            if (profileFunction.getUnits() == GlucoseUnit.MGDL != isMGDL) {
                sendError("Different units used on watch and phone!")
                return
            }
            val duration = SafeParse.stringToInt(act[2])
            if (duration == 0) {
                rMessage += "Zero-Temp-Target - cancelling running Temp-Targets?"
                rAction = "temptarget true 0 0 0"
            } else {
                var low = SafeParse.stringToDouble(act[3])
                var high = SafeParse.stringToDouble(act[4])
                if (!isMGDL) {
                    low *= Constants.MMOLL_TO_MGDL
                    high *= Constants.MMOLL_TO_MGDL
                }
                if (low < HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0] || low > HardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]) {
                    sendError("Min-BG out of range!")
                    return
                }
                if (high < HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0] || high > HardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]) {
                    sendError("Max-BG out of range!")
                    return
                }
                rMessage += "Temptarget:\nMin: " + act[3] + "\nMax: " + act[4] + "\nDuration: " + act[2]
                rAction = actionString
            }
        } else if ("status" == act[0]) { ////////////////////////////////////////////// STATUS
            rTitle = "STATUS"
            rAction = "statusmessage"
            if ("pump" == act[1]) {
                rTitle += " PUMP"
                rMessage = pumpStatus
            } else if ("loop" == act[1]) {
                rTitle += " LOOP"
                rMessage = "TARGETS:\n$targetsStatus"
                rMessage += "\n\n" + loopStatus
                rMessage += "\n\nOAPS RESULT:\n$oAPSResultStatus"
            }
        } else if ("wizard" == act[0]) {
            sendError("Update APP on Watch!")
            return
        } else if ("wizard2" == act[0]) { ////////////////////////////////////////////// WIZARD
            val carbsBeforeConstraints = SafeParse.stringToInt(act[1])
            val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbsBeforeConstraints)).value()
            if (carbsAfterConstraints - carbsBeforeConstraints != 0) {
                sendError("Carb constraint violation!")
                return
            }
            val useBG = sp.getBoolean(R.string.key_wearwizard_bg, true)
            val useTT = sp.getBoolean(R.string.key_wearwizard_tt, false)
            val useBolusIOB = sp.getBoolean(R.string.key_wearwizard_bolusiob, true)
            val useBasalIOB = sp.getBoolean(R.string.key_wearwizard_basaliob, true)
            val useCOB = sp.getBoolean(R.string.key_wearwizard_cob, true)
            val useTrend = sp.getBoolean(R.string.key_wearwizard_trend, false)
            val percentage = act[2].toInt()
            val profile = profileFunction.getProfile()
            val profileName = profileFunction.getProfileName()
            if (profile == null) {
                sendError("No profile found!")
                return
            }
            val bgReading = iobCobCalculator.ads.actualBg()
            if (bgReading == null) {
                sendError("No recent BG to base calculation on!")
                return
            }
            val cobInfo = iobCobCalculator.getCobInfo(false, "Wizard wear")
            if (cobInfo.displayCob == null) {
                sendError("Unknown COB! BG reading missing or recent app restart?")
                return
            }
            val format = DecimalFormat("0.00")
            val formatInt = DecimalFormat("0")
            val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
            val tempTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null

            val bolusWizard = BolusWizard(injector).doCalc(profile, profileName, tempTarget,
                carbsAfterConstraints, if (cobInfo.displayCob != null) cobInfo.displayCob!! else 0.0, bgReading.valueToUnits(profileFunction.getUnits()),
                0.0, percentage, useBG, useCOB, useBolusIOB, useBasalIOB, false, useTT, useTrend, false)
            if (abs(bolusWizard.insulinAfterConstraints - bolusWizard.calculatedTotalInsulin) >= 0.01) {
                sendError("Insulin constraint violation!" +
                    "\nCannot deliver " + format.format(bolusWizard.calculatedTotalInsulin) + "!")
                return
            }
            if (bolusWizard.calculatedTotalInsulin <= 0 && bolusWizard.carbs <= 0) {
                rAction = "info"
                rTitle = "INFO"
            } else {
                rAction = actionString
            }
            rMessage += "Carbs: " + bolusWizard.carbs + "g"
            rMessage += "\nBolus: " + format.format(bolusWizard.calculatedTotalInsulin) + "U"
            rMessage += "\n_____________"
            rMessage += "\nCalc (IC:" + DecimalFormatter.to1Decimal(bolusWizard.ic) + ", " + "ISF:" + DecimalFormatter.to1Decimal(bolusWizard.sens) + "): "
            rMessage += "\nFrom Carbs: " + format.format(bolusWizard.insulinFromCarbs) + "U"
            if (useCOB) rMessage += "\nFrom" + formatInt.format(cobInfo.displayCob) + "g COB : " + format.format(bolusWizard.insulinFromCOB) + "U"
            if (useBG) rMessage += "\nFrom BG: " + format.format(bolusWizard.insulinFromBG) + "U"
            if (useBolusIOB) rMessage += "\nBolus IOB: " + format.format(bolusWizard.insulinFromBolusIOB) + "U"
            if (useBasalIOB) rMessage += "\nBasal IOB: " + format.format(bolusWizard.insulinFromBasalIOB) + "U"
            if (useTrend) rMessage += "\nFrom 15' trend: " + format.format(bolusWizard.insulinFromTrend) + "U"
            if (percentage != 100) {
                rMessage += "\nPercentage: " + format.format(bolusWizard.totalBeforePercentageAdjustment) + "U * " + percentage + "% -> ~" + format.format(bolusWizard.calculatedTotalInsulin) + "U"
            }
            lastBolusWizard = bolusWizard
        } else if ("opencpp" == act[0]) {
            val activeProfileSwitch = repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet()
            if (activeProfileSwitch is ValueWrapper.Existing) { // read CPP values
                rTitle = "opencpp"
                rMessage = "opencpp"
                rAction = "opencpp" + " " + activeProfileSwitch.value.originalPercentage + " " + activeProfileSwitch.value.originalTimeshift
            } else {
                sendError("No active profile switch!")
                return
            }
        } else if ("cppset" == act[0]) {
            val activeProfileSwitch = repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet()
            if (activeProfileSwitch is ValueWrapper.Existing) {
                rMessage = "CPP:" + "\n\n" +
                    "Timeshift: " + act[1] + "\n" +
                    "Percentage: " + act[2] + "%"
                rAction = actionString
            } else { // read CPP values
                sendError("No active profile switch!")
                return
            }
        } else if ("tddstats" == act[0]) {
            val activePump = activePlugin.activePump
            // check if DB up to date
            val dummies: MutableList<TotalDailyDose> = LinkedList()
            val historyList = getTDDList(dummies)
            if (isOldData(historyList)) {
                rTitle = "TDD"
                rAction = "statusmessage"
                rMessage = "OLD DATA - "
                //if pump is not busy: try to fetch data
                if (activePump.isBusy()) {
                    rMessage += resourceHelper.gs(R.string.pumpbusy)
                } else {
                    rMessage += "trying to fetch data from pump."
                    commandQueue.loadTDDs(object : Callback() {
                        override fun run() {
                            val dummies1: MutableList<TotalDailyDose> = LinkedList()
                            val historyList1 = getTDDList(dummies1)
                            if (isOldData(historyList1)) {
                                sendStatusMessage("TDD: Still old data! Cannot load from pump.\n" + generateTDDMessage(historyList1, dummies1))
                            } else {
                                sendStatusMessage(generateTDDMessage(historyList1, dummies1))
                            }
                        }
                    })
                }
            } else { // if up to date: prepare, send (check if CPP is activated -> add CPP stats)
                rTitle = "TDD"
                rAction = "statusmessage"
                rMessage = generateTDDMessage(historyList, dummies)
            }
        } else if ("ecarbs" == act[0]) { ////////////////////////////////////////////// ECARBS
            val carbs = SafeParse.stringToInt(act[1])
            val starttime = SafeParse.stringToInt(act[2])
            val duration = SafeParse.stringToInt(act[3])
            val starttimestamp = System.currentTimeMillis() + starttime * 60 * 1000
            val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
            rMessage += resourceHelper.gs(R.string.carbs) + ": " + carbsAfterConstraints + "g"
            rMessage += "\n" + resourceHelper.gs(R.string.time) + ": " + dateUtil.timeString(starttimestamp)
            rMessage += "\n" + resourceHelper.gs(R.string.duration) + ": " + duration + "h"
            if (carbsAfterConstraints - carbs != 0) {
                rMessage += "\n" + resourceHelper.gs(R.string.constraintapllied)
            }
            if (carbsAfterConstraints <= 0) {
                sendError("Carbs = 0! No action taken!")
                return
            }
            rAction += "ecarbs $carbsAfterConstraints $starttimestamp $duration"
        } else if ("changeRequest" == act[0]) { ////////////////////////////////////////////// CHANGE REQUEST
            rTitle = resourceHelper.gs(R.string.openloop_newsuggestion)
            rAction = "changeRequest"
            loopPlugin.lastRun?.let {
                rMessage += it.constraintsProcessed
                wearPlugin.requestChangeConfirmation(rTitle, rMessage, rAction)
                lastSentTimestamp = System.currentTimeMillis()
                lastConfirmActionString = rAction
            }
            return
        } else if ("cancelChangeRequest" == act[0]) { ////////////////////////////////////////////// CANCEL CHANGE REQUEST NOTIFICATION
            rAction = "cancelChangeRequest"
            wearPlugin.requestNotificationCancel(rAction)
            return
        } else return
        // send result
        wearPlugin.requestActionConfirmation(rTitle, rMessage, rAction)
        lastSentTimestamp = System.currentTimeMillis()
        lastConfirmActionString = rAction
    }

    private fun generateTDDMessage(historyList: MutableList<TotalDailyDose>, dummies: MutableList<TotalDailyDose>): String {
        val profile = profileFunction.getProfile() ?: return "No profile loaded :("
        if (historyList.isEmpty()) {
            return "No history data!"
        }
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        var message = ""
        val refTDD = profile.baseBasalSum() * 2
        val pump = activePlugin.activePump
        if (df.format(Date(historyList[0].timestamp)) == df.format(Date())) {
            val tdd = historyList[0].total
            historyList.removeAt(0)
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n"
            message += "\n"
        } else if (pump is DanaRPlugin) {
            val tdd = danaPump.dailyTotalUnits
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n"
            message += "\n"
        }
        var weighted03 = 0.0
        var weighted05 = 0.0
        var weighted07 = 0.0
        historyList.reverse()
        for ((i, record) in historyList.withIndex()) {
            val tdd = record.total
            if (i == 0) {
                weighted03 = tdd
                weighted05 = tdd
                weighted07 = tdd
            } else {
                weighted07 = weighted07 * 0.3 + tdd * 0.7
                weighted05 = weighted05 * 0.5 + tdd * 0.5
                weighted03 = weighted03 * 0.7 + tdd * 0.3
            }
        }
        message += "weighted:\n"
        message += "0.3: " + DecimalFormatter.to2Decimal(weighted03) + "U " + (DecimalFormatter.to0Decimal(100 * weighted03 / refTDD) + "%") + "\n"
        message += "0.5: " + DecimalFormatter.to2Decimal(weighted05) + "U " + (DecimalFormatter.to0Decimal(100 * weighted05 / refTDD) + "%") + "\n"
        message += "0.7: " + DecimalFormatter.to2Decimal(weighted07) + "U " + (DecimalFormatter.to0Decimal(100 * weighted07 / refTDD) + "%") + "\n"
        message += "\n"
        historyList.reverse()
        //add TDDs:
        for (record in historyList) {
            val tdd = record.total
            message += df.format(Date(record.timestamp)) + " " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + (if (dummies.contains(record)) "x" else "") + "\n"
        }
        return message
    }

    private fun isOldData(historyList: List<TotalDailyDose>): Boolean {
        val activePump = activePlugin.activePump
        val startsYesterday = activePump === danaRPlugin || activePump === danaRSPlugin || activePump === danaRv2Plugin || activePump === danaRKoreanPlugin || activePump === localInsightPlugin
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].timestamp)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }

    private fun getTDDList(returnDummies: MutableList<TotalDailyDose>): MutableList<TotalDailyDose> {
        var historyList = repository.getLastTotalDailyDoses(10, false).blockingGet().toMutableList()
        //var historyList = databaseHelper.getTDDs().toMutableList()
        historyList = historyList.subList(0, min(10, historyList.size))
        //fill single gaps - only needed for Dana*R data
        val dummies: MutableList<TotalDailyDose> = returnDummies
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1.timestamp)) != df.format(Date(elem2.timestamp + 25 * 60 * 60 * 1000))) {
                val dummy = TotalDailyDose(timestamp = elem1.timestamp - T.hours(24).msecs(), bolusAmount = elem1.bolusAmount / 2, basalAmount = elem1.basalAmount / 2)
                dummies.add(dummy)
                elem1.basalAmount /= 2.0
                elem1.bolusAmount /= 2.0
            }
        }
        historyList.addAll(dummies)
        historyList.sortWith { lhs, rhs -> (rhs.timestamp - lhs.timestamp).toInt() }
        return historyList
    }

    private val pumpStatus: String
        get() = activePlugin.activePump.shortStatus(false)

    // decide if enabled/disabled closed/open; what Plugin as APS?
    private val loopStatus: String
        get() {
            var ret = ""
            // decide if enabled/disabled closed/open; what Plugin as APS?
            if (loopPlugin.isEnabled(loopPlugin.getType())) {
                ret += if (constraintChecker.isClosedLoopAllowed().value()) {
                    "CLOSED LOOP\n"
                } else {
                    "OPEN LOOP\n"
                }
                val aps = activePlugin.activeAPS
                ret += "APS: " + (aps as PluginBase).name
                val lastRun = loopPlugin.lastRun
                if (lastRun != null) {
                    ret += "\nLast Run: " + dateUtil.timeString(lastRun.lastAPSRun)
                    if (lastRun.lastTBREnact != 0L) ret += "\nLast Enact: " + dateUtil.timeString(lastRun.lastTBREnact)
                }
            } else {
                ret += "LOOP DISABLED\n"
            }
            return ret
        }

    //Check for Temp-Target:
    private val targetsStatus: String
        get() {
            var ret = ""
            if (!config.APS) {
                return "Targets only apply in APS mode!"
            }
            val profile = profileFunction.getProfile() ?: return "No profile set :("
            //Check for Temp-Target:
            val tempTarget = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
            if (tempTarget is ValueWrapper.Existing) {
                ret += "Temp Target: " + Profile.toTargetRangeString(tempTarget.value.lowTarget, tempTarget.value.lowTarget, GlucoseUnit.MGDL, profileFunction.getUnits())
                ret += "\nuntil: " + dateUtil.timeString(tempTarget.value.end)
                ret += "\n\n"
            }
            ret += "DEFAULT RANGE: "
            ret += Profile.fromMgdlToUnits(profile.getTargetLowMgdl(), profileFunction.getUnits()).toString() + " - " + Profile.fromMgdlToUnits(profile.getTargetHighMgdl(), profileFunction.getUnits())
            ret += " target: " + Profile.fromMgdlToUnits(profile.getTargetMgdl(), profileFunction.getUnits())
            return ret
        }

    private val oAPSResultStatus: String
        get() {
            var ret = ""
            if (!config.APS)
                return "Only apply in APS mode!"
            val usedAPS = activePlugin.activeAPS
            val result = usedAPS.lastAPSResult ?: return "Last result not available!"
            ret += if (!result.isChangeRequested) {
                resourceHelper.gs(R.string.nochangerequested) + "\n"
            } else if (result.rate == 0.0 && result.duration == 0) {
                resourceHelper.gs(R.string.canceltemp) + "\n"
            } else {
                resourceHelper.gs(R.string.rate) + ": " + DecimalFormatter.to2Decimal(result.rate) + " U/h " +
                    "(" + DecimalFormatter.to2Decimal(result.rate / activePlugin.activePump.baseBasalRate * 100) + "%)\n" +
                    resourceHelper.gs(R.string.duration) + ": " + DecimalFormatter.to0Decimal(result.duration.toDouble()) + " min\n"
            }
            ret += "\n" + resourceHelper.gs(R.string.reason) + ": " + result.reason
            return ret
        }

    @Synchronized
    fun handleConfirmation(actionString: String) {
        if (!sp.getBoolean(R.string.key_wear_control, false)) return
        //Guard from old or duplicate confirmations
        if (lastConfirmActionString == null) return
        if (lastConfirmActionString != actionString) return
        if (System.currentTimeMillis() - lastSentTimestamp > timeout) return
        lastConfirmActionString = null
        // do the parsing, check constraints and enact!
        val act = actionString.split("\\s+".toRegex()).toTypedArray()
        if ("fill" == act[0]) {
            val amount = SafeParse.stringToDouble(act[1])
            val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(amount)).value()
            if (amount - insulinAfterConstraints != 0.0) {
                ToastUtils.showToastInUiThread(context, "aborting: previously applied constraint changed")
                sendError("aborting: previously applied constraint changed")
                return
            }
            doFillBolus(amount)
        } else if ("temptarget" == act[0]) {
            val duration = SafeParse.stringToInt(act[2])
            var low = SafeParse.stringToDouble(act[3])
            var high = SafeParse.stringToDouble(act[4])
            val isMGDL = java.lang.Boolean.parseBoolean(act[1])
            if (!isMGDL) {
                low *= Constants.MMOLL_TO_MGDL
                high *= Constants.MMOLL_TO_MGDL
            }
            generateTempTarget(duration, low, high)
        } else if ("wizard2" == act[0]) {
            if (lastBolusWizard != null) { //use last calculation as confirmed string matches
                doBolus(lastBolusWizard!!.calculatedTotalInsulin, lastBolusWizard!!.carbs, null, 0)
                lastBolusWizard = null
            }
        } else if ("bolus" == act[0]) {
            val insulin = SafeParse.stringToDouble(act[1])
            val carbs = SafeParse.stringToInt(act[2])
            doBolus(insulin, carbs, null, 0)
        } else if ("cppset" == act[0]) {
            val timeshift = SafeParse.stringToInt(act[1])
            val percentage = SafeParse.stringToInt(act[2])
            setCPP(timeshift, percentage)
        } else if ("ecarbs" == act[0]) {
            val carbs = SafeParse.stringToInt(act[1])
            val starttime = SafeParse.stringToLong(act[2])
            val duration = SafeParse.stringToInt(act[3])
            doECarbs(carbs, starttime, duration)
        } else if ("dismissoverviewnotification" == act[0]) {
            rxBus.send(EventDismissNotification(SafeParse.stringToInt(act[1])))
        } else if ("changeRequest" == act[0]) {
            loopPlugin.acceptChangeRequest()
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(Constants.notificationID)
        }
        lastBolusWizard = null
    }

    private fun setCPP(timeshift: Int, percentage: Int) {
        var msg = ""
        //check for validity
        if (percentage < Constants.CPP_MIN_PERCENTAGE || percentage > Constants.CPP_MAX_PERCENTAGE) {
            msg += String.format(resourceHelper.gs(R.string.valueoutofrange), "Profile-Percentage") + "\n"
        }
        if (timeshift < 0 || timeshift > 23) {
            msg += String.format(resourceHelper.gs(R.string.valueoutofrange), "Profile-Timeshift") + "\n"
        }
        val profile = profileFunction.getProfile()
        if (profile == null) {
            msg += resourceHelper.gs(R.string.notloadedplugins) + "\n"
        }
        if ("" != msg) {
            msg += resourceHelper.gs(R.string.valuesnotstored)
            val rTitle = "STATUS"
            val rAction = "statusmessage"
            wearPlugin.requestActionConfirmation(rTitle, msg, rAction)
            lastSentTimestamp = System.currentTimeMillis()
            lastConfirmActionString = rAction
            return
        }
        //send profile to pump
        uel.log(Action.PROFILE_SWITCH, Sources.Wear,
            ValueWithUnit.Percent(percentage),
            ValueWithUnit.Hour(timeshift).takeIf { timeshift != 0 })
        profileFunction.createProfileSwitch(0, percentage, timeshift)
    }

    private fun generateTempTarget(duration: Int, low: Double, high: Double) {
        if (duration != 0) {
            disposable += repository.runTransactionForResult(InsertAndCancelCurrentTemporaryTargetTransaction(
                timestamp = System.currentTimeMillis(),
                duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                reason = TemporaryTarget.Reason.WEAR,
                lowTarget = Profile.toMgdl(low, profileFunction.getUnits()),
                highTarget = Profile.toMgdl(high, profileFunction.getUnits())
            )).subscribe({ result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
            }, {
                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
            })
            uel.log(Action.TT, Sources.Wear,
                ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.WEAR),
                ValueWithUnit.fromGlucoseUnit(low, profileFunction.getUnits().asText),
                ValueWithUnit.fromGlucoseUnit(high, profileFunction.getUnits().asText).takeIf { low != high },
                ValueWithUnit.Minute(duration))
        } else {
            disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(System.currentTimeMillis()))
                .subscribe({ result ->
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                }, {
                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                })
            uel.log(Action.CANCEL_TT, Sources.Wear,
                ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.WEAR))
        }
    }

    private fun doFillBolus(amount: Double) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.PRIMING
        uel.log(Action.PRIME_BOLUS, Sources.Wear,
            ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 })
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    sendError(resourceHelper.gs(R.string.treatmentdeliveryerror) +
                        "\n" +
                        result.comment)
                }
            }
        })
    }

    private fun doECarbs(carbs: Int, time: Long, duration: Int) {
        uel.log(if (duration == 0) Action.CARBS else Action.EXTENDED_CARBS, Sources.Wear,
            ValueWithUnit.Timestamp(time),
            ValueWithUnit.Gram(carbs),
            ValueWithUnit.Hour(duration).takeIf { duration != 0 })
        doBolus(0.0, carbs, time, duration)
    }

    private fun doBolus(amount: Double, carbs: Int, carbsTime: Long?, carbsDuration: Int) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.carbs = carbs.toDouble()
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.NORMAL
        detailedBolusInfo.carbsTimestamp = carbsTime
        detailedBolusInfo.carbsDuration = T.hours(carbsDuration.toLong()).msecs()
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            val action = when {
                amount.equals(0.0) -> Action.CARBS
                carbs.equals(0)    -> Action.BOLUS
                carbsDuration > 0  -> Action.EXTENDED_CARBS
                else               -> Action.TREATMENT
            }
            uel.log(action, Sources.Wear,
                ValueWithUnit.Insulin(amount).takeIf { amount != 0.0 },
                ValueWithUnit.Gram(carbs).takeIf { carbs != 0 },
                ValueWithUnit.Hour(carbsDuration).takeIf { carbsDuration != 0 })
            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        sendError(resourceHelper.gs(R.string.treatmentdeliveryerror) +
                            "\n" +
                            result.comment)
                    }
                }
            })
        }
    }

    @Synchronized private fun sendError(errorMessage: String) {
        wearPlugin.requestActionConfirmation("ERROR", errorMessage, "error")
        lastSentTimestamp = System.currentTimeMillis()
        lastConfirmActionString = null
        lastBolusWizard = null
    }

    @Synchronized
    private fun sendStatusMessage(message: String) {
        wearPlugin.requestActionConfirmation("TDD", message, "statusmessage")
        lastSentTimestamp = System.currentTimeMillis()
        lastConfirmActionString = null
        lastBolusWizard = null
    }
}