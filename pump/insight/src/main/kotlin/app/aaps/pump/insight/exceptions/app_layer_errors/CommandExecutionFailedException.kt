package app.aaps.pump.insight.exceptions.app_layer_errors

class CommandExecutionFailedException(errorCode: Int) : AppLayerErrorException(errorCode)