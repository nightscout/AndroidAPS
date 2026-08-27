package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.utils.MidnightUtils

class InputTime(private val rh: ResourceHelper, private val dateUtil: DateUtil) {

    var value: Int = getMinSinceMidnight(dateUtil.now())

    private fun getMinSinceMidnight(time: Long): Int = MidnightUtils.secondsFromMidnight(time) / 60
}
