package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.ResponseType.ActivationResponseType

abstract class ActivationResponseBase(
    val activationResponseType: ActivationResponseType,
    encoded: ByteArray
) : ResponseBase(ResponseType.ACTIVATION_RESPONSE, encoded)
