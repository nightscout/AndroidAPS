package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.shiftBlock
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.plugins.aps.autotune.data.LocalInsulin
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.TimeZone

class AutotunePrepTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var persistenceLayer: PersistenceLayer
    private lateinit var autotunePrep: AutotunePrep
    private lateinit var autotuneIob: TestAutotuneIob
    private var ts = 0
    private var min5mCarbImpact = 0.0
    private var autotuneMin = 0.0
    private var autotuneMax = 0.0
    private var startDayTime = 0L

    @BeforeEach
    fun initData() {
        ts = T.msecs(TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()).hours().toInt() - 2
    }

    @Test
    fun autotunePrepTest1() { // Test if categorisation with standard treatments with carbs is Ok
        val inputIobJson = File("src/test/res/autotune/test1/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, persistenceLayer, profileFunction, preferences, dateUtil, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(preferences, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test1/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test1/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test1/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(min5mCarbImpact)
        whenever(preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal)).thenReturn(false)
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)!!
        // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
        for (i in aapsPreppedGlucose.crData.indices)
            assertThat(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i])).isTrue()
        for (i in aapsPreppedGlucose.csfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.isfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.basalGlucoseData.indices)
            assertThat(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i])).isTrue()
    }

    @Test
    fun autotunePrepTest2() { // Test if categorisation without carbs (full UAM) and categorize UAM as basal false is Ok
        val inputIobJson = File("src/test/res/autotune/test2/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, persistenceLayer, profileFunction, preferences, dateUtil, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(preferences, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test2/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test2/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test2/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test2/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(min5mCarbImpact)
        whenever(preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal)).thenReturn(false)           // CategorizeUAM as Basal = False
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)!!
        // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
        for (i in aapsPreppedGlucose.crData.indices)
            assertThat(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i])).isTrue()
        for (i in aapsPreppedGlucose.csfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.isfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.basalGlucoseData.indices)
            assertThat(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i])).isTrue()
    }

    @Test
    fun autotunePrepTest3() { // Test if categorisation without carbs (full UAM) and categorize UAM as basal true is Ok
        val inputIobJson = File("src/test/res/autotune/test3/oaps-iobCalc.2022-05-21.json").readText() //json files build with iob/activity calculated by OAPS
        val iobOapsCalculation = buildIobOaps(JSONArray(inputIobJson))
        autotuneIob = TestAutotuneIob(aapsLogger, persistenceLayer, profileFunction, preferences, dateUtil, autotuneFS, iobOapsCalculation)
        autotunePrep = AutotunePrep(preferences, dateUtil, autotuneFS, autotuneIob)
        val inputProfileJson = File("src/test/res/autotune/test3/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prepJson = File("src/test/res/autotune/test3/autotune.2022-05-21.json").readText()
        val oapsPreppedGlucose = PreppedGlucose(JSONObject(prepJson), dateUtil) //prep data calculated by OpenAPS autotune
        val oapsEntriesJson = File("src/test/res/autotune/test3/aaps-entries.2022-05-21.json").readText()
        autotuneIob.glucose = buildGlucose(JSONArray(oapsEntriesJson))
        val oapsTreatmentsJson = File("src/test/res/autotune/test3/aaps-treatments.2022-05-21.json").readText()
        autotuneIob.meals = buildMeals(JSONArray(oapsTreatmentsJson))  //Only meals is used in unit test, Insulin only used for iob calculation
        autotuneIob.boluses = buildBoluses(oapsPreppedGlucose) //Values from oapsPrepData because linked to iob calculation method for TBR
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(min5mCarbImpact)
        whenever(preferences.get(BooleanKey.AutotuneCategorizeUamAsBasal)).thenReturn(true)           // CategorizeUAM as Basal = True
        val aapsPreppedGlucose = autotunePrep.categorizeBGDatums(inputProfile, inputProfile.localInsulin, false)!!
        // compare all categorization calculated by aaps plugin (aapsPreppedGlucose) with categorization calculated by OpenAPS (oapsPreppedGlucose)
        for (i in aapsPreppedGlucose.crData.indices)
            assertThat(oapsPreppedGlucose.crData[i].equals(aapsPreppedGlucose.crData[i])).isTrue()
        for (i in aapsPreppedGlucose.csfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.csfGlucoseData[i].equals(aapsPreppedGlucose.csfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.isfGlucoseData = oapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.isfGlucoseData = aapsPreppedGlucose.isfGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.isfGlucoseData.indices)
            assertThat(oapsPreppedGlucose.isfGlucoseData[i].equals(aapsPreppedGlucose.isfGlucoseData[i])).isTrue()
        oapsPreppedGlucose.basalGlucoseData = oapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        aapsPreppedGlucose.basalGlucoseData = aapsPreppedGlucose.basalGlucoseData.sortedBy { it.date }
        for (i in aapsPreppedGlucose.basalGlucoseData.indices)
            assertThat(oapsPreppedGlucose.basalGlucoseData[i].equals(aapsPreppedGlucose.basalGlucoseData[i])).isTrue()
    }

    /*************************************************************************************************************************************************************************************
     * OpenAPS profile for Autotune only have one ISF value and one IC value
     */
    @Suppress("SpellCheckingInspection")
    private fun atProfileFromOapsJson(jsonObject: JSONObject, dateUtil: DateUtil, defaultUnits: String? = null): ATProfile? {
        try {
            min5mCarbImpact = JsonHelper.safeGetDoubleAllowNull(jsonObject, "min_5m_carbimpact") ?: return null
            autotuneMin = JsonHelper.safeGetDoubleAllowNull(jsonObject, "autosens_min") ?: return null
            autotuneMax = JsonHelper.safeGetDoubleAllowNull(jsonObject, "autosens_max") ?: return null
            val txtUnits = JsonHelper.safeGetStringAllowNull(jsonObject, "units", defaultUnits) ?: return null
            val units = GlucoseUnit.fromText(txtUnits)
            val dia = JsonHelper.safeGetDoubleAllowNull(jsonObject, "dia") ?: return null
            val peak = JsonHelper.safeGetIntAllowNull(jsonObject, "insulinPeakTime") ?: return null
            val localInsulin = LocalInsulin("insulin", peak, dia)
            val timezone = TimeZone.getTimeZone(JsonHelper.safeGetString(jsonObject, "timezone", "UTC"))
            val isfJson = jsonObject.getJSONObject("isfProfile")
            val isfBlocks = ArrayList<Block>(1).also {
                val isfJsonArray = isfJson.getJSONArray("sensitivities")
                val value = isfJsonArray.getJSONObject(0).getDouble("sensitivity")
                it.add(0, Block((T.hours(24).secs()) * 1000L, value))
            }
            val icBlocks = ArrayList<Block>(1).also {
                val value = jsonObject.getDouble("carb_ratio")
                it.add(0, Block((T.hours(24).secs()) * 1000L, value))
            }
            val basalBlocks = blockFromJsonArray(jsonObject.getJSONArray("basalprofile"))
                ?: return null
            val targetBlocks = ArrayList<TargetBlock>(1).also {
                it.add(0, TargetBlock((T.hours(24).secs()) * 1000L, 100.0, 100.0))
            }

            val pure = PureProfile(
                jsonObject = jsonObject,
                basalBlocks = basalBlocks.shiftBlock(1.0, ts),
                isfBlocks = isfBlocks,
                icBlocks = icBlocks,
                targetBlocks = targetBlocks,
                glucoseUnit = units,
                timeZone = timezone
            )
            return ATProfile(preferences, profileUtil, dateUtil, rh, profileStoreProvider, aapsLogger).with(ProfileSealed.Pure(pure, activePlugin), localInsulin)
        } catch (_: Exception) {
            return null
        }
    }

    private fun blockFromJsonArray(jsonArray: JSONArray?): List<Block>? {
        val size = jsonArray?.length() ?: return null
        val ret = ArrayList<Block>(size)
        try {
            for (index in 0 until jsonArray.length() - 1) {
                val o = jsonArray.getJSONObject(index)
                val tas = o.getInt("minutes") * 60
                val next = jsonArray.getJSONObject(index + 1)
                val nextTas = next.getInt("minutes") * 60
                val value = o.getDouble("rate")
                if (tas % 3600 != 0) return null
                if (nextTas % 3600 != 0) return null
                ret.add(index, Block((nextTas - tas) * 1000L, value))
            }
            val last: JSONObject = jsonArray.getJSONObject(jsonArray.length() - 1)
            val lastTas = last.getInt("minutes") * 60
            val value = last.getDouble("rate")
            ret.add(jsonArray.length() - 1, Block((T.hours(24).secs() - lastTas) * 1000L, value))
        } catch (_: Exception) {
            return null
        }
        return ret
    }

    private fun buildBoluses(preppedGlucose: PreppedGlucose): ArrayList<BS> { //if categorization is correct then I return for dose function the crInsulin calculated in Oaps
        val boluses: ArrayList<BS> = ArrayList()
        for (i in preppedGlucose.crData.indices) {
            boluses.add(
                BS(
                    timestamp = preppedGlucose.crData[i].crEndTime,
                    amount = preppedGlucose.crData[i].crInsulin,
                    type = BS.Type.NORMAL,
                    iCfg = ICfg(insulinLabel = "unused within Autotune", peak = 0, dia = 0.0, concentration = 0.0)
                )
            )
        }
        if (boluses.isEmpty())  //Add at least one insulin treatment for tests to avoid return null in categorization
            boluses.add(
                BS(
                    timestamp = startDayTime,
                    amount = 1.0,
                    type = BS.Type.NORMAL,
                    iCfg = ICfg(insulinLabel = "unused within Autotune", peak = 0, dia = 0.0, concentration = 0.0)
                )
            )
        return boluses
    }

    private fun buildMeals(jsonArray: JSONArray): ArrayList<CA> {
        val list: ArrayList<CA> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val value = JsonHelper.safeGetDouble(json, "carbs", 0.0)
            val timestamp = JsonHelper.safeGetLong(json, "date")
            if (value > 0.0 && timestamp > startDayTime) {
                list.add(CA(timestamp = timestamp, amount = value, duration = 0))
            }
        }
        return list
    }

    private fun buildGlucose(jsonArray: JSONArray): List<GV> {
        val list: ArrayList<GV> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val value = JsonHelper.safeGetDouble(json, "sgv")
            val timestamp = JsonHelper.safeGetLong(json, "date")
            list.add(GV(raw = value, noise = 0.0, value = value, timestamp = timestamp, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT))
        }
        if (list.isNotEmpty())
            startDayTime = list[list.size - 1].timestamp
        return list
    }

    private fun buildIobOaps(jsonArray: JSONArray): ArrayList<IobTotal> { //if categorization is correct then I return for dose function the crInsulin calculated in Oaps
        val list: ArrayList<IobTotal> = ArrayList()
        for (index in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(index)
            val time = JsonHelper.safeGetLong(json, "date")
            val iob = JsonHelper.safeGetDouble(json, "iob")
            val activity = JsonHelper.safeGetDouble(json, "activity")
            val iobTotal = IobTotal(time)
            iobTotal.iob = iob
            iobTotal.activity = activity
            list.add(iobTotal)
        }
        return list
    }

    class TestAutotuneIob(
        aapsLogger: AAPSLogger,
        persistenceLayer: PersistenceLayer,
        profileFunction: ProfileFunction,
        preferences: Preferences,
        dateUtil: DateUtil,
        autotuneFS: AutotuneFS,
        private val iobOapsCalculation: ArrayList<IobTotal>
    ) : AutotuneIob(
        aapsLogger,
        persistenceLayer,
        profileFunction,
        preferences,
        dateUtil,
        autotuneFS
    ) {

        override fun getIOB(time: Long, localInsulin: LocalInsulin): IobTotal {
            val bolusIob = IobTotal(time)
            iobOapsCalculation.forEach {
                if (it.time == time)
                    return it
            }
            return bolusIob
        }
    }
}
