package info.nightscout.androidaps.plugins.PumpInsightLocal.utils;

import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.ReadParameterBlockMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.Service;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.configuration.WriteConfigurationBlockMessage;
import info.nightscout.androidaps.plugins.PumpInsightLocal.app_layer.parameter_blocks.ParameterBlock;
import info.nightscout.androidaps.plugins.PumpInsightLocal.connection_service.InsightConnectionService;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.ConfigurationBlock;

public class ParameterBlockUtil {

    public static <T extends ParameterBlock> T readParameterBlock(InsightConnectionService connectionService, Service service, Class<T> parameterBlock) throws Exception {
        ReadParameterBlockMessage readMessage = new ReadParameterBlockMessage();
        readMessage.setService(service);
        readMessage.setParameterBlockId(parameterBlock);
        return (T) connectionService.requestMessage(readMessage).await().getParameterBlock();
    }

    public static void writeConfigurationBlock(InsightConnectionService connectionService, ParameterBlock parameterBlock) throws Exception {
        WriteConfigurationBlockMessage writeMessage = new WriteConfigurationBlockMessage();
        writeMessage.setParameterBlock(parameterBlock);
        connectionService.requestMessage(writeMessage).await();
    }

}
