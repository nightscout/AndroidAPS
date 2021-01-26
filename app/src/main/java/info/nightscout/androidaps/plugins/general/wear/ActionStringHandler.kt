package info.nightscout.androidaps.plugins.general.wear

import android.app.NotificationManager
import android.content.Context
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dana.DanaPump
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TDD
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.danar.DanaRPlugin
import info.nightscout.androidaps.danaRKorean.DanaRKoreanPlugin
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.danaRv2.DanaRv2Plugin
import info.nightscout.androidaps.plugins.pump.insight.LocalInsightPlugin
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.wizard.BolusWizard
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionStringHandler @Inject constructor(
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val resourceHelper: ResourceHelper,
    private val injector: HasAndroidInjector,
    private val context: Context,
    private val constraintChecker: ConstraintChecker,
    private val profileFunction: ProfileFunction,
    private val loopPlugin: LoopPlugin,
    private val wearPlugin: WearPlugin,
    private val commandQueue: CommandQueueProvider,
    private val activePlugin: ActivePluginProvider,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val localInsightPlugin: LocalInsightPlugin,
    private val danaRPlugin: DanaRPlugin,
    private val danaRKoreanPlugin: DanaRKoreanPlugin,
    private val danaRv2Plugin: DanaRv2Plugin,
    private val danaRSPlugin: DanaRSPlugin,
    private val danaPump: DanaPump,
    private val hardLimits: HardLimits,
    private val carbsGenerator: CarbsGenerator,
    private val dateUtil: DateUtil,
    private val config: Config
) {

    private val TIMEOUT = 65 * 1000
    private var lastSentTimestamp: Long = 0
    private var lastConfirmActionString: String? = null
    private var lastBolusWizard: BolusWizard? = null

    // TODO Adrian use RxBus instead of Lazy + cross dependency
    @Synchronized
    fun handleInitiate(actionString: String) {
        if (!sp.getBoolean("wearcontrol", false)) return
        lastBolusWizard = null
        var rTitle = "CONFIRM" //TODO: i18n
        var rMessage = ""
        var rAction = ""
        // do the parsing and check constraints
        val act = actionString.split("\\s+".toRegex()).toTypedArray()
        if ("fillpreset" == act[0]) { ///////////////////////////////////// PRIME/FILL
            val amount: Double = if ("1" == act[1]) {
                sp.getDouble("fill_button1", 0.3)
            } else if ("2" == act[1]) {
                sp.getDouble("fill_button2", 0.0)
            } else if ("3" == act[1]) {
                sp.getDouble("fill_button3", 0.0)
            } else {
                return
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
            if (profileFunction.getUnits() == Constants.MGDL != isMGDL) {
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
                if (low < hardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[0] || low > hardLimits.VERY_HARD_LIMIT_TEMP_MIN_BG[1]) {
                    sendError("Min-BG out of range!")
                    return
                }
                if (high < hardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[0] || high > hardLimits.VERY_HARD_LIMIT_TEMP_MAX_BG[1]) {
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
            val bgReading = iobCobCalculatorPlugin.actualBg()
            if (bgReading == null) {
                sendError("No recent BG to base calculation on!")
                return
            }
            val cobInfo = iobCobCalculatorPlugin.getCobInfo(false, "Wizard wear")
            if (cobInfo.displayCob == null) {
                sendError("Unknown COB! BG reading missing or recent app restart?")
                return
            }
            val format = DecimalFormat("0.00")
            val formatInt = DecimalFormat("0")
            val bolusWizard = BolusWizard(injector).doCalc(profile, profileName, activePlugin.activeTreatments.tempTargetFromHistory,
                carbsAfterConstraints, cobInfo.displayCob, bgReading.valueToUnits(profileFunction.getUnits()),
                0.0, percentage.toDouble(), useBG, useCOB, useBolusIOB, useBasalIOB, false, useTT, useTrend, false)
            if (Math.abs(bolusWizard.insulinAfterConstraints - bolusWizard.calculatedTotalInsulin) >= 0.01) {
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
            val activeProfileSwitch = activePlugin.activeTreatments.getProfileSwitchFromHistory(System.currentTimeMillis())
            if (activeProfileSwitch == null) {
                sendError("No active profile switch!")
                return
            } else { // read CPP values
                rTitle = "opencpp"
                rMessage = "opencpp"
                rAction = "opencpp" + " " + activeProfileSwitch.percentage + " " + activeProfileSwitch.timeshift
            }
        } else if ("cppset" == act[0]) {
            val activeProfileSwitch = activePlugin.activeTreatments.getProfileSwitchFromHistory(System.currentTimeMillis())
            if (activeProfileSwitch == null) {
                sendError("No active profile switch!")
                return
            } else { // read CPP values
                rMessage = "CPP:" + "\n\n" +
                    "Timeshift: " + act[1] + "\n" +
                    "Percentage: " + act[2] + "%"
                rAction = actionString
            }
        } else if ("tddstats" == act[0]) {
            val activePump = activePlugin.activePump
            // check if DB up to date
            val dummies: MutableList<TDD> = LinkedList()
            val historyList = getTDDList(dummies)
            if (isOldData(historyList)) {
                rTitle = "TDD"
                rAction = "statusmessage"
                rMessage = "OLD DATA - "
                //if pump is not busy: try to fetch data
                if (activePump.isBusy) {
                    rMessage += resourceHelper.gs(R.string.pumpbusy)
                } else {
                    rMessage += "trying to fetch data from pump."
                    commandQueue.loadTDDs(object : Callback() {
                        override fun run() {
                            val dummies1: MutableList<TDD> = LinkedList()
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

    private fun generateTDDMessage(historyList: MutableList<TDD>, dummies: MutableList<TDD>): String {
        val profile = profileFunction.getProfile() ?: return "No profile loaded :("
        if (historyList.isEmpty()) {
            return "No history data!"
        }
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        var message = ""
        val refTDD = profile.baseBasalSum() * 2
        val pump = activePlugin.activePump
        if (df.format(Date(historyList[0].date)) == df.format(Date())) {
            val tdd = historyList[0].getTotal()
            historyList.removeAt(0)
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n"
            message += "\n"
        } else if (pump is DanaRPlugin) {
            val tdd = danaPump.dailyTotalUnits
            message += "Today: " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + "\n"
            message += "\n"
        }
        var i = 0
        var weighted03 = 0.0
        var weighted05 = 0.0
        var weighted07 = 0.0
        Collections.reverse(historyList)
        for (record in historyList) {
            val tdd = record.getTotal()
            if (i == 0) {
                weighted03 = tdd
                weighted05 = tdd
                weighted07 = tdd
            } else {
                weighted07 = weighted07 * 0.3 + tdd * 0.7
                weighted05 = weighted05 * 0.5 + tdd * 0.5
                weighted03 = weighted03 * 0.7 + tdd * 0.3
            }
            i++
        }
        message += "weighted:\n"
        message += "0.3: " + DecimalFormatter.to2Decimal(weighted03) + "U " + (DecimalFormatter.to0Decimal(100 * weighted03 / refTDD) + "%") + "\n"
        message += "0.5: " + DecimalFormatter.to2Decimal(weighted05) + "U " + (DecimalFormatter.to0Decimal(100 * weighted05 / refTDD) + "%") + "\n"
        message += "0.7: " + DecimalFormatter.to2Decimal(weighted07) + "U " + (DecimalFormatter.to0Decimal(100 * weighted07 / refTDD) + "%") + "\n"
        message += "\n"
        Collections.reverse(historyList)
        //add TDDs:
        for (record in historyList) {
            val tdd = record.getTotal()
            message += df.format(Date(record.date)) + " " + DecimalFormatter.to2Decimal(tdd) + "U " + (DecimalFormatter.to0Decimal(100 * tdd / refTDD) + "%") + (if (dummies.contains(record)) "x" else "") + "\n"
        }
        return message
    }

    private fun isOldData(historyList: List<TDD>): Boolean {
        val activePump = activePlugin.activePump
        val startsYesterday = activePump === danaRPlugin || activePump === danaRSPlugin || activePump === danaRv2Plugin || activePump === danaRKoreanPlugin || activePump === localInsightPlugin
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        return historyList.size < 3 || df.format(Date(historyList[0].date)) != df.format(Date(System.currentTimeMillis() - if (startsYesterday) 1000 * 60 * 60 * 24 else 0))
    }

    private fun getTDDList(returnDummies: MutableList<TDD>): MutableList<TDD> {
        var historyList = MainApp.getDbHelper().tdDs
        historyList = historyList.subList(0, Math.min(10, historyList.size))
        //fill single gaps - only needed for Dana*R data
        val dummies: MutableList<TDD> = returnDummies
        val df: DateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
        for (i in 0 until historyList.size - 1) {
            val elem1 = historyList[i]
            val elem2 = historyList[i + 1]
            if (df.format(Date(elem1!!.date)) != df.format(Date(elem2!!.date + 25 * 60 * 60 * 1000))) {
                val dummy = TDD()
                dummy.date = elem1.date - 24 * 60 * 60 * 1000
                dummy.basal = elem1.basal / 2
                dummy.bolus = elem1.bolus / 2
                dummies.add(dummy)
                elem1.basal /= 2.0
                elem1.bolus /= 2.0
            }
        }
        historyList.addAll(dummies)
        Collections.sort(historyList) { lhs, rhs -> (rhs.date - lhs.date).toInt() }
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
            val tempTarget = activePlugin.activeTreatments.tempTargetFromHistory
            if (tempTarget != null) {
                ret += "Temp Target: " + Profile.toTargetRangeString(tempTarget.low, tempTarget.low, Constants.MGDL, profileFunction.getUnits())
                ret += "\nuntil: " + dateUtil.timeString(tempTarget.originalEnd())
                ret += "\n\n"
            }
            ret += "DEFAULT RANGE: "
            ret += Profile.fromMgdlToUnits(profile.targetLowMgdl, profileFunction.getUnits()).toString() + " - " + Profile.fromMgdlToUnits(profile.targetHighMgdl, profileFunction.getUnits())
            ret += " target: " + Profile.fromMgdlToUnits(profile.targetMgdl, profileFunction.getUnits())
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
        if (!sp.getBoolean("wearcontrol", false)) return
        //Guard from old or duplicate confirmations
        if (lastConfirmActionString == null) return
        if (lastConfirmActionString != actionString) return
        if (System.currentTimeMillis() - lastSentTimestamp > TIMEOUT) return
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
                doBolus(lastBolusWizard!!.calculatedTotalInsulin, lastBolusWizard!!.carbs)
                lastBolusWizard = null
            }
        } else if ("bolus" == act[0]) {
            val insulin = SafeParse.stringToDouble(act[1])
            val carbs = SafeParse.stringToInt(act[2])
            doBolus(insulin, carbs)
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

    private fun doECarbs(carbs: Int, time: Long, duration: Int) {
        if (carbs > 0) {
            if (duration == 0) {
                carbsGenerator.createCarb(carbs, time, CareportalEvent.CARBCORRECTION, "watch")
            } else {
                carbsGenerator.generateCarbs(carbs, time, duration, "watch eCarbs")
            }
        }
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
        //send profile to pumpe
        activePlugin.activeTreatments.doProfileSwitch(0, percentage, timeshift)
    }

    private fun generateTempTarget(duration: Int, low: Double, high: Double) {
        val tempTarget = TempTarget()
            .date(System.currentTimeMillis())
            .duration(duration)
            .reason("WearPlugin")
            .source(Source.USER)
        if (tempTarget.durationInMinutes != 0) {
            tempTarget.low(low).high(high)
        } else {
            tempTarget.low(0.0).high(0.0)
        }
        activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
    }

    private fun doFillBolus(amount: Double) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.isValid = false
        detailedBolusInfo.source = Source.USER
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

    private fun doBolus(amount: Double, carbs: Int) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = amount
        detailedBolusInfo.carbs = carbs.toDouble()
        detailedBolusInfo.source = Source.USER
        val storesCarbs = activePlugin.activePump.pumpDescription.storesCarbInfo
        if (detailedBolusInfo.insulin > 0 || storesCarbs) {
            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        sendError(resourceHelper.gs(R.string.treatmentdeliveryerror) +
                            "\n" +
                            result.comment)
                    }
                }
            })
        } else {
            activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
        }
    }

    @Synchronized private fun sendError(errormessage: String) {
        wearPlugin.requestActionConfirmation("ERROR", errormessage, "error")
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
    } /*
    public synchronized static void expectNotificationAction(String message, int id) {
        String actionstring = "dismissoverviewnotification " + id;
        WearPlugin.getPlugin().requestActionConfirmation("DISMISS", message, actionstring);
        lastSentTimestamp = System.currentTimeMillis();
        lastConfirmActionString = actionstring;
        lastBolusWizard = null;
    }
*/
}