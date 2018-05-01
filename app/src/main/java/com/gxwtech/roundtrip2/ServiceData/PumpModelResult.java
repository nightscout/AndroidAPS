package com.gxwtech.roundtrip2.ServiceData;

/**
 * Created by geoff on 7/11/16.
 */
public class PumpModelResult extends ServiceResult {
    private static final String TAG = "PumpModelResult";
    public PumpModelResult() { }

    @Override
    public void init() {
        map.putString("ServiceMessageType","PumpModelResult");
    }

    public void setPumpModel(String model) {
        map.putString("model", model);
    }

    public String getPumpModel() {
        return map.getString("model");
    }

    public void initFromServiceResult(ServiceResult serviceResult) {
        setMap(serviceResult.getMap());
    }

}
