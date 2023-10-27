package info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority

class CloseConfigurationWriteSessionMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONFIGURATION)