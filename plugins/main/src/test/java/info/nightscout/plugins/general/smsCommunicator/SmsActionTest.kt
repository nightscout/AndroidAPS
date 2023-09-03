package info.nightscout.plugins.general.smsCommunicator

import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(result, "A")
        smsAction = object : SmsAction(false, 1.0) {
            override fun run() {
                result = "B"
            }
        }
        smsAction.run()
        Assertions.assertEquals(result, "B")
        Assertions.assertEquals(smsAction.aDouble(), 1.0, 0.000001)
        smsAction = object : SmsAction(false, 1.0, 2) {
            override fun run() {
                result = "C"
            }
        }
        smsAction.run()
        Assertions.assertEquals(result, "C")
        Assertions.assertEquals(smsAction.aDouble(), 1.0, 0.000001)
        Assertions.assertEquals(smsAction.secondInteger().toLong(), 2)
        smsAction = object : SmsAction(false, "aString", 3) {
            override fun run() {
                result = "D"
            }
        }
        smsAction.run()
        Assertions.assertEquals(result, "D")
        Assertions.assertEquals(smsAction.aString(), "aString")
        Assertions.assertEquals(smsAction.secondInteger().toLong(), 3)
        smsAction = object : SmsAction(false, 4) {
            override fun run() {
                result = "E"
            }
        }
        smsAction.run()
        Assertions.assertEquals(result, "E")
        Assertions.assertEquals(smsAction.anInteger().toLong(), 4)
        smsAction = object : SmsAction(false, 5, 6) {
            override fun run() {
                result = "F"
            }
        }
        smsAction.run()
        Assertions.assertEquals(result, "F")
        Assertions.assertEquals(smsAction.anInteger().toLong(), 5)
        Assertions.assertEquals(smsAction.secondInteger().toLong(), 6)
    }
}