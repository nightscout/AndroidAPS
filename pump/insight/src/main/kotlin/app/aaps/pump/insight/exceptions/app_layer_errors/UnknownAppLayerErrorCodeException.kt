package app.aaps.pump.insight.exceptions.app_layer_errors

class UnknownAppLayerErrorCodeException(errorCode: Int) : AppLayerErrorException(errorCode)