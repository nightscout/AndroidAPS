package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;

/**
 * Created by geoff on 7/4/16.
 *
 * Base class for all messages passed between service and client
 */
public class ServiceMessage {
    protected Bundle map = new Bundle();
    public ServiceMessage() { init(); }
    public void init() {
        map.putString("ServiceMessageClass",this.getClass().getCanonicalName());
        map.putString("ServiceMessageType",this.getClass().getSimpleName());
    }
    public Bundle getMap() { return map; }
    public void setMap(Bundle map) { this.map = map; }
    public String getServiceMessageType() {
        return map.getString("ServiceMessageType");
    }


}
