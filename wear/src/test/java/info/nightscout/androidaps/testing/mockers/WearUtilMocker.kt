package info.nightscout.androidaps.testing.mockers

import info.nightscout.androidaps.interaction.utils.Constants
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.annotations.OpenForTesting
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

@OpenForTesting
class WearUtilMocker(private val wearUtil: WearUtil) {

    private var clockMsDiff = 0L
    fun prepareMock() {
        resetClock()

        // because we cleverly used timestamp() by implementation, we can mock it
        // and control the time in tests
        Mockito.`when`(wearUtil.timestamp()).thenReturn(REF_NOW + clockMsDiff)
    }

    fun prepareMockNoReal() {
        resetClock()
        Mockito.doAnswer { REF_NOW + clockMsDiff }.`when`(wearUtil).timestamp()
        Mockito.doReturn(null).`when`(wearUtil).getWakeLock(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())
    }

    private fun resetClock() {
        clockMsDiff = 0L
    }

    fun progressClock(byMilliseconds: Long) {
        clockMsDiff += byMilliseconds
    }

    fun backInTime(d: Int, h: Int, m: Int, s: Int): Long {
        return REF_NOW - (Constants.DAY_IN_MS * d + Constants.HOUR_IN_MS * h + Constants.MINUTE_IN_MS * m + Constants.SECOND_IN_MS * s)
    }

    companion object {

        const val REF_NOW = 1572610530000L
    }
}