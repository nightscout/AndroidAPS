package app.aaps.pump.omnipod.dash.driver.pod.response

import java.io.Serializable

interface Response : Serializable {

    val responseType: ResponseType
    val encoded: ByteArray
}
