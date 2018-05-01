package com.gxwtech.roundtrip2.ServiceData;

/**
 * Created by geoff on 7/4/16.
 */
@Deprecated
public class ServiceMessageUpdate extends ServiceMessage {
    public ServiceMessageUpdate() {}
    public void init() {
        map.putString("ServiceMessageType","ServiceUpdateMessage");
    }
}
