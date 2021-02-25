package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response

interface Response {

    val responseType: ResponseType
    val encoded: ByteArray
}