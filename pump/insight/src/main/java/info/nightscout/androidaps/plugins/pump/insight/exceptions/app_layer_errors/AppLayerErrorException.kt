package info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors

import info.nightscout.androidaps.plugins.pump.insight.exceptions.AppLayerException

abstract class AppLayerErrorException(val errorCode: Int) : AppLayerException() {

    override val message: String
        get() = "Error code: $errorCode"
}