package info.nightscout.core.pump.common.utils

import info.nightscout.core.utils.DateTimeUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class DateTimeUtilUTest {

    @Test fun getaTechDateDifferenceAsMinutes() {
        val dt1 = 20191001182301L
        val dt2 = 20191001192805L
        val aTechDateDifferenceAsMinutes = DateTimeUtil.getATechDateDifferenceAsMinutes(dt1, dt2)
        Assertions.assertEquals(65, aTechDateDifferenceAsMinutes)
    }
}