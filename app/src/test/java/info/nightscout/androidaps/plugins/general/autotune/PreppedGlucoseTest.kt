package info.nightscout.androidaps.plugins.general.autotune

import android.content.Context
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.utils.DateUtil
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import java.io.File

class PreppedGlucoseTest : TestBase() {
    @Mock lateinit var context: Context
    lateinit var dateUtil: DateUtil
    lateinit var prep1: PreppedGlucose
    lateinit var prepjson1: String

    @Before
    fun initData() {
        dateUtil = DateUtil(context)
        prepjson1 = File("src/test/res/autotune/test1/autotune.2022-05-21.json").readText()
        prep1 = PreppedGlucose(JSONObject(prepjson1), dateUtil)
    }

    @Test
    fun preppedGlucoseLoadTest() { // Test if load from file of OpenAPS categorisation is Ok
        val crData0 = CRDatum(JSONObject("{\"CRInitialIOB\":13.594,\"CRInitialBG\":123,\"CRInitialCarbTime\":\"2022-05-21T07:54:09.000Z\",\"CREndIOB\":-0.155,\"CREndBG\":98,\"CREndTime\":\"2022-05-21T11:19:08.000Z\",\"CRCarbs\":70,\"CRInsulin\":-2.13}"), dateUtil)
        val csfDataEnd = BGDatum(JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653176050000,\"dateString\":\"2022-05-21T23:34:10.000Z\",\"isValid\":true,\"sgv\":127,\"direction\":\"Flat\",\"type\":\"sgv\",\"_id\":\"6289771371a363000480abc1\",\"glucose\":127,\"avgDelta\":\"2.50\",\"BGI\":-2.93,\"deviation\":\"5.43\",\"mealCarbs\":0,\"mealAbsorption\":\"end\"}"), dateUtil)
        val isfData0 = BGDatum(JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653108249000,\"dateString\":\"2022-05-21T04:44:09.000Z\",\"isValid\":true,\"sgv\":123,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"_id\":\"62886e2919e2e60004989bba\",\"glucose\":123,\"avgDelta\":\"-7.50\",\"BGI\":-7.59,\"deviation\":\"0.09\"}"), dateUtil)
        val basalDataEnd = BGDatum(JSONObject("{\"device\":\"AndroidAPS-DexcomG6\",\"date\":1653180549000,\"dateString\":\"2022-05-22T00:49:09.000Z\",\"isValid\":true,\"sgv\":121,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"_id\":\"628988a3da46aa0004d1e0f5\",\"glucose\":121,\"avgDelta\":\"-5.25\",\"BGI\":-3.32,\"deviation\":\"-1.93\"}"), dateUtil)
        Assert.assertEquals(3, prep1.crData.size)
        Assert.assertTrue(crData0.equals(prep1.crData[0]))
        Assert.assertTrue(csfDataEnd.equals(prep1.csfGlucoseData[prep1.csfGlucoseData.size - 1]))
        Assert.assertTrue(isfData0.equals(prep1.isfGlucoseData[0]))
        Assert.assertTrue(basalDataEnd.equals(prep1.basalGlucoseData[prep1.basalGlucoseData.size - 1]))
    }
}
