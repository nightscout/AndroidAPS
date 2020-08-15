package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data;

import android.os.Bundle;

/**
 * Created by geoff on 7/6/16.
 * <p>
 * These are "one liner" messages between client and service. Must still be contained within ServiceTransports
 */
public class ServiceNotification extends ServiceMessage {

    public ServiceNotification() {
    }


    public ServiceNotification(Bundle b) {
        if (b != null) {
            if ("ServiceNotification".equals(b.getString("ServiceMessageType"))) {
                setMap(b);
            } else {
                throw new IllegalArgumentException();
            }
        }
    }


    public ServiceNotification(String notificationType) {
        setNotificationType(notificationType);
    }


    @Override
    public void init() {
        super.init();
        map.putString("ServiceMessageType", "ServiceNotification");
    }


    public String getNotificationType() {
        return map.getString("NotificationType", "");
    }


    public void setNotificationType(String notificationType) {
        map.putString("NotificationType", notificationType);
    }

}
