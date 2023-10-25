package app.aaps.plugins.aps.autotune

import app.aaps.plugins.aps.autotune.data.BGDatum
import app.aaps.plugins.aps.autotune.data.CRDatum
import app.aaps.plugins.aps.autotune.data.PreppedGlucose
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class PreppedGlucoseTest : TestBaseWithProfile() {

    private lateinit var prep1: PreppedGlucose
    private lateinit var prepJson1: String

    @BeforeEach
    fun initData() {
        prepJson1 = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        prep1 = PreppedGlucose(JSONObject(prepJson1), dateUtil)
    }

    @Test
    fun preppedGlucoseLoadTest() { // Test if load from file of OpenAPS categorisation is Ok
        val crData0 =
            CRDatum(
                JSONObject("{\"CRInitialIOB\":13.594,\"CRInitialBG\":123,\"CRInitialCarbTime\":\"2022-05-21T07:54:09.000Z\",\"CREndIOB\":-0.155,\"CREndBG\":98,\"CREndTime\":\"2022-05-21T11:19:08.000Z\",\"CRCarbs\":70,\"CRInsulin\":-2.13}"),
                dateUtil
            )
        val csfDataEnd =
            BGDatum(
                JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653176050000,\"dateString\":\"2022-05-21T23:34:10.000Z\",\"isValid\":true,\"sgv\":127,\"direction\":\"Flat\",\"type\":\"sgv\",\"_id\":\"6289771371a363000480abc1\",\"glucose\":127,\"avgDelta\":\"2.50\",\"BGI\":-2.93,\"deviation\":\"5.43\",\"mealCarbs\":0,\"mealAbsorption\":\"end\"}"),
                dateUtil
            )
        val isfData0 =
            BGDatum(
                JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653108249000,\"dateString\":\"2022-05-21T04:44:09.000Z\",\"isValid\":true,\"sgv\":123,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"_id\":\"62886e2919e2e60004989bba\",\"glucose\":123,\"avgDelta\":\"-7.50\",\"BGI\":-7.59,\"deviation\":\"0.09\"}"),
                dateUtil
            )
        val basalDataEnd =
            BGDatum(
                JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653180549000,\"dateString\":\"2022-05-22T00:49:09.000Z\",\"isValid\":true,\"sgv\":121,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"_id\":\"628988a3da46aa0004d1e0f5\",\"glucose\":121,\"avgDelta\":\"-5.25\",\"BGI\":-3.32,\"deviation\":\"-1.93\"}"),
                dateUtil
            )
        assertThat(prep1.crData).hasSize(3)
        assertThat(crData0.equals(prep1.crData[0])).isTrue()
        assertThat(csfDataEnd.equals(prep1.csfGlucoseData[prep1.csfGlucoseData.size - 1])).isTrue()
        assertThat(isfData0.equals(prep1.isfGlucoseData[0])).isTrue()
        assertThat(basalDataEnd.equals(prep1.basalGlucoseData[prep1.basalGlucoseData.size - 1])).isTrue()
    }
}
