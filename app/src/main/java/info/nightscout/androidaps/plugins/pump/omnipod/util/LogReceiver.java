package info.nightscout.androidaps.plugins.pump.omnipod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInitReceiver;

public class LogReceiver implements PodInitReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(LogReceiver.class);

    @Override
    public void returnInitTaskStatus(PodInitActionType podInitActionType, boolean isSuccess, String errorMessage) {

        if (errorMessage != null) {
            LOG.error(podInitActionType.name() + " - Success: " + isSuccess + ", Error Message: " + errorMessage);
        } else {
            if (isSuccess) {
                LOG.info(podInitActionType.name() + " - Success: " + isSuccess);
            } else {
                LOG.error(podInitActionType.name() + " - NOT Succesful");
            }
        }
    }
}
