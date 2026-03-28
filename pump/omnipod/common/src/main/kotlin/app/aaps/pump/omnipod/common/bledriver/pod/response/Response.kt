package app.aaps.pump.omnipod.common.bledriver.pod.response

import java.io.Serializable

interface Response : Serializable {

    val responseType: ResponseType
    val encoded: ByteArray
}
