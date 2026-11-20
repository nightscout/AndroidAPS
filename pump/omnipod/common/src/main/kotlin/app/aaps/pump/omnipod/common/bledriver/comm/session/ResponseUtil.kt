package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotParseResponseException
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.DefaultStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.NakResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoActivationTimeResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoPulseLogPlusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoPulseLogPreviousResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoPulseLogRecentResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.PodInfoTriggeredAlertsResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.response.ResponseType
import app.aaps.pump.omnipod.common.bledriver.pod.response.SetUniqueIdResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.VersionResponse
import app.aaps.pump.omnipod.common.bledriver.pod.util.byValue

object ResponseUtil {

    @Throws(CouldNotParseResponseException::class, UnsupportedOperationException::class)
    fun parseResponse(payload: ByteArray): Response {
        return when (byValue(payload[0], ResponseType.UNKNOWN)) {
            ResponseType.ACTIVATION_RESPONSE        -> parseActivationResponse(payload)
            ResponseType.DEFAULT_STATUS_RESPONSE    -> DefaultStatusResponse(payload)
            ResponseType.ADDITIONAL_STATUS_RESPONSE -> parseAdditionalStatusResponse(payload)
            ResponseType.NAK_RESPONSE               -> NakResponse(payload)
            ResponseType.UNKNOWN                    -> throw CouldNotParseResponseException("Unrecognized message type: ${payload[0]}")
        }
    }

    @Throws(CouldNotParseResponseException::class)
    private fun parseActivationResponse(payload: ByteArray): Response {
        return when (byValue(payload[1], ResponseType.ActivationResponseType.UNKNOWN)) {
            ResponseType.ActivationResponseType.GET_VERSION_RESPONSE   -> VersionResponse(payload)
            ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE -> SetUniqueIdResponse(payload)
            ResponseType.ActivationResponseType.UNKNOWN                -> throw CouldNotParseResponseException("Unrecognized activation response type: ${payload[1]}")
        }
    }

    @Throws(CouldNotParseResponseException::class, UnsupportedOperationException::class)
    private fun parseAdditionalStatusResponse(payload: ByteArray): Response {
        return when (byValue(payload[2], ResponseType.StatusResponseType.UNKNOWN)) {
            ResponseType.StatusResponseType.DEFAULT_STATUS_RESPONSE -> DefaultStatusResponse(payload) // Unreachable; this response type is only used for requesting a default status response
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_1  -> PodInfoTriggeredAlertsResponse(payload)
            ResponseType.StatusResponseType.ALARM_STATUS            -> AlarmStatusResponse(payload)
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_3  -> PodInfoPulseLogPlusResponse(payload)
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_5  -> PodInfoActivationTimeResponse(payload)
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_80 -> PodInfoPulseLogRecentResponse(payload)
            ResponseType.StatusResponseType.STATUS_RESPONSE_PAGE_81 -> PodInfoPulseLogPreviousResponse(payload)
            ResponseType.StatusResponseType.UNKNOWN                 -> throw CouldNotParseResponseException("Unrecognized additional status response type: ${payload[2]}")
        }
    }
}
