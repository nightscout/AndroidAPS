package app.aaps.plugins.main.general.smsCommunicator

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SmsActionTest {

    var result = ""
    @Test fun doTests() {
        var smsAction: SmsAction = object : SmsAction(false) {
            override fun run() {
                result = "A"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("A")
        smsAction = object : SmsAction(false, 1.0) {
            override fun run() {
                result = "B"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("B")
        assertThat(smsAction.aDouble()).isWithin(0.000001).of(1.0)
        smsAction = object : SmsAction(false, 1.0, 2) {
            override fun run() {
                result = "C"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("C")
        assertThat(smsAction.aDouble()).isWithin(0.000001).of(1.0)
        assertThat(smsAction.secondInteger().toLong()).isEqualTo(2)
        smsAction = object : SmsAction(false, "aString", 3) {
            override fun run() {
                result = "D"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("D")
        assertThat(smsAction.aString()).isEqualTo("aString")
        assertThat(smsAction.secondInteger().toLong()).isEqualTo(3)
        smsAction = object : SmsAction(false, 4) {
            override fun run() {
                result = "E"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("E")
        assertThat(smsAction.anInteger().toLong()).isEqualTo(4)
        smsAction = object : SmsAction(false, 5, 6) {
            override fun run() {
                result = "F"
            }
        }
        smsAction.run()
        assertThat(result).isEqualTo("F")
        assertThat(smsAction.anInteger().toLong()).isEqualTo(5)
        assertThat(smsAction.secondInteger().toLong()).isEqualTo(6)
    }
}
