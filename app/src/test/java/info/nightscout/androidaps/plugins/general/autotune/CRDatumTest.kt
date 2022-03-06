package info.nightscout.androidaps.plugins.general.autotune

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.utils.DateUtil

import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class CRDatumTest : TestBase() {
    @Mock lateinit var dateUtil: DateUtil

    @Test
    fun crDatumEqualTest() {
        val prepjson1 = "{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}"
        //CRInitialCarbTime rounded to lower second
        val prepjson3 = "{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.000Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.300Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}"
        val prep1 = CRDatum(JSONObject(prepjson1), dateUtil)
        val prep2 = CRDatum(JSONObject(prepjson1), dateUtil)
        val prep4 = CRDatum(JSONObject(prepjson3), dateUtil)
        Assert.assertTrue(prep1.equals(prep2))
        Assert.assertTrue(prep1.equals(prep4))
    }

    @Test
    fun crDatumNotEqualTest() {
        val prepjson1 = "{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}"
        val prepjson2 = "{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":42,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}"
        val prep1 = CRDatum(JSONObject(prepjson1), dateUtil)
        val prep3 = CRDatum(JSONObject(prepjson2), dateUtil)
        Assert.assertFalse(prep1.equals(prep3))
    }
}
