package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.IllegalResponseException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.byValue

object ResponseUtil {

    @Throws(IllegalResponseException::class, UnsupportedOperationException::class)
    fun parseResponse(payload: ByteArray): Response {
        return when (val responseType = byValue(payload[0], ResponseType.UNKNOWN)) {
            ResponseType.ACTIVATION_RESPONSE -> parseActivationResponse(payload)
            ResponseType.DEFAULT_STATUS_RESPONSE -> DefaultStatusResponse(payload)
            ResponseType.ADDITIONAL_STATUS_RESPONSE -> parseAdditionalStatusResponse(payload)
            ResponseType.NAK_RESPONSE -> NakResponse(payload)
            ResponseType.UNKNOWN -> throw IllegalResponseException("Unrecognized message type: $responseType")
        }
    }

    @Throws(IllegalResponseException::class)
    private fun parseActivationResponse(payload: ByteArray): Response {
        return when (val activationResponseType = byValue(payload[1], ResponseType.ActivationResponseType.UNKNOWN)) {
            ResponseType.ActivationResponseType.GET_VERSION_RESPONSE -> VersionResponse(payload)
            ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE -> SetUniqueIdResponse(payload)
            ResponseType.ActivationResponseType.UNKNOWN -> throw IllegalResponseException("Unrecognized activation response type: $activationResponseType")
        }
    }

    @Throws(IllegalResponseException::class, UnsupportedOperationException::class)
    private fun parseAdditionalStatusResponse(payload: ByteArray): Response {
        return when (val additionalStatusResponseType = byValue(payload[2], ResponseType.StatusResponseType.UNKNOWN)) {
            ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE -> DefaultStatusResponse(payload) // Unreachable; this response type is only used for requesting a default status response
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_1 -> throw UnsupportedOperationException("Status response page 1 is not (yet) implemented")
            ResponseType.StatusResponseType.ALARM_STATUS -> AlarmStatusResponse(payload)
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_3 -> throw UnsupportedOperationException("Status response page 3 is not (yet) implemented")
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_5 -> throw UnsupportedOperationException("Status response page 5 is not (yet) implemented")
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_6 -> throw UnsupportedOperationException("Status response page 6 is not (yet) implemented")
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_70 -> throw UnsupportedOperationException("Status response page 70 is not (yet) implemented")
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_80 -> throw UnsupportedOperationException("Status response page 80 is not (yet) implemented")
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_81 -> throw UnsupportedOperationException("Status response page 81 is not (yet) implemented")
            ResponseType.StatusResponseType.UNKNOWN -> throw IllegalResponseException("Unrecognized additional status response type: $additionalStatusResponseType")
        }
    }
}