package app.aaps.pump.omnipod.dash.driver.pod.response

abstract class ResponseBase(
    override val responseType: ResponseType,
    encoded: ByteArray
) : Response {

    override val encoded: ByteArray = encoded.copyOf(encoded.size)
}
