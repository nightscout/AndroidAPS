package info.nightscout.plugins.general.autotune

import info.nightscout.core.extensions.convertedToAbsolute
import info.nightscout.core.extensions.durationInMinutes
import info.nightscout.core.extensions.toJson
import info.nightscout.core.extensions.toTemporaryBasal
import info.nightscout.core.iob.round
import info.nightscout.core.utils.MidnightUtils
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.Carbs
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.iob.Iob
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.utils.Round
import info.nightscout.plugins.general.autotune.data.ATProfile
import info.nightscout.plugins.general.autotune.data.LocalInsulin
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

@Singleton
open class AutotuneIob @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val repository: AppRepository,
    private val profileFunction: ProfileFunction,
    private val sp: SP,
    private val dateUtil: DateUtil,
    private val autotuneFS: AutotuneFS
) {

    private var nsTreatments = ArrayList<NsTreatment>()
    private var dia: Double = Constants.defaultDIA
    var boluses: ArrayList<Bolus> = ArrayList()
    var meals = ArrayList<Carbs>()
    lateinit var glucose: List<GlucoseValue> // newest at index 0
    private lateinit var tempBasals: ArrayList<TemporaryBasal>
    var startBG: Long = 0
    private var endBG: Long = 0
    private fun range(): Long = (60 * 60 * 1000L * dia + T.hours(2).msecs()).toLong()

    fun initializeData(from: Long, to: Long, tunedProfile: ATProfile) {
        dia = tunedProfile.dia
        startBG = from
        endBG = to
        nsTreatments.clear()
        meals.clear()
        boluses.clear()
        tempBasals = ArrayList()
        if (profileFunction.getProfile(from - range()) == null)
            return
        initializeBgReadings(from, to)
        initializeTreatmentData(from - range(), to)
        initializeTempBasalData(from - range(), to, tunedProfile)
        initializeExtendedBolusData(from - range(), to, tunedProfile)
        sortTempBasal()
        addNeutralTempBasal(from - range(), to, tunedProfile)        // Without Neutral TBR, Autotune Web will ignore iob for periods without TBR running
        sortNsTreatments()
        sortBoluses()
        aapsLogger.debug(LTag.AUTOTUNE, "Nb Treatments: " + nsTreatments.size + " Nb meals: " + meals.size)
    }

    @Synchronized
    private fun sortTempBasal() {
        tempBasals = ArrayList(tempBasals.toList().sortedWith { o1: TemporaryBasal, o2: TemporaryBasal -> (o2.timestamp - o1.timestamp).toInt() })
    }

    @Synchronized
    private fun sortNsTreatments() {
        nsTreatments = ArrayList(nsTreatments.toList().sortedWith { o1: NsTreatment, o2: NsTreatment -> (o2.date - o1.date).toInt() })
    }

    @Synchronized
    private fun sortBoluses() {
        boluses = ArrayList(boluses.toList().sortedWith { o1: Bolus, o2: Bolus -> (o2.timestamp - o1.timestamp).toInt() })
    }

    private fun initializeBgReadings(from: Long, to: Long) {
        glucose = repository.compatGetBgReadingsDataFromTime(from, to, false).blockingGet()
    }

    //nsTreatment is used only for export data, meals is used in AutotunePrep
    private fun initializeTreatmentData(from: Long, to: Long) {
        val oldestBgDate = if (glucose.isNotEmpty()) glucose[glucose.size - 1].timestamp else from
        aapsLogger.debug(
            LTag.AUTOTUNE,
            "Check BG date: BG Size: " + glucose.size + " OldestBG: " + dateUtil.dateAndTimeAndSecondsString(oldestBgDate) + " to: " + dateUtil.dateAndTimeAndSecondsString(to)
        )
        val tmpCarbs = repository.getCarbsDataFromTimeToTimeExpanded(from, to, false).blockingGet()
        aapsLogger.debug(LTag.AUTOTUNE, "Nb treatments after query: " + tmpCarbs.size)
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
        val tmpBolus = repository.getBolusesDataFromTimeToTime(from, to, false).blockingGet()
        var nbSMB = 0
        var nbBolus = 0
        for (i in tmpBolus.indices) {
            val tp = tmpBolus[i]
            if (tp.isValid && tp.type != Bolus.Type.PRIMING) {
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
        //log.debug("AutotunePlugin Nb Meals: $nbCarbs Nb Bolus: $nbBolus Nb SMB: $nbSMB")
    }

    //nsTreatment is used only for export data
    private fun initializeTempBasalData(from: Long, to: Long, tunedProfile: ATProfile) {
        val tBRs = repository.getTemporaryBasalsDataFromTimeToTime(from, to, false).blockingGet()
        //log.debug("D/AutotunePlugin tempBasal size before cleaning:" + tBRs.size);
        for (i in tBRs.indices) {
            if (tBRs[i].isValid)
                toSplittedTimestampTB(tBRs[i], tunedProfile)
        }
        //log.debug("D/AutotunePlugin: tempBasal size: " + tempBasals.size)
    }

    //nsTreatment is used only for export data
    private fun initializeExtendedBolusData(from: Long, to: Long, tunedProfile: ATProfile) {
        val extendedBoluses = repository.getExtendedBolusDataFromTimeToTime(from, to, false).blockingGet()
        for (i in extendedBoluses.indices) {
            val eb = extendedBoluses[i]
            if (eb.isValid)
                if (eb.isEmulatingTempBasal) {
                    profileFunction.getProfile(eb.timestamp)?.let {
                        toSplittedTimestampTB(eb.toTemporaryBasal(it), tunedProfile)
                    }
                } else {
                    nsTreatments.add(NsTreatment(eb))
                    boluses.addAll(convertToBoluses(eb))
                }
        }
    }

    // addNeutralTempBasal will add a fake neutral TBR (100%) to have correct basal rate in exported file for periods without TBR running
    // to be able to compare results between oref0 algo and aaps
    @Synchronized
    private fun addNeutralTempBasal(from: Long, to: Long, tunedProfile: ATProfile) {
        var previousStart = to
        for (i in tempBasals.indices) {
            val newStart = tempBasals[i].timestamp + tempBasals[i].duration
            if (previousStart - newStart > T.mins(1).msecs()) {                  // fill neutral only if more than 1 min
                val neutralTbr = TemporaryBasal(
                    isValid = true,
                    isAbsolute = false,
                    timestamp = newStart,
                    rate = 100.0,
                    duration = previousStart - newStart,
                    interfaceIDs_backing = InterfaceIDs(nightscoutId = "neutral_$newStart"),
                    type = TemporaryBasal.Type.NORMAL
                )
                toSplittedTimestampTB(neutralTbr, tunedProfile)
            }
            previousStart = tempBasals[i].timestamp
        }
        if (previousStart - from > T.mins(1).msecs()) {                         // fill neutral only if more than 1 min
            val neutralTbr = TemporaryBasal(
                isValid = true,
                isAbsolute = false,
                timestamp = from,
                rate = 100.0,
                duration = previousStart - from,
                interfaceIDs_backing = InterfaceIDs(nightscoutId = "neutral_$from"),
                type = TemporaryBasal.Type.NORMAL
            )
            toSplittedTimestampTB(neutralTbr, tunedProfile)
        }
    }

    // toSplittedTimestampTB will split all TBR across hours in different TBR with correct absolute value to be sure to have correct basal rate
    // even if profile rate is not the same
    @Synchronized
    private fun toSplittedTimestampTB(tb: TemporaryBasal, tunedProfile: ATProfile) {
        var splittedTimestamp = tb.timestamp
        val cutInMilliSec = T.mins(60).msecs()                  //30 min to compare with oref0, 60 min to improve accuracy
        var splittedDuration = tb.duration
        if (tb.isValid && tb.durationInMinutes > 0) {
            val endTimestamp = splittedTimestamp + splittedDuration
            while (splittedDuration > 0) {
                if (MidnightUtils.milliSecFromMidnight(splittedTimestamp) / cutInMilliSec == MidnightUtils.milliSecFromMidnight(endTimestamp) / cutInMilliSec) {
                    val newTb = TemporaryBasal(
                        isValid = true,
                        isAbsolute = tb.isAbsolute,
                        timestamp = splittedTimestamp,
                        rate = tb.rate,
                        duration = splittedDuration,
                        interfaceIDs_backing = tb.interfaceIDs_backing,
                        type = tb.type
                    )
                    tempBasals.add(newTb)
                    nsTreatments.add(NsTreatment(newTb))
                    splittedDuration = 0
                    val profile = profileFunction.getProfile(newTb.timestamp) ?: continue
                    boluses.addAll(convertToBoluses(newTb, profile, tunedProfile.profile))           //
                    // required for correct iob calculation with oref0 algo
                } else {
                    val durationFilled = (cutInMilliSec - MidnightUtils.milliSecFromMidnight(splittedTimestamp) % cutInMilliSec)
                    val newTb = TemporaryBasal(
                        isValid = true,
                        isAbsolute = tb.isAbsolute,
                        timestamp = splittedTimestamp,
                        rate = tb.rate,
                        duration = durationFilled,
                        interfaceIDs_backing = tb.interfaceIDs_backing,
                        type = tb.type
                    )
                    tempBasals.add(newTb)
                    nsTreatments.add(NsTreatment(newTb))
                    splittedTimestamp += durationFilled
                    splittedDuration -= durationFilled
                    val profile = profileFunction.getProfile(newTb.timestamp) ?: continue
                    boluses.addAll(convertToBoluses(newTb, profile, tunedProfile.profile))           // required for correct iob calculation with oref0 algo
                }
            }
        }
    }

    open fun getIOB(time: Long, localInsulin: LocalInsulin): IobTotal =
        getCalculationToTimeTreatments(time, localInsulin).round()

    // Add specific calculation for Autotune (reference localInsulin for Peak/dia)
    private fun Bolus.iobCalc(time: Long, localInsulin: LocalInsulin): Iob {
        if (!isValid  || type == Bolus.Type.PRIMING ) return Iob()
        return localInsulin.iobCalcForTreatment(this, time)
    }
    private fun getCalculationToTimeTreatments(time: Long, localInsulin: LocalInsulin): IobTotal {
        val total = IobTotal(time)
        val detailedLog = sp.getBoolean(info.nightscout.core.utils.R.string.key_autotune_additional_log, false)
        for (pos in boluses.indices) {
            val t = boluses[pos]
            if (!t.isValid) continue
            if (t.timestamp > time || t.timestamp < time - localInsulin.duration) continue
            val tIOB = t.iobCalc(time, localInsulin)
            if (detailedLog)
                log(
                    "iobCalc;${t.interfaceIDs.nightscoutId};$time;${t.timestamp};${tIOB.iobContrib};${tIOB.activityContrib};${dateUtil.dateAndTimeAndSecondsString(time)};${
                        dateUtil.dateAndTimeAndSecondsString(
                            t.timestamp
                        )
                    }"
                )
            total.iob += tIOB.iobContrib
            total.activity += tIOB.activityContrib
        }
        return total
    }

    private fun convertToBoluses(eb: ExtendedBolus): MutableList<Bolus> {
        val result: MutableList<Bolus> = ArrayList()
        val aboutFiveMinIntervals = ceil(eb.duration / 5.0).toInt()
        val spacing = eb.duration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (eb.timestamp + j * spacing * 60 * 1000 + 0.5 * spacing * 60 * 1000).toLong()
            val tempBolusSize: Double = eb.amount / aboutFiveMinIntervals
            val bolusInterfaceIDs = InterfaceIDs().also { it.nightscoutId = eb.interfaceIDs.nightscoutId + "_eb_$j" }
            val tempBolusPart = Bolus(
                interfaceIDs_backing = bolusInterfaceIDs,
                timestamp = calcDate,
                amount = tempBolusSize,
                type = Bolus.Type.NORMAL
            )
            result.add(tempBolusPart)
        }
        return result
    }

    private fun convertToBoluses(tbr: TemporaryBasal, profile: Profile, tunedProfile: Profile): MutableList<Bolus> {
        val result: MutableList<Bolus> = ArrayList()
        val realDuration = tbr.durationInMinutes
        val basalRate = profile.getBasal(tbr.timestamp)
        val tunedRate = tunedProfile.getBasal(tbr.timestamp)
        val netBasalRate = Round.roundTo(
            if (tbr.isAbsolute) {
                tbr.rate - tunedRate
            } else {
                tbr.rate / 100.0 * basalRate - tunedRate
            }, 0.001
        )
        val aboutFiveMinIntervals = ceil(realDuration / 5.0).toInt()
        val tempBolusSpacing = realDuration / aboutFiveMinIntervals.toDouble()
        for (j in 0L until aboutFiveMinIntervals) {
            // find middle of the interval
            val calcDate = (tbr.timestamp + j * tempBolusSpacing * 60 * 1000 + 0.5 * tempBolusSpacing * 60 * 1000).toLong()
            val tempBolusSize = netBasalRate * tempBolusSpacing / 60.0
            val bolusInterfaceIDs = InterfaceIDs().also { it.nightscoutId = tbr.interfaceIDs.nightscoutId + "_tbr_$j" }
            val tempBolusPart = Bolus(
                interfaceIDs_backing = bolusInterfaceIDs,
                timestamp = calcDate,
                amount = tempBolusSize,
                type = Bolus.Type.NORMAL
            )
            result.add(tempBolusPart)
        }
        return result
    }

    fun Bolus.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
        JSONObject()
            .put("eventType", if (type == Bolus.Type.SMB) TherapyEvent.Type.CORRECTION_BOLUS.text else info.nightscout.database.entities.TherapyEvent.Type.MEAL_BOLUS.text)
            .put("insulin", amount)
            .put("created_at", dateUtil.toISOString(timestamp))
            .put("date", timestamp)
            .put("type", type.name)
            .put("notes", notes)
            .put("isValid", isValid)
            .put("isSMB", type == Bolus.Type.SMB).also {
                if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
                if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
                if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
                if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
            }

    @Synchronized
    fun glucoseToJSON(): String {
        val glucoseJson = JSONArray()
        for (bgReading in glucose)
            glucoseJson.put(bgReading.toJson(true, dateUtil))
        return glucoseJson.toString(2)
    }

    @Synchronized
    fun bolusesToJSON(): String {
        val bolusesJson = JSONArray()
        for (bolus in boluses)
            bolusesJson.put(bolus.toJson(true, dateUtil))
        return bolusesJson.toString(2)
    }

    @Synchronized
    fun nsHistoryToJSON(): String {
        val json = JSONArray()
        for (t in nsTreatments) {
            json.put(t.toJson())
        }
        return json.toString(2).replace("\\/", "/")
    }

    //I add this internal class to be able to export easily ns-treatment files with same contain and format than NS query used by oref0-autotune
    private inner class NsTreatment {

        var date: Long = 0
        var eventType: TherapyEvent.Type? = null
        var carbsTreatment: Carbs? = null
        var bolusTreatment: Bolus? = null
        var temporaryBasal: TemporaryBasal? = null
        var extendedBolus: ExtendedBolus? = null

        constructor(t: Carbs) {
            carbsTreatment = t
            date = t.timestamp
            eventType = TherapyEvent.Type.CARBS_CORRECTION
        }

        constructor(t: Bolus) {
            bolusTreatment = t
            date = t.timestamp
            eventType = TherapyEvent.Type.CORRECTION_BOLUS
        }

        constructor(t: TemporaryBasal) {
            temporaryBasal = t
            date = t.timestamp
            eventType = TherapyEvent.Type.TEMPORARY_BASAL
        }

        constructor(t: ExtendedBolus) {
            extendedBolus = t
            date = t.timestamp
            eventType = TherapyEvent.Type.COMBO_BOLUS
        }

        fun TemporaryBasal.toJson(isAdd: Boolean, profile: Profile, dateUtil: DateUtil): JSONObject =
            JSONObject()
                .put("created_at", dateUtil.toISOString(timestamp))
                .put("enteredBy", "openaps://" + "AndroidAPS")
                .put("eventType", info.nightscout.database.entities.TherapyEvent.Type.TEMPORARY_BASAL.text)
                .put("isValid", isValid)
                .put("duration", T.msecs(duration).mins())
                .put("durationInMilliseconds", duration) // rounded duration leads to different basal IOB
                .put("type", type.name)
                .put("rate", convertedToAbsolute(timestamp, profile)) // generated by OpenAPS, for compatibility
                .also {
                    if (isAbsolute) it.put("absolute", rate)
                    else it.put("percent", rate - 100)
                    if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
                    if (interfaceIDs.endId != null) it.put("endId", interfaceIDs.endId)
                    if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
                    if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
                    if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
                }

        fun ExtendedBolus.toJson(isAdd: Boolean, profile: Profile, dateUtil: DateUtil): JSONObject =
            if (isEmulatingTempBasal)
                toTemporaryBasal(profile)
                    .toJson(isAdd, profile, dateUtil)
                    .put("extendedEmulated", toRealJson(isAdd, dateUtil))
            else toRealJson(isAdd, dateUtil)

        fun ExtendedBolus.toRealJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
            JSONObject()
                .put("created_at", dateUtil.toISOString(timestamp))
                .put("enteredBy", "openaps://" + "AndroidAPS")
                .put("eventType", info.nightscout.database.entities.TherapyEvent.Type.COMBO_BOLUS.text)
                .put("duration", T.msecs(duration).mins())
                .put("durationInMilliseconds", duration)
                .put("splitNow", 0)
                .put("splitExt", 100)
                .put("enteredinsulin", amount)
                .put("relative", rate)
                .put("isValid", isValid)
                .put("isEmulatingTempBasal", isEmulatingTempBasal)
                .also {
                    if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
                    if (interfaceIDs.endId != null) it.put("endId", interfaceIDs.endId)
                    if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
                    if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
                    if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
                }

        fun Carbs.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
            JSONObject()
                .put("eventType", if (amount < 12) info.nightscout.database.entities.TherapyEvent.Type.CARBS_CORRECTION.text else info.nightscout.database.entities.TherapyEvent.Type.MEAL_BOLUS.text)
                .put("carbs", amount)
                .put("notes", notes)
                .put("created_at", dateUtil.toISOString(timestamp))
                .put("isValid", isValid)
                .put("date", timestamp).also {
                    if (duration != 0L) it.put("duration", duration)
                    if (interfaceIDs.pumpId != null) it.put("pumpId", interfaceIDs.pumpId)
                    if (interfaceIDs.pumpType != null) it.put("pumpType", interfaceIDs.pumpType!!.name)
                    if (interfaceIDs.pumpSerial != null) it.put("pumpSerial", interfaceIDs.pumpSerial)
                    if (isAdd && interfaceIDs.nightscoutId != null) it.put("_id", interfaceIDs.nightscoutId)
                }

        fun toJson(): JSONObject? {
            val cpJson = JSONObject()
            return when (eventType) {
                TherapyEvent.Type.TEMPORARY_BASAL  ->
                    temporaryBasal?.let { tbr ->
                        val profile = profileFunction.getProfile(tbr.timestamp)
                        profile?.let {
                            tbr.toJson(true, it, dateUtil)
                        }
                    }

                TherapyEvent.Type.COMBO_BOLUS      ->
                    extendedBolus?.let { ebr ->
                        val profile = profileFunction.getProfile(ebr.timestamp)
                        profile?.let {
                            ebr.toJson(true, it, dateUtil)
                        }
                    }

                TherapyEvent.Type.CORRECTION_BOLUS -> bolusTreatment?.toJson(true, dateUtil)
                TherapyEvent.Type.CARBS_CORRECTION -> carbsTreatment?.toJson(true, dateUtil)
                else                               -> cpJson
            }
        }
    }

    private fun log(message: String) {
        autotuneFS.atLog("[iob] $message")
    }
}