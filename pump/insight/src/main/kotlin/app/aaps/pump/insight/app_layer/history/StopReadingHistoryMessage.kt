package app.aaps.pump.insight.app_layer.history

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority

class StopReadingHistoryMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.HISTORY)