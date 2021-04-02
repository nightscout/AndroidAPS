package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import kotlin.reflect.KClass

class IllegalResponseException(
    expectedResponseType: KClass<out Response>,
    actualResponse: Response
) : Exception("Illegal response: expected ${expectedResponseType.simpleName} but got $actualResponse")
