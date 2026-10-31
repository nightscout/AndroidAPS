package app.aaps.plugins.automation.elements

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil

class InputDateTime(private val rh: TextResolver, private val dateUtil: DateUtil, var value: Long = dateUtil.now())
