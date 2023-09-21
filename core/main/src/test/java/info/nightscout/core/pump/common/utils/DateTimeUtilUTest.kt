package info.nightscout.core.pump.common.utils

import com.google.common.truth.Truth.assertThat
import info.nightscout.core.utils.DateTimeUtil
import org.junit.jupiter.api.Test

internal class DateTimeUtilUTest {

    @Test fun getaTechDateDifferenceAsMinutes() {
        val dt1 = 20191001182301L
        val dt2 = 20191001192805L
        val aTechDateDifferenceAsMinutes = DateTimeUtil.getATechDateDifferenceAsMinutes(dt1, dt2)
        assertThat(aTechDateDifferenceAsMinutes).isEqualTo(65)
    }
}
