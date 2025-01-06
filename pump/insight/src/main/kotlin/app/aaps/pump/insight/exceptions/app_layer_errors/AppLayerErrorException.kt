package app.aaps.pump.insight.exceptions.app_layer_errors

abstract class AppLayerErrorException(val errorCode: Int) : app.aaps.pump.insight.exceptions.AppLayerException() {

    override val message: String
        get() = "Error code: $errorCode"
}