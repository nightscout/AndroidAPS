package info.nightscout.androidaps.plugins.general.smsCommunicator

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class SmsActionTest {

    var result = ""
    @Test fun doTests() {
        var smsAction: SmsAction = object : SmsAction() {
            override fun run() {
                result = "A"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "A")
        smsAction = object : SmsAction(1.0) {
            override fun run() {
                result = "B"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "B")
        Assert.assertEquals(smsAction.aDouble(), 1.0, 0.000001)
        smsAction = object : SmsAction(1.0, 2) {
            override fun run() {
                result = "C"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "C")
        Assert.assertEquals(smsAction.aDouble(), 1.0, 0.000001)
        Assert.assertEquals(smsAction.secondInteger().toLong(), 2)
        smsAction = object : SmsAction("aString", 3) {
            override fun run() {
                result = "D"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "D")
        Assert.assertEquals(smsAction.aString(), "aString")
        Assert.assertEquals(smsAction.secondInteger().toLong(), 3)
        smsAction = object : SmsAction(4) {
            override fun run() {
                result = "E"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "E")
        Assert.assertEquals(smsAction.anInteger().toLong(), 4)
        smsAction = object : SmsAction(5, 6) {
            override fun run() {
                result = "F"
            }
        }
        smsAction.run()
        Assert.assertEquals(result, "F")
        Assert.assertEquals(smsAction.anInteger().toLong(), 5)
        Assert.assertEquals(smsAction.secondInteger().toLong(), 6)
    }
}