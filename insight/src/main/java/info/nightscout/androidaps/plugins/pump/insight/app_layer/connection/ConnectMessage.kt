package info.nightscout.androidaps.plugins.pump.insight.app_layer.connection

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.descriptors.MessagePriority
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf
import org.spongycastle.util.encoders.Hex

class ConnectMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONNECTION) {

    override val data: ByteBuf
        get() = ByteBuf.from(Hex.decode("0000080100196000"))
}