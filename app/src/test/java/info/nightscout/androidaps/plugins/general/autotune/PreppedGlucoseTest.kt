package info.nightscout.androidaps.plugins.general.autotune

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.general.autotune.data.*
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock

class PreppedGlucoseTest : TestBase() {
    @Mock lateinit var dateUtil: DateUtil
    lateinit var prep1: PreppedGlucose
    lateinit var prepjson1: String

    @Before
    fun initData() {
        prepjson1 = "{\"CRData\":[{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}],\"CSFGlucoseData\":[{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}],\"ISFGlucoseData\":[{\"_id\":\"1598521313061\",\"device\":\"xDrip-LimiTTer\",\"date\":1598521313061,\"dateString\":\"2020-08-27T09:41:53.061Z\",\"sgv\":71,\"delta\":-15.745,\"direction\":\"SingleDown\",\"type\":\"sgv\",\"filtered\":60705.877799999995,\"unfiltered\":60705.877799999995,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T09:41:53.061Z\",\"utcOffset\":120,\"glucose\":71,\"avgDelta\":-14.75,\"BGI\":-7.2,\"deviation\":-7.55}],\"basalGlucoseData\":[{\"_id\":\"1598496872193\",\"device\":\"xDrip-LimiTTer\",\"date\":1598496872193,\"dateString\":\"2020-08-27T02:54:32.193Z\",\"sgv\":127,\"delta\":-7.259,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"filtered\":106823.5214,\"unfiltered\":106823.5214,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T02:54:32.193Z\",\"utcOffset\":120,\"glucose\":127,\"avgDelta\":-5.75,\"BGI\":-0.51,\"deviation\":-5.24}]}"
        prep1 = PreppedGlucose(JSONObject(prepjson1), dateUtil)
    }

    @Test
    fun preppedGlucoseEqualTest() {
        val prep2 = PreppedGlucose(JSONObject(prepjson1), dateUtil)
        aapsLogger.debug(prep1.toString(4))
        Assert.assertTrue(prep1.equals(prep2))
    }

    @Test
    fun preppedGlucoseCRDataNotEqualTest() {
         //CRData modification
        val prepjson2 = "{\"CRData\":[{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.360,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}],\"CSFGlucoseData\":[{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}],\"ISFGlucoseData\":[{\"_id\":\"1598521313061\",\"device\":\"xDrip-LimiTTer\",\"date\":1598521313061,\"dateString\":\"2020-08-27T09:41:53.061Z\",\"sgv\":71,\"delta\":-15.745,\"direction\":\"SingleDown\",\"type\":\"sgv\",\"filtered\":60705.877799999995,\"unfiltered\":60705.877799999995,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T09:41:53.061Z\",\"utcOffset\":120,\"glucose\":71,\"avgDelta\":-14.75,\"BGI\":-7.2,\"deviation\":-7.55}],\"basalGlucoseData\":[{\"_id\":\"1598496872193\",\"device\":\"xDrip-LimiTTer\",\"date\":1598496872193,\"dateString\":\"2020-08-27T02:54:32.193Z\",\"sgv\":127,\"delta\":-7.259,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"filtered\":106823.5214,\"unfiltered\":106823.5214,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T02:54:32.193Z\",\"utcOffset\":120,\"glucose\":127,\"avgDelta\":-5.75,\"BGI\":-0.51,\"deviation\":-5.24}]}"
        val prep3 = PreppedGlucose(JSONObject(prepjson2), dateUtil)
        Assert.assertFalse(prep1.equals(prep3))
    }

    @Test
    fun preppedGlucoseCSFDataNotEqualTest() {
        //CSF Modification
        val prepjson3 = "{\"CRData\":[{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}],\"CSFGlucoseData\":[{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.01,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}],\"ISFGlucoseData\":[{\"_id\":\"1598521313061\",\"device\":\"xDrip-LimiTTer\",\"date\":1598521313061,\"dateString\":\"2020-08-27T09:41:53.061Z\",\"sgv\":71,\"delta\":-15.745,\"direction\":\"SingleDown\",\"type\":\"sgv\",\"filtered\":60705.877799999995,\"unfiltered\":60705.877799999995,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T09:41:53.061Z\",\"utcOffset\":120,\"glucose\":71,\"avgDelta\":-14.75,\"BGI\":-7.2,\"deviation\":-7.55}],\"basalGlucoseData\":[{\"_id\":\"1598496872193\",\"device\":\"xDrip-LimiTTer\",\"date\":1598496872193,\"dateString\":\"2020-08-27T02:54:32.193Z\",\"sgv\":127,\"delta\":-7.259,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"filtered\":106823.5214,\"unfiltered\":106823.5214,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T02:54:32.193Z\",\"utcOffset\":120,\"glucose\":127,\"avgDelta\":-5.75,\"BGI\":-0.51,\"deviation\":-5.24}]}"
        val prep4 = PreppedGlucose(JSONObject(prepjson3), dateUtil)
        Assert.assertFalse(prep1.equals(prep4))
    }

    @Test
    fun preppedGlucoseISFDataNotEqualTest() {
        //ISF Modification
        val prepjson4 = "{\"CRData\":[{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}],\"CSFGlucoseData\":[{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}],\"ISFGlucoseData\":[{\"_id\":\"1598521313061\",\"device\":\"xDrip-LimiTTer\",\"date\":1598521313061,\"dateString\":\"2020-08-27T09:41:53.061Z\",\"sgv\":71,\"delta\":-15.745,\"direction\":\"SingleDown\",\"type\":\"sgv\",\"filtered\":60705.877799999995,\"unfiltered\":60705.877799999995,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T09:41:53.061Z\",\"utcOffset\":120,\"glucose\":71,\"avgDelta\":-14.75,\"BGI\":-7.1,\"deviation\":-7.55}],\"basalGlucoseData\":[{\"_id\":\"1598496872193\",\"device\":\"xDrip-LimiTTer\",\"date\":1598496872193,\"dateString\":\"2020-08-27T02:54:32.193Z\",\"sgv\":127,\"delta\":-7.259,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"filtered\":106823.5214,\"unfiltered\":106823.5214,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T02:54:32.193Z\",\"utcOffset\":120,\"glucose\":127,\"avgDelta\":-5.75,\"BGI\":-0.51,\"deviation\":-5.24}]}"
        val prep5 = PreppedGlucose(JSONObject(prepjson4), dateUtil)
        Assert.assertFalse(prep1.equals(prep5))
    }

    @Test
    fun preppedGlucoseBasalDataNotEqualTest() {
         //basal Modification
        val prepjson5 = "{\"CRData\":[{\"CRInitialIOB\":12.319,\"CRInitialBG\":83,\"CRInitialCarbTime\":\"2020-08-27T05:51:43.572Z\",\"CREndIOB\":0.361,\"CREndBG\":41,\"CREndTime\":\"2020-08-27T10:21:53.116Z\",\"CRCarbs\":65,\"CRInsulin\":0.94}],\"CSFGlucoseData\":[{\"_id\":\"1598507503572\",\"device\":\"xDrip-LimiTTer\",\"date\":1598507503572,\"dateString\":\"2020-08-27T05:51:43.572Z\",\"sgv\":83,\"delta\":-0.168,\"direction\":\"Flat\",\"type\":\"sgv\",\"filtered\":76588.22954999999,\"unfiltered\":76588.22954999999,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T05:51:43.572Z\",\"utcOffset\":120,\"glucose\":83,\"avgDelta\":8.00,\"BGI\":0.82,\"deviation\":7.18,\"mealAbsorption\":\"start\",\"mealCarbs\":65}],\"ISFGlucoseData\":[{\"_id\":\"1598521313061\",\"device\":\"xDrip-LimiTTer\",\"date\":1598521313061,\"dateString\":\"2020-08-27T09:41:53.061Z\",\"sgv\":71,\"delta\":-15.745,\"direction\":\"SingleDown\",\"type\":\"sgv\",\"filtered\":60705.877799999995,\"unfiltered\":60705.877799999995,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T09:41:53.061Z\",\"utcOffset\":120,\"glucose\":71,\"avgDelta\":-14.75,\"BGI\":-7.2,\"deviation\":-7.55}],\"basalGlucoseData\":[{\"_id\":\"1598496872193\",\"device\":\"xDrip-LimiTTer\",\"date\":1598496872193,\"dateString\":\"2020-08-27T02:54:32.193Z\",\"sgv\":127,\"delta\":-7.259,\"direction\":\"FortyFiveDown\",\"type\":\"sgv\",\"filtered\":106823.5214,\"unfiltered\":106823.5214,\"rssi\":100,\"noise\":1,\"sysTime\":\"2020-08-27T02:54:32.193Z\",\"utcOffset\":120,\"glucose\":127,\"avgDelta\":-5.75,\"BGI\":-0.51,\"deviation\":-5.23}]}"
        val prep6 = PreppedGlucose(JSONObject(prepjson5), dateUtil)
        Assert.assertFalse(prep1.equals(prep6))
    }

}
