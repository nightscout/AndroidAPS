package app.aaps.plugins.aps.autotune

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
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

class AutotuneCoreTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    private lateinit var autotuneCore: AutotuneCore
    private var min5mCarbImpact = 0.0
    private var autotuneMin = 0.0
    private var autotuneMax = 0.0

    @BeforeEach
    fun initData() {
        autotuneCore = AutotuneCore(preferences, autotuneFS)
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+2"))
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun autotuneCoreTest1() { // Test if AutotuneCore with input files of OpenAPS categorisation gives correct output profile
        val prepJson = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        val inputProfileJson = File("src/test/res/autotune/test1/profile.pump.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val prep = PreppedGlucose(JSONObject(prepJson), dateUtil)

        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(autotuneMax)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(autotuneMin)
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(min5mCarbImpact)
        val oapsOutputProfileJson = File("src/test/res/autotune/test1/aapsorefprofile.json").readText()
        val oapsOutputProfile = atProfileFromOapsJson(JSONObject(oapsOutputProfileJson), dateUtil)!!
        val outProfile = autotuneCore.tuneAllTheThings(prep, inputProfile, inputProfile)
        assertThat(outProfile.isf).isEqualTo(oapsOutputProfile.isf)
        assertThat(outProfile.ic).isEqualTo(oapsOutputProfile.ic)
        for (i in 0..23) {
            assertThat(outProfile.basal[i]).isEqualTo(oapsOutputProfile.basal[i])
        }
    }

    @Suppress("SpellCheckingInspection")
    @Test
    fun autotuneCoreTest4() { // Test if limiting modification with Min Max Autosens parameter works (18h on basal and on ISF value)
        val prepJson = File("src/test/res/autotune/test4/autotune.2022-05-30.json").readText()
        val inputProfileJson = File("src/test/res/autotune/test4/profile.2022-05-30.json").readText()
        val inputProfile = atProfileFromOapsJson(JSONObject(inputProfileJson), dateUtil)!!
        val pumpProfileJson = File("src/test/res/autotune/test4/profile.pump.json").readText()
        val pumpProfile = atProfileFromOapsJson(JSONObject(pumpProfileJson), dateUtil)!!
        val prep = PreppedGlucose(JSONObject(prepJson), dateUtil)
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(autotuneMax)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(autotuneMin)
        whenever(preferences.get(DoubleKey.ApsSmbMin5MinCarbsImpact)).thenReturn(min5mCarbImpact)
        val oapsOutputProfileJson = File("src/test/res/autotune/test4/newprofile.2022-05-30.json").readText()
        val oapsOutputProfile = atProfileFromOapsJson(JSONObject(oapsOutputProfileJson), dateUtil)!!
        val outProfile = autotuneCore.tuneAllTheThings(prep, inputProfile, pumpProfile)
        assertThat(outProfile.isf).isEqualTo(oapsOutputProfile.isf)
        assertThat(outProfile.ic).isEqualTo(oapsOutputProfile.ic)
        for (i in 0..23) {
            assertThat(outProfile.basal[i]).isEqualTo(oapsOutputProfile.basal[i])
        }
    }

    /**
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
                basalBlocks = basalBlocks,
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
}
