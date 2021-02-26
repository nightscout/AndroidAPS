package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType.AdditionalStatusResponseType

open class AdditionalStatusResponseBase internal constructor(
    val statusResponseType: AdditionalStatusResponseType,
    encoded: ByteArray
) : ResponseBase(ResponseType.ADDITIONAL_STATUS_RESPONSE, encoded)