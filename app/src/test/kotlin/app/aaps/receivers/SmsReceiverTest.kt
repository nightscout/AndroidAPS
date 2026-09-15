package app.aaps.receivers

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmsReceiverTest {

    val sut = SmsReceiver()
    @Test
    fun testType() {
        assertTrue(DataReceiver::class.java.isAssignableFrom(sut::class.java))
    }

}