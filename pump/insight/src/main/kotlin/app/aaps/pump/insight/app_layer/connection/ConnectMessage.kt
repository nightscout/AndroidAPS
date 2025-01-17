package app.aaps.pump.insight.app_layer.connection

import app.aaps.pump.insight.app_layer.AppLayerMessage
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.utils.ByteBuf
import org.spongycastle.util.encoders.Hex

class ConnectMessage : AppLayerMessage(MessagePriority.NORMAL, false, false, Service.CONNECTION) {

    override val data: ByteBuf
        get() = ByteBuf.from(Hex.decode("0000080100196000"))
}