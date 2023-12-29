package app.aaps.pump.insight.app_layer.remote_control

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority

class CancelTBRMessage : AppLayerMessage(MessagePriority.HIGHER, false, false, Service.REMOTE_CONTROL)