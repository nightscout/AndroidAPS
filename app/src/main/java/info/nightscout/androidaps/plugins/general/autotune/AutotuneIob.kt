package info.nightscout.androidaps.plugins.general.autotune

import androidx.collection.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.*
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.historyBrowser.IobCobCalculatorPluginHistory
import info.nightscout.androidaps.historyBrowser.TreatmentsPluginHistory
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.autotune.AutotunePlugin
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutotuneIob(
    private val injector: HasAndroidInjector
) {

    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var iobCobCalculatorPluginHistory: IobCobCalculatorPluginHistory
    @Inject lateinit var treatmentsPluginHistory: TreatmentsPluginHistory
    @Inject lateinit var nsUpload: NSUpload

    private val disposable = CompositeDisposable()
    private val nsTreatments = ArrayList<NsTreatment>()
    var treatments: MutableList<Treatment> = ArrayList()
    var meals = ArrayList<Treatment>()
    var glucose: MutableList<BgReading> = ArrayList()
    private val tempBasals: Intervals<TemporaryBasal> = NonOverlappingIntervals()
    private val tempBasals2: Intervals<TemporaryBasal> = NonOverlappingIntervals()
    private val extendedBoluses: Intervals<ExtendedBolus> = NonOverlappingIntervals()
    private val tempTargets: Intervals<TempTarget> = OverlappingIntervals()
    private val profiles = ProfileIntervals<ProfileSwitch>()
    var startBG: Long = 0
    var endBG: Long = 0
    private val iobTable = LongSparseArray<IobTotal>() // oldest at index 0
    private fun range(): Long {
        var dia = Constants.defaultDIA
        if (profileFunction!!.getProfile() != null) dia = profileFunction!!.getProfile()!!.dia
        return (60 * 60 * 1000L * dia).toLong()
    }

    fun initializeData(from: Long, to: Long) {
        startBG = from
        endBG = to
        nsTreatments.clear()
        initializeBgreadings(from, to)
        initializeTreatmentData(from - range(), to)
        initializeTempBasalData(from - range(), to)
        initializeExtendedBolusData(from - range(), to)
        //initializeTempTargetData(from, to);
        //initializeProfileSwitchData(from-range(), to);
        //NsTreatment is used to export all "ns-treatments" for cross execution of oref0-autotune on a virtual machine
        //it contains traitments, tempbasals and extendedbolus data (profileswitch data also included in ns-treatment files are not used by oref0-autotune)
        Collections.sort(nsTreatments) { o1: NsTreatment, o2: NsTreatment -> (o2.date - o1.date).toInt() }
        log.debug("D/AutotunePlugin: Nb Treatments: " + nsTreatments.size + " Nb meals: " + meals.size)
    }

    private fun initializeBgreadings(from: Long, to: Long) {
        glucose.clear()
        glucose = MainApp.getDbHelper().getBgreadingsDataFromTime(from, to, false)
    }

    //nsTreatment is used only for export data, meals is used in AutotunePrep
    private fun initializeTreatmentData(from: Long, to: Long) {
        val oldestBgDate = if (glucose.size > 0) glucose[glucose.size - 1].date else from
        log.debug("AutotunePlugin Check BG date: BG Size: " + glucose.size + " OldestBG: " + dateUtil!!.dateAndTimeAndSecondsString(oldestBgDate) + " to: " + dateUtil!!.dateAndTimeAndSecondsString(to))
        val temp = treatmentsPluginHistory!!.service.getTreatmentDataFromTime(from, to, false)
        log.debug("AutotunePlugin Nb treatments after query: " + temp.size)
        meals.clear()
        treatments.clear()
        var nbCarbs = 0
        var nbSMB = 0
        var nbBolus = 0
        for (i in temp.indices) {
            val tp = temp[i]
            if (tp.isValid) {
                treatments.add(tp)
                nsTreatments.add(NsTreatment(tp))
                //only carbs after first BGReadings are taken into account in calculation of Autotune
                if (tp.carbs > 0 && tp.date >= oldestBgDate) meals.add(temp[i])
                if (tp.date < to) {
                    if (tp.isSMB) nbSMB++ else if (tp.insulin > 0) nbBolus++
                    if (tp.carbs > 0) nbCarbs++
                }
            }
        }
        log.debug("AutotunePlugin Nb Meals: $nbCarbs Nb Bolus: $nbBolus Nb SMB: $nbSMB")
    }

    //nsTreatment is used only for export data
    private fun initializeTempBasalData(from: Long, to: Long) {
        val temp = MainApp.getDbHelper().getTemporaryBasalsDataFromTime(from - range(), to, false)
        // Initialize tempBasals according to TreatmentsPlugin
        tempBasals.reset().add(temp)
        //first keep only valid data
        //log.debug("D/AutotunePlugin Start inisalize Tempbasal from: " + dateUtil.dateAndTimeAndSecondsString(from) + " number of entries:" + temp.size());
        run {
            var i = 0
            while (i < temp.size) {
                if (!temp[i].isValid) temp.removeAt(i--)
                i++
            }
        }
        val temp2: MutableList<TemporaryBasal> = ArrayList()
        //log.debug("D/AutotunePlugin after cleaning number of entries:" + temp.size());
        //Then add neutral TBR if start of next TBR is after the end of previous one
        var previousend = temp[temp.size - 1].date + temp[temp.size - 1].realDuration * 60 * 1000
        for (i in temp.indices.reversed()) {
            val tb = temp[i]
            //log.debug("D/AutotunePlugin previous end: " + dateUtil.dateAndTimeAndSecondsString(previousend) + " new entry start:" + dateUtil.dateAndTimeAndSecondsString(tb.date) + " new entry duration:" + tb.getRealDuration() + " test:" + (tb.date < previousend + 60 * 1000));
            if (tb.date < previousend + 60 * 1000) {                         // 1 min is minimum duration for TBR
                nsTreatments.add(NsTreatment(tb))
                temp2.add(0, tb)
                previousend = tb.date + tb.realDuration * 60 * 1000
            } else {
                var minutesToFill = (tb.date - previousend).toInt() / (60 * 1000)
                //log.debug("D/AutotunePlugin Minutes to fill: "+ minutesToFill);
                while (minutesToFill > 0) {
                    val profile = profileFunction!!.getProfile(previousend)
                    if (Profile.secondsFromMidnight(tb.date) / 3600 == Profile.secondsFromMidnight(previousend) / 3600) {  // next tbr is in the same hour
                        val neutralTbr = TemporaryBasal(injector)
                        neutralTbr.date = previousend + 1000 //add 1s to be sure it starts after endEvent
                        neutralTbr.isValid = true
                        neutralTbr.absoluteRate = profile!!.getBasal(previousend)
                        neutralTbr.durationInMinutes = minutesToFill + 1 //add 1 minute to be sure there is no gap between TBR and neutral TBR
                        neutralTbr.isAbsolute = true
                        minutesToFill = 0
                        previousend += minutesToFill * 60 * 1000.toLong()
                        nsTreatments.add(NsTreatment(neutralTbr))
                        temp2.add(0, neutralTbr)
                        //log.debug("D/AutotunePlugin fill neutral start: " + dateUtil.dateAndTimeAndSecondsString(neutralTbr.date) + " duration:" + neutralTbr.durationInMinutes + " absolute:" + neutralTbr.absoluteRate);
                    } else {  //fill data until the end of current hour
                        val minutesFilled = 60 - Profile.secondsFromMidnight(previousend) / 60 % 60
                        //log.debug("D/AutotunePlugin remaining time before next hour: "+ minutesFilled);
                        val neutralTbr = TemporaryBasal(injector)
                        neutralTbr.date = previousend + 1000 //add 1s to be sure it starts after endEvent
                        neutralTbr.isValid = true
                        neutralTbr.absoluteRate = profile!!.getBasal(previousend)
                        neutralTbr.durationInMinutes = minutesFilled + 1 //add 1 minute to be sure there is no gap between TBR and neutral TBR
                        neutralTbr.isAbsolute = true
                        minutesToFill -= minutesFilled
                        previousend = MidnightTime.calc(previousend) + (Profile.secondsFromMidnight(previousend) / 3600 + 1) * 3600 * 1000L //previousend is updated at the beginning of next hour
                        nsTreatments.add(NsTreatment(neutralTbr))
                        temp2.add(0, neutralTbr)
                        //log.debug("D/AutotunePlugin fill neutral start: " + dateUtil.dateAndTimeAndSecondsString(neutralTbr.date) + " duration:" + neutralTbr.durationInMinutes + " absolute:" + neutralTbr.absoluteRate);
                    }
                }
                nsTreatments.add(NsTreatment(tb))
                temp2.add(0, tb)
                previousend = tb.date + tb.realDuration * 60 * 1000
            }
        }
        Collections.sort(temp2) { o1: TemporaryBasal, o2: TemporaryBasal -> (o2.date - o1.date).toInt() }
        // Initialize tempBasals with neutral TBR added
        tempBasals2.reset().add(temp2)
        log.debug("D/AutotunePlugin: tempBasal size: " + tempBasals.size() + " tempBasal2 size: " + tempBasals2.size())
    }

    //nsTreatment is used only for export data
    private fun initializeExtendedBolusData(from: Long, to: Long) {
        val temp = MainApp.getDbHelper().getExtendedBolusDataFromTime(from - range(), to, false)
        extendedBoluses.reset().add(temp)
        for (i in temp.indices) {
            val eb = temp[i]
            nsTreatments.add(NsTreatment(eb))
        }
    }

    fun getIOB(time: Long, currentBasal: Double): IobTotal {
        val bolusIob = getCalculationToTimeTreatments(time).round()
        // Calcul from specific tempBasals completed with neutral tbr
        val basalIob = getCalculationToTimeTempBasals(time, true, endBG, currentBasal).round()
//        log.debug("D/AutotunePlugin: CurrentBasal: " + currentBasal + " BolusIOB: " + bolusIob.iob + " CalculABS: " + basalIob.basaliob + " CalculSTD: " + basalIob2.basaliob + " testAbs: " + absbasaliob.basaliob + " activity " + absbasaliob.activity)
        return IobTotal.combine(bolusIob, basalIob).round()
    }

    fun getCalculationToTimeTreatments(time: Long): IobTotal {
        val total = IobTotal(time)
        val profile = profileFunction!!.getProfile(time) ?: return total
        val pumpInterface = activePlugin!!.activePump
        val dia = profile.dia
        for (pos in treatments.indices) {
            val t = treatments[pos]
            if (!t.isValid) continue
            if (t.date > time) continue
            val tIOB = t.iobCalc(time, dia)
            total.iob += tIOB.iobContrib
            total.activity += tIOB.activityContrib
            if (t.insulin > 0 && t.date > total.lastBolusTime) total.lastBolusTime = t.date
            if (!t.isSMB) {
                // instead of dividing the DIA that only worked on the bilinear curves,
                // multiply the time the treatment is seen active.
                val timeSinceTreatment = time - t.date
                val snoozeTime = t.date + (timeSinceTreatment * sp!!.getDouble(R.string.key_openapsama_bolussnooze_dia_divisor, 2.0)).toLong()
                val bIOB = t.iobCalc(snoozeTime, dia)
                total.bolussnooze += bIOB.iobContrib
            }
        }
        if (!pumpInterface.isFakingTempsByExtendedBoluses) for (pos in 0 until extendedBoluses.size()) {
            val e = extendedBoluses[pos]
            if (e.date > time) continue
            val calc = e.iobCalc(time, profile)
            total.plus(calc)
        }
        return total
    }

    fun getCalculationToTimeTempBasals(time: Long, truncate: Boolean, truncateTime: Long, currentBasal: Double): IobTotal {
        val total = IobTotal(time)
        val pumpInterface = activePlugin!!.activePump
        for (pos in 0 until tempBasals2.size()) {
            val t = tempBasals2[pos]
            if (t.date > time) continue
            var calc: IobTotal?
            val profile = profileFunction!!.getProfile(t.date) ?: continue
            calc = if (truncate && t.end() > truncateTime) {
                val dummyTemp = TemporaryBasal(injector)
                dummyTemp.copyFrom(t)
                dummyTemp.cutEndTo(truncateTime)
                dummyTemp.iobCalc(time, profile, currentBasal)
            } else {
                t.iobCalc(time, profile, currentBasal)
            }
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(time)
            for (pos in 0 until extendedBoluses.size()) {
                val e = extendedBoluses[pos]
                if (e.date > time) continue
                var calc: IobTotal?
                val profile = profileFunction!!.getProfile(e.date) ?: continue
                calc = if (truncate && e.end() > truncateTime) {
                    val dummyExt = ExtendedBolus(injector)
                    dummyExt.copyFrom(e)
                    dummyExt.cutEndTo(truncateTime)
                    dummyExt.iobCalc(time, profile)
                } else {
                    e.iobCalc(time, profile)
                }
                totalExt.plus(calc)
            }
            // Convert to basal iob
            totalExt.basaliob = totalExt.iob
            totalExt.iob = 0.0
            totalExt.netbasalinsulin = totalExt.extendedBolusInsulin
            totalExt.hightempinsulin = totalExt.extendedBolusInsulin
            total.plus(totalExt)
        }
        return total
    }

    /** */
    fun glucosetoJSON(): JSONArray {
        val glucoseJson = JSONArray()
        val now = Date(System.currentTimeMillis())
        val utcOffset = ((DateUtil.fromISODateString(DateUtil.toISOString(now, null, null)).time - DateUtil.fromISODateString(DateUtil.toISOString(now)).time) / (60 * 1000)).toInt()
        try {
            for (bgreading in glucose) {
                val bgjson = JSONObject()
                bgjson.put("_id", bgreading._id)
                bgjson.put("device", "AndroidAPS")
                bgjson.put("date", bgreading.date)
                bgjson.put("dateString", DateUtil.toISOString(bgreading.date))
                bgjson.put("sgv", bgreading.value)
                bgjson.put("direction", bgreading.direction)
                bgjson.put("type", "sgv")
                bgjson.put("systime", DateUtil.toISOString(bgreading.date))
                bgjson.put("utcOffset", utcOffset)
                glucoseJson.put(bgjson)
            }
        } catch (e: JSONException) {
        }
        return glucoseJson
    }

    fun nsHistorytoJSON(): JSONArray {
        val json = JSONArray()
        for (t in nsTreatments) {
            if (t.isValid) json.put(t.toJson())
        }
        return json
    }

    /** */ //I add this internal class to be able to export easily ns-treatment files with same containt and format than NS query used by oref0-autotune
    private inner class NsTreatment {

        //Common properties
        var _id: String? = null
        var date: Long = 0
        var isValid = false
        var eventType: String? = null
        var created_at: String? = null

        // treatment properties
        var treatment: Treatment? = null
        var insulin = 0.0
        var carbs = 0.0
        var isSMB = false
        var mealBolus = false

        //TemporayBasal or ExtendedBolus properties
        var temporaryBasal: TemporaryBasal? = null
        var absoluteRate: Double? = null
        var isEndingEvent = false
        var duration=0 // msec converted in minutes = 0
        var isFakeExtended = false
        var enteredBy: String? = null
        var percentRate = 0
        var isAbsolute = false
        var extendedBolus: ExtendedBolus? = null
        private val origin: String? = null

        //CarePortalEvents
        var careportalEvent: CareportalEvent? = null
        var json: String? = null

        constructor(t: Treatment) {
            treatment = t
            _id = t._id
            date = t.date
            carbs = t.carbs
            insulin = t.insulin
            isSMB = t.isSMB
            isValid = t.isValid
            mealBolus = t.mealBolus
            eventType = if (insulin > 0 && carbs > 0) CareportalEvent.BOLUSWIZARD else if (carbs > 0) CareportalEvent.CARBCORRECTION else CareportalEvent.CORRECTIONBOLUS
            created_at = DateUtil.toISOString(t.date)
        }

        constructor(t: CareportalEvent) {
            careportalEvent = t
            _id = t._id
            date = t.date
            created_at = DateUtil.toISOString(t.date)
            eventType = t.eventType
            duration = Math.round(t.duration / 60f / 1000)
            isValid = t.isValid
            json = t.json
        }

        constructor(t: TemporaryBasal) {
            temporaryBasal = t
            _NsTreatment(t)
        }

        constructor(t: ExtendedBolus?) {
            extendedBolus = t
            _NsTreatment(TemporaryBasal(t))
        }

        private fun _NsTreatment(t: TemporaryBasal) {
            _id = t._id
            date = t.date
            if (t.isAbsolute)
                absoluteRate = Round.roundTo(t.absoluteRate, 0.001)
            else {
                val profile = profileFunction?.getProfile(date)
                absoluteRate = profile!!.getBasal(temporaryBasal!!.date) * temporaryBasal!!.percentRate / 100 ?:0.0
            }
            isValid = t.isValid
            isEndingEvent = t.isEndingEvent
            eventType = CareportalEvent.TEMPBASAL
            enteredBy = "openaps://" + resourceHelper!!.gs(R.string.app_name)
            duration = t.realDuration
            percentRate = t.percentRate
            isFakeExtended = t.isFakeExtended
            created_at = DateUtil.toISOString(t.date)
            isAbsolute = true
        }

        fun toJson(): JSONObject {
            val cPjson = JSONObject()
            try {
                cPjson.put("_id", _id)
                cPjson.put("eventType", eventType)
                cPjson.put("date", date)
                cPjson.put("created_at", created_at)
                cPjson.put("insulin", if (insulin > 0) insulin else JSONObject.NULL)
                cPjson.put("carbs", if (carbs > 0) carbs else JSONObject.NULL)
                if (eventType === CareportalEvent.TEMPBASAL) {
                    if (!isEndingEvent) {
                        cPjson.put("duration", duration)
                        cPjson.put("absolute", absoluteRate)
                        cPjson.put("rate", absoluteRate)
                        // cPjson.put("percent", percentRate - 100);
                        cPjson.put("isFakeExtended", isFakeExtended)
                    }
                    cPjson.put("enteredBy", enteredBy)
                    //cPjson.put("isEnding", isEndingEvent);
                } else {
                    cPjson.put("isSMB", isSMB)
                    cPjson.put("isMealBolus", mealBolus)
                }
            } catch (e: JSONException) {
            }
            return cPjson
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AutotunePlugin::class.java)
    }

    init {
        //injector = StaticInjector.Companion.getInstance();
        injector.androidInjector().inject(this)
        //initializeData(from,to);
    }
}