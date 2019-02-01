package info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.configuration;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.descriptors.MessagePriority;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;

public class OpenConfigurationWriteSessionMessage extends AppLayerMessage {

    public OpenConfigurationWriteSessionMessage() {
        super(MessagePriority.NORMAL, false, false, Service.CONFIGURATION);
    }
}
