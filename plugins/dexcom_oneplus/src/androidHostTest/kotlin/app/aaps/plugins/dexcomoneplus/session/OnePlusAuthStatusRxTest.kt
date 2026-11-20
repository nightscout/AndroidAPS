package app.aaps.plugins.dexcomoneplus.session

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class OnePlusAuthStatusRxTest {

    @Test
    fun parse_auth2_bonded1_isRejected() {
        val status = OnePlusAuthStatusRx.parse(byteArrayOf(0x05, 0x02, 0x01))
        assertThat(status).isNotNull()
        assertThat(status!!.authenticated).isEqualTo(2)
        assertThat(status.bonded).isEqualTo(1)
        assertThat(status.isAuthenticated).isFalse()
        assertThat(status.sensorReportsBonded).isTrue()
        assertThat(status.needsKeyRefresh).isFalse()
    }

    @Test
    fun parse_auth1_bonded1_isOk() {
        val status = OnePlusAuthStatusRx.parse(byteArrayOf(0x05, 0x01, 0x01))
        assertThat(status!!.isAuthenticated).isTrue()
        assertThat(status.sensorReportsBonded).isTrue()
    }

    @Test
    fun parse_bond3_needsKeyRefresh() {
        val status = OnePlusAuthStatusRx.parse(byteArrayOf(0x05, 0x01, 0x03))
        assertThat(status!!.needsKeyRefresh).isTrue()
    }

    @Test
    fun parse_ignoresNonAuthStatus() {
        assertThat(OnePlusAuthStatusRx.parse(byteArrayOf(0x04, 0x01, 0x01))).isNull()
        assertThat(OnePlusAuthStatusRx.parse(byteArrayOf(0x05))).isNull()
    }

    @Test
    fun authResult_invalidateFlag_equals() {
        val a = AuthResult(ok = false, message = "key refresh", invalidateSharedKey = true)
        val b = AuthResult(ok = false, message = "key refresh", invalidateSharedKey = true)
        assertThat(a).isEqualTo(b)
        assertThat(a.invalidateSharedKey).isTrue()
    }
}
