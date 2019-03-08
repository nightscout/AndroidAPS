package info.nightscout.androidaps.plugins.pump.insight.connection_service;

import info.nightscout.androidaps.plugins.pump.insight.app_layer.AppLayerMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.CloseConfigurationWriteSessionMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.configuration.OpenConfigurationWriteSessionMessage;

public class ConfigurationMessageRequest<T extends AppLayerMessage> extends MessageRequest<T> {

    private MessageRequest<OpenConfigurationWriteSessionMessage> openRequest;
    private MessageRequest<CloseConfigurationWriteSessionMessage> closeRequest;

    public ConfigurationMessageRequest(T request, MessageRequest<OpenConfigurationWriteSessionMessage> openRequest, MessageRequest<CloseConfigurationWriteSessionMessage> closeRequest) {
        super(request);
        this.openRequest = openRequest;
        this.closeRequest = closeRequest;
    }

    @Override
    public T await() throws Exception {
        openRequest.await();
        T response = super.await();
        closeRequest.await();
        return response;
    }

    @Override
    public T await(long timeout) throws Exception {
        openRequest.await(timeout);
        T response = super.await(timeout);
        closeRequest.await(timeout);
        return response;
    }
}
