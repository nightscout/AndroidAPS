package app.aaps.pump.omnipod.dash.driver.pod.response

import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType.StatusResponseType

open class AdditionalStatusResponseBase internal constructor(
    val statusResponseType: StatusResponseType,
    encoded: ByteArray
) : ResponseBase(ResponseType.ADDITIONAL_STATUS_RESPONSE, encoded)
