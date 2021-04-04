package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.NakResponse

class NakResponseException(val response: NakResponse) :
    Exception("Received NAK response: ${response.nakErrorType.value} ${response.nakErrorType.name}")
