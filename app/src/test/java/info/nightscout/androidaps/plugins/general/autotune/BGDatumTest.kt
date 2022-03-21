package info.nightscout.androidaps.plugins.general.autotune

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.mockito.Mock

class BGDatumTest : TestBase() {
    @Mock lateinit var dateUtil: DateUtil

    @Test
    fun bgDatumEqualTest() {
        val prepjson1 = "{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\"," +
            "\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}"
        //date rouded to lower second
        val prepjson3 = "{\"_id\":\"1598507503000\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503000,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}"
        var prep1 = BGDatum(JSONObject(prepjson1), dateUtil)
        val prep2 = BGDatum(JSONObject(prepjson1), dateUtil)
        val prep3 = BGDatum(JSONObject(prepjson3), dateUtil)
        aapsLogger.debug("Prep1 " + prep1.toJSON(true).toString(4))
        Assert.assertTrue(prep1.equals(prep2))
        Assert.assertTrue(prep1.equals(prep3))
    }


    @Test
    fun bgDatumNotEqualTest() {
        val prepjson1 = "{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}"
        val prepjson2 = "{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.01,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}"
        //date next second
        val prepjson4 = "{\"_id\":\"1598507504000\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507504000,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}"
        var prep1 = BGDatum(JSONObject(prepjson1), dateUtil)
        val prep4 = BGDatum(JSONObject(prepjson2), dateUtil)
        val prep5 = BGDatum(JSONObject(prepjson4), dateUtil)
        Assert.assertFalse(prep1.equals(prep4))
        Assert.assertFalse(prep1.equals(prep5))
    }
}
