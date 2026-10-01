package app.aaps.core.nssdk.mapper

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MbgMapperTest {

    @Test
    fun acceptsMarkedCalibrationMbg() {
        val json = """{"type":"mbg","mbg":120.0,"sensorMgdlAtPairing":110.0,"isCalibration":true,"date":1700000000000,"identifier":"abc","units":"mg/dl","isValid":true}"""
        val cal = json.toCalibrationMbg()
        assertThat(cal).isNotNull()
        assertThat(cal!!.mbg).isEqualTo(120.0)
        assertThat(cal.sensorMgdlAtPairing).isEqualTo(110.0)
        assertThat(cal.identifier).isEqualTo("abc")
    }

    @Test
    fun rejectsForeignMbgWithoutMarker() {
        // A manual BG logged by another uploader (no isCalibration / sensorMgdlAtPairing) must be ignored.
        val json = """{"type":"mbg","mbg":120.0,"date":1700000000000,"units":"mg/dl"}"""
        assertThat(json.toCalibrationMbg()).isNull()
    }

    @Test
    fun rejectsMarkedMbgMissingSensorPair() {
        val json = """{"type":"mbg","mbg":120.0,"isCalibration":true,"date":1700000000000}"""
        assertThat(json.toCalibrationMbg()).isNull()
    }

    @Test
    fun rejectsSgv() {
        val json = """{"type":"sgv","sgv":120.0,"date":1700000000000}"""
        assertThat(json.toCalibrationMbg()).isNull()
    }

    @Test
    fun rejectsMarkedMbgMissingDate() {
        val json = """{"type":"mbg","mbg":120.0,"sensorMgdlAtPairing":110.0,"isCalibration":true}"""
        assertThat(json.toCalibrationMbg()).isNull()
    }
}
