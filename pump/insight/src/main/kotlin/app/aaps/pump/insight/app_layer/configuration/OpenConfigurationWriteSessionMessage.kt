package app.aaps.pump.insight.app_layer.configuration

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority

class OpenConfigurationWriteSessionMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONFIGURATION)