package info.nightscout.pump.medtrum

import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.Assert.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MedtrumPumpTest : MedtrumTestBase() {

    @Test fun buildMedtrumProfileArrayGivenProfileWhenValuesSetThenReturnCorrectByteArray() {
        // Inputs
        // Basal profile with 7 elements:
        // 00:00 : 2.1
        // 04:00 : 1.9
        // 06:00 : 1.7
        // 08:00 : 1.5
        // 16:00 : 1.6
        // 21:00 : 1.7
        // 23:00 : 2
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"2.1\"},{\"time\":\"04:00\",\"value\":\"1.9\"},{\"time\":\"06:00\",\"value\":\"1.7\"}," +
            "{\"time\":\"08:00\",\"value\":\"1.5\"},{\"time\":\"16:00\",\"value\":\"1.6\"},{\"time\":\"21:00\",\"value\":\"1.7\"},{\"time\":\"23:00\",\"value\":\"2\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        val expectedByteArray = byteArrayOf(7, 0, -96, 2, -16, 96, 2, 104, 33, 2, -32, -31, 1, -64, 3, 2, -20, 36, 2, 100, -123, 2)
        assertEquals(expectedByteArray.contentToString(), result?.contentToString())
    }

    @Test fun buildMedtrumProfileArrayGiveProfileWhenValuesTooHighThenReturnNull() {
        // Inputs
        // Basal profile with 1 element:
        // 00:00 : 600
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"600\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)

        // Call
        val result = medtrumPump.buildMedtrumProfileArray(profile)

        // Expected values
        assertNull(result)
    }

    @Test fun getCurrentHourlyBasalFromMedtrumProfileArrayGivenProfileWhenValuesSetThenReturnCorrectValue() {
        // Inputs
        // Basal profile with 7 elements:
        // 00:00 : 2.1
        // 04:00 : 1.9
        // 06:00 : 1.7
        // 08:00 : 1.5
        // 16:00 : 1.6
        // 21:00 : 1.7
        // 23:00 : 2
        val profileJSON = "{\"dia\":\"5\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\"," +
            "\"sens\":[{\"time\":\"00:00\",\"value\":\"3\"},{\"time\":\"02:00\",\"value\":\"3.4\"}],\"timezone\":\"UTC\"," +
            "\"basal\":[{\"time\":\"00:00\",\"value\":\"2.1\"},{\"time\":\"04:00\",\"value\":\"1.9\"},{\"time\":\"06:00\",\"value\":\"1.7\"}," +
            "{\"time\":\"08:00\",\"value\":\"1.5\"},{\"time\":\"16:00\",\"value\":\"1.6\"},{\"time\":\"21:00\",\"value\":\"1.7\"},{\"time\":\"23:00\",\"value\":\"2\"}]," +
            "\"target_low\":[{\"time\":\"00:00\",\"value\":\"4.5\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"7\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"
        val profile = ProfileSealed.Pure(pureProfileFromJson(JSONObject(profileJSON), dateUtil)!!)
        val profileArray = medtrumPump.buildMedtrumProfileArray(profile)

        val localDate = LocalDate.of(2023, 1, 1)

        // For 03:59
        val localTime0399 = LocalTime.of(3, 59)
        val zonedDateTime0399 = localDate.atTime(localTime0399).atZone(ZoneId.systemDefault())
        val time0399 = zonedDateTime0399.toInstant().toEpochMilli()
        val result = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time0399)
        assertEquals(2.1, result, 0.01)

        // For 22:30
        val localTime2230 = LocalTime.of(22, 30)
        val zonedDateTime2230 = localDate.atTime(localTime2230).atZone(ZoneId.systemDefault())
        val time2230 = zonedDateTime2230.toInstant().toEpochMilli()
        val result1 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time2230)
        assertEquals(1.7, result1, 0.01)

        // For 23:59
        val localTime2359 = LocalTime.of(23, 59)
        val zonedDateTime2359 = localDate.atTime(localTime2359).atZone(ZoneId.systemDefault())
        val time2359 = zonedDateTime2359.toInstant().toEpochMilli()
        val result2 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time2359)
        assertEquals(2.0, result2, 0.01)

        // For 00:00
        val localTime0000 = LocalTime.of(0, 0)
        val zonedDateTime0000 = localDate.atTime(localTime0000).atZone(ZoneId.systemDefault())
        val time0000 = zonedDateTime0000.toInstant().toEpochMilli()
        val result3 = medtrumPump.getHourlyBasalFromMedtrumProfileArray(profileArray!!, time0000)
        assertEquals(2.1, result3, 0.01)
    }
}
