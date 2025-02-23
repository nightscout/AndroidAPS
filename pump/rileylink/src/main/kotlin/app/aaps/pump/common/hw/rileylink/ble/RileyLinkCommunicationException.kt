package app.aaps.pump.common.hw.rileylink.ble

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkBLEError
import java.lang.Exception

/**
 * Created by andy on 11/23/18.
 */
class RileyLinkCommunicationException(val errorCode: RileyLinkBLEError, @Suppress("unused") val extendedErrorText: String? = null) : Exception(errorCode.description)