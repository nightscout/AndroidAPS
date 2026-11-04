package app.aaps.plugins.automation.elements

import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime

class InputTimeRange(private val rh: TextResolver, private val dateUtil: DateUtil) {

    var start: Int = getMinSinceMidnight(dateUtil.now())
    var end: Int = getMinSinceMidnight(dateUtil.now())

    private fun getMinSinceMidnight(time: Long): Int = MidnightTime.secondsFromMidnight(time, SystemTimeZone.current()) / 60
}
