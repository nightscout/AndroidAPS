package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil

class InputDateTime(private val rh: ResourceHelper, private val dateUtil: DateUtil, var value: Long = dateUtil.now())
