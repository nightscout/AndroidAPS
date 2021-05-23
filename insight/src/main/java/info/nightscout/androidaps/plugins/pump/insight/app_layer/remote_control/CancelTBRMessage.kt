package info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority

class CancelTBRMessage : AppLayerMessage(MessagePriority.HIGHER, false, false, Service.REMOTE_CONTROL)