package info.nightscout.androidaps.plugins.general.autotune

import androidx.collection.LongSparseArray
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.*
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.db.*
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.activities.TreatmentsActivity
import info.nightscout.androidaps.database.entities.*
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.extensions.durationInMinutes
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.toJson
import info.nightscout.androidaps.extensions.toTemporaryBasal
import info.nightscout.androidaps.interfaces.Profile
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.sensitivity.SensitivityAAPSPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityWeightedAveragePlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.MidnightTime
import info.nightscout.androidaps.utils.Round
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.sharedPreferences.SP
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

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
    @Inject lateinit var sensitivityAAPSPlugin: SensitivityAAPSPlugin
    @Inject lateinit var sensitivityWeightedAveragePlugin: SensitivityWeightedAveragePlugin
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var sp: SP
    //@Inject lateinit var treatmentsActivity: TreatmentsActivity
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin

    lateinit var iobCobCalculator: IobCobCalculatorPlugin
    private val disposable = CompositeDisposable()
    private val nsTreatments = ArrayList<NsTreatment>()
    var boluses: MutableList<Bolus> = ArrayList()
    var meals = ArrayList<Carbs>()
    lateinit var glucose: List<GlucoseValue> // newest at index 0
    //var glucose: MutableList<GlucoseValue> = ArrayList()
    private lateinit var tempBasals: MutableList<TemporaryBasal>
    private lateinit var tempBasals2: MutableList<TemporaryBasal>
    private lateinit var extendedBoluses: List<ExtendedBolus>
    //private val tempTargets: Intervals<TempTarget> = OverlappingIntervals()
    private lateinit var profiles: MutableList<ProfileSwitch>
    var startBG: Long = 0
    var endBG: Long = 0
    private val iobTable = LongSparseArray<IobTotal>() // oldest at index 0
    private fun range(): Long {
        var dia = Constants.defaultDIA
        if (profileFunction.getProfile() != null) dia = profileFunction.getProfile()!!.dia
        return (60 * 60 * 1000L * dia).toLong()
    }

    fun initializeData(from: Long, to: Long) {
        iobCobCalculator =
            IobCobCalculatorPlugin(
                injector,
                aapsLogger,
                aapsSchedulers,
                rxBus,
                sp,
                rh,
                profileFunction,
                activePlugin,
                sensitivityOref1Plugin,
                sensitivityAAPSPlugin,
                sensitivityWeightedAveragePlugin,
                fabricPrivacy,
                dateUtil,
                repository
            )

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
        //glucose.clear()
        glucose = repository.compatGetBgReadingsDataFromTime(from, to, false).blockingGet();
    }

    //nsTreatment is used only for export data, meals is used in AutotunePrep
    private fun initializeTreatmentData(from: Long, to: Long) {
        val oldestBgDate = if (glucose.size > 0) glucose[glucose.size - 1].timestamp else from
        log.debug("AutotunePlugin Check BG date: BG Size: " + glucose.size + " OldestBG: " + dateUtil.dateAndTimeAndSecondsString(oldestBgDate) + " to: " + dateUtil.dateAndTimeAndSecondsString(to))
        val tmpCarbs = repository.getCarbsDataFromTimeToTimeExpanded(from, to, true).blockingGet()
        log.debug("AutotunePlugin Nb treatments after query: " + tmpCarbs.size)
        meals.clear()
        boluses.clear()
        var nbCarbs = 0
        for (i in tmpCarbs.indices) {
            val tp = tmpCarbs[i]
            if (tp.isValid) {
                nsTreatments.add(NsTreatment(tp))
                //only carbs after first BGReadings are taken into account in calculation of Autotune
                if (tp.amount > 0.0 && tp.timestamp >= oldestBgDate) meals.add(tmpCarbs[i])
                if (tp.timestamp < to && tp.amount > 0.0)
                    nbCarbs++
            }
        }
        val tmpBolus = repository.getBolusesDataFromTimeToTime(from, to, true).blockingGet()
        var nbSMB = 0
        var nbBolus = 0
        for (i in tmpBolus.indices) {
            val tp = tmpBolus[i]
            if (tp.isValid) {
                boluses.add(tp)
                nsTreatments.add(NsTreatment(tp))
                //only carbs after first BGReadings are taken into account in calculation of Autotune
                if (tp.timestamp < to) {
                    if (tp.type == Bolus.Type.SMB)
                        nbSMB++
                    else if (tp.amount > 0.0)
                        nbBolus++
                }
            }
        }

        log.debug("AutotunePlugin Nb Meals: $nbCarbs Nb Bolus: $nbBolus Nb SMB: $nbSMB")
    }

    //nsTreatment is used only for export data
    private fun initializeTempBasalData(from: Long, to: Long) {
        val temp = repository.getTemporaryBasalsDataFromTimeToTime(from - range(), to, false).blockingGet()
        // Initialize tempBasals according to TreatmentsPlugin
        //tempBasals.(temp)
        //first keep only valid data
        //log.debug("D/AutotunePlugin Start inisalize Tempbasal from: " + dateUtil.dateAndTimeAndSecondsString(from) + " number of entries:" + tmpCarbs.size());
        run {
            var i = 0
            while (i < temp.size) {
                //if (!temp[i].isValid) temp.removeAt(i--)
                i++
            }
        }
        val temp2: MutableList<TemporaryBasal> = ArrayList()
        //log.debug("D/AutotunePlugin after cleaning number of entries:" + tmpCarbs.size());
        //Then add neutral TBR if start of next TBR is after the end of previous one
        var previousend = temp[temp.size - 1].timestamp + temp[temp.size - 1].duration
        for (i in temp.indices.reversed()) {
            val tb = temp[i]
            //log.debug("D/AutotunePlugin previous end: " + dateUtil.dateAndTimeAndSecondsString(previousend) + " new entry start:" + dateUtil.dateAndTimeAndSecondsString(tb.date) + " new entry duration:" + tb.getRealDuration() + " test:" + (tb.date < previousend + 60 * 1000));
            if (tb.timestamp < previousend) {                         // 1 min is minimum duration for TBR
                nsTreatments.add(NsTreatment(tb))
                temp2.add(0, tb)
                previousend = tb.timestamp + tb.durationInMinutes * 60 * 1000
            } else {
                var minutesToFill = (tb.timestamp - previousend).toInt() / (60 * 1000)
                //log.debug("D/AutotunePlugin Minutes to fill: "+ minutesToFill);
                while (minutesToFill > 0) {
                    val profile = profileFunction.getProfile(previousend)
                    if (Profile.secondsFromMidnight(tb.timestamp) / 3600 == Profile.secondsFromMidnight(previousend) / 3600) {  // next tbr is in the same hour
                        val neutralTbr = TemporaryBasal(
                            isValid = true,
                            isAbsolute = true,
                            timestamp = previousend + 1000,
                            rate = profile!!.getBasal(previousend),
                            duration = (minutesToFill * 60 * 1000).toLong(),
                            type = TemporaryBasal.Type.NORMAL
                        )

                        minutesToFill = 0
                        previousend += minutesToFill * 60 * 1000.toLong()
                        nsTreatments.add(NsTreatment(neutralTbr))
                        temp2.add(0, neutralTbr)
                        //log.debug("D/AutotunePlugin fill neutral start: " + dateUtil.dateAndTimeAndSecondsString(neutralTbr.date) + " duration:" + neutralTbr.durationInMinutes + " absolute:" + neutralTbr.absoluteRate);
                    } else {  //fill data until the end of current hour
                        val minutesFilled = 60 - Profile.secondsFromMidnight(previousend) / 60 % 60
                        //log.debug("D/AutotunePlugin remaining time before next hour: "+ minutesFilled);
                        val neutralTbr = TemporaryBasal(
                            isValid = true,
                            isAbsolute = true,
                            timestamp = previousend + 1000,
                            rate = profile!!.getBasal(previousend),
                            duration = (minutesToFill * 60 * 1000).toLong(),
                            type = TemporaryBasal.Type.NORMAL
                        )

                        minutesToFill -= minutesFilled
                        previousend = MidnightTime.calc(previousend) + (Profile.secondsFromMidnight(previousend) / 3600 + 1) * 3600 * 1000L //previousend is updated at the beginning of next hour
                        nsTreatments.add(NsTreatment(neutralTbr))
                        temp2.add(0, neutralTbr)
                        //log.debug("D/AutotunePlugin fill neutral start: " + dateUtil.dateAndTimeAndSecondsString(neutralTbr.date) + " duration:" + neutralTbr.durationInMinutes + " absolute:" + neutralTbr.absoluteRate);
                    }
                }
                nsTreatments.add(NsTreatment(tb))
                temp2.add(0, tb)
                previousend = tb.timestamp + tb.duration
            }
        }
        Collections.sort(temp2) { o1: TemporaryBasal, o2: TemporaryBasal -> (o2.timestamp - o1.timestamp).toInt() }
        // Initialize tempBasals with neutral TBR added
        tempBasals2 = ArrayList<TemporaryBasal>()
        tempBasals2.addAll(temp2)
        log.debug("D/AutotunePlugin: tempBasal size: " + tempBasals2.size)
    }

    //nsTreatment is used only for export data
    private fun initializeExtendedBolusData(from: Long, to: Long) {
        extendedBoluses = repository.getExtendedBolusDataFromTimeToTime(from - range(), to, false).blockingGet()
        //extendedBoluses.addAll(temp)
        for (i in extendedBoluses.indices) {
            val eb = extendedBoluses[i]
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
        val profile = profileFunction.getProfile(time) ?: return total
        val pumpInterface = activePlugin.activePump
        val dia = profile.dia
        for (pos in boluses.indices) {
            val t = boluses[pos]
            if (!t.isValid) continue
            if (t.timestamp > time) continue
            val tIOB = t.iobCalc(activePlugin, time, dia)
            total.iob += tIOB.iobContrib
            total.activity += tIOB.activityContrib
            if (t.amount > 0 && t.timestamp > total.lastBolusTime) total.lastBolusTime = t.timestamp
            if (t.type != Bolus.Type.SMB) {
                // instead of dividing the DIA that only worked on the bilinear curves,
                // multiply the time the treatment is seen active.
                val timeSinceTreatment = time - t.timestamp
                val snoozeTime = t.timestamp + (timeSinceTreatment * sp.getDouble(R.string.key_openapsama_bolussnooze_dia_divisor, 2.0)).toLong()
                val bIOB = t.iobCalc(activePlugin, snoozeTime, dia)
                total.bolussnooze += bIOB.iobContrib
            }
        }
        if (!pumpInterface.isFakingTempsByExtendedBoluses) for (pos in 0 until extendedBoluses.size) {
            val e = extendedBoluses[pos]
            if (e.timestamp > time) continue
            val calc = e.iobCalc(time, profile, activePlugin.activeInsulin)
            total.plus(calc)
        }
        return total
    }

    fun getCalculationToTimeTempBasals(time: Long, truncate: Boolean, truncateTime: Long, currentBasal: Double): IobTotal {
        val total = IobTotal(time)
        val pumpInterface = activePlugin.activePump
        for (pos in 0 until tempBasals2.size) {
            val t = tempBasals2[pos]
            if (t.timestamp > time) continue
            var calc: IobTotal?
            val profile = profileFunction.getProfile(t.timestamp) ?: continue
            calc = if (truncate && t.end > truncateTime) {
                val dummyTemp = TemporaryBasal(
                    id = t.id,
                    timestamp = t.timestamp,
                    rate = t.rate,
                    type = TemporaryBasal.Type.NORMAL,
                    isAbsolute = true,
                    duration = truncateTime - t.timestamp
                )
                dummyTemp.iobCalc(time, profile, activePlugin.activeInsulin)
            } else {
                t.iobCalc(time, profile, activePlugin.activeInsulin)
            }
            //log.debug("BasalIOB " + new Date(time) + " >>> " + calc.basaliob);
            total.plus(calc)
        }
        if (pumpInterface.isFakingTempsByExtendedBoluses) {
            val totalExt = IobTotal(time)
            for (pos in 0 until extendedBoluses.size) {
                val e = extendedBoluses[pos]
                if (e.timestamp > time) continue
                var calc: IobTotal?
                val profile = profileFunction.getProfile(e.timestamp) ?: continue
                calc = if (truncate && e.end > truncateTime) {
                    val dummyExt = ExtendedBolus(
                        id = e.id,
                        timestamp = e.timestamp,
                        duration = e.duration,
                        amount = e.amount
                    )
                    //dummyExt.cutEndTo(truncateTime)
                    dummyExt.iobCalc(time, profile, activePlugin.activeInsulin)
                } else {
                    e.iobCalc(time, profile, activePlugin.activeInsulin)
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
        val utcOffset = T.msecs(TimeZone.getDefault().getOffset(dateUtil.now()).toLong()).hours()
        try {
            for (bgreading in glucose) {
                val bgjson = JSONObject()
                bgjson.put("_id", bgreading.interfaceIDs.nightscoutId ?:bgreading.timestamp.toString())
                bgjson.put("device", "AndroidAPS")
                bgjson.put("date", bgreading.timestamp)
                bgjson.put("dateString", dateUtil.toISOString(bgreading.timestamp))
                bgjson.put("sgv", bgreading.value)
                bgjson.put("direction", bgreading.trendArrow)
                bgjson.put("type", "sgv")
                bgjson.put("systime", dateUtil.toISOString(bgreading.timestamp))
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
        var eventType: TherapyEvent.Type? = null
        var created_at: String? = null

        // treatment properties
        var carbsTreatment: Carbs? = null
        var bolusTreatment: Bolus? = null
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
        var careportalEvent: TherapyEvent? = null
        var json: String? = null

        constructor(t: Carbs) {
            carbsTreatment = t
            _id = t.interfaceIDs.nightscoutId ?:t.timestamp.toString()
            date = t.timestamp
            carbs = t.amount
            insulin = 0.0
            isSMB = false
            isValid = t.isValid
            mealBolus = false // t.mealBolus
            eventType = TherapyEvent.Type.CARBS_CORRECTION  //if (insulin > 0 && carbs > 0) CareportalEvent.BOLUSWIZARD else if (carbs > 0) CareportalEvent.CARBCORRECTION else CareportalEvent
                // .CORRECTIONBOLUS
            created_at = dateUtil.toISOString(t.timestamp)
        }

        constructor(t: Bolus) {
            bolusTreatment = t
            _id = t.interfaceIDs.nightscoutId ?:t.timestamp.toString()
            date = t.timestamp
            carbs = 0.0
            insulin = t.amount
            isSMB = t.type == Bolus.Type.SMB
            isValid = t.isValid
            mealBolus = false //t.mealBolus
            eventType = TherapyEvent.Type.CORRECTION_BOLUS //if (insulin > 0 && carbs > 0) CareportalEvent.BOLUSWIZARD else if (carbs > 0) CareportalEvent.CARBCORRECTION else CareportalEvent
            // .CORRECTIONBOLUS
            created_at = dateUtil.toISOString(t.timestamp)
        }

        constructor(t: TherapyEvent) {
            careportalEvent = t
            _id = t.interfaceIDs.nightscoutId ?:t.timestamp.toString()
            date = t.timestamp
            created_at = dateUtil.toISOString(t.timestamp)
            eventType = t.type
            duration = Math.round(t.duration / 60f / 1000).toInt()
            isValid = t.isValid
            json = t.toJson(true, dateUtil).toString(2)
        }

        constructor(t: TemporaryBasal) {
            temporaryBasal = t
            isFakeExtended = false
            _NsTreatment(t)
        }

        constructor(t: ExtendedBolus) {
            extendedBolus = t
            val profile = profileFunction.getProfile(date)
            isFakeExtended = true
            _NsTreatment(t.toTemporaryBasal(profile!!))
        }

        private fun _NsTreatment(t: TemporaryBasal) {
            _id = t.interfaceIDs.nightscoutId ?:t.timestamp.toString()
            date = t.timestamp
            if (t.isAbsolute)
                absoluteRate = Round.roundTo(t.rate, 0.001)
            else {
                val profile = profileFunction.getProfile(date)
                absoluteRate = Round.roundTo(profile!!.getBasal(temporaryBasal!!.timestamp) * temporaryBasal!!.rate / 100, 0.001)
            }
            isValid = t.isValid
            eventType = TherapyEvent.Type.TEMPORARY_BASAL
            enteredBy = "openaps://" + resourceHelper.gs(R.string.app_name)
            duration = t.durationInMinutes.toInt()
            created_at = dateUtil.toISOString(t.timestamp)
            isAbsolute = true
        }

        fun toJson(): JSONObject {
            val cPjson = JSONObject()
            try {
                cPjson.put("_id", _id)
                cPjson.put("eventType", eventType!!.text)
                cPjson.put("date", date)
                cPjson.put("created_at", created_at)
                cPjson.put("insulin", if (insulin > 0) insulin else JSONObject.NULL)
                cPjson.put("carbs", if (carbs > 0) carbs else JSONObject.NULL)
                if (eventType === TherapyEvent.Type.TEMPORARY_BASAL) {
                    cPjson.put("duration", duration)
                    //cPjson.put("absolute", absoluteRate)
                    cPjson.put("rate", absoluteRate)
                    cPjson.put("isFakeExtended", isFakeExtended)
                    cPjson.put("enteredBy", enteredBy)
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