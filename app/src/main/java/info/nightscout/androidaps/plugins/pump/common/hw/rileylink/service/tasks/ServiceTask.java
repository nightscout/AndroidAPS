package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;

/**
 * Created by geoff on 7/9/16.
 */
public class ServiceTask implements Runnable {

    private static final String TAG = "ServiceTask(base)";
    public boolean completed = false;
    protected ServiceTransport mTransport;


    public ServiceTask() {
        init(new ServiceTransport());
    }


    public ServiceTask(ServiceTransport transport) {
        init(transport);
    }


    public void init(ServiceTransport transport) {
        mTransport = transport;
    }


    @Override
    public void run() {
    }


    public void preOp() {
        // This function is called by UI thread before running asynch thread.
    }


    public void postOp() {
        // This function is called by UI thread after running asynch thread.
    }


    public ServiceTransport getServiceTransport() {
        return mTransport;
    }

    /*
     * protected void sendResponse(ServiceResult result) {
     * RoundtripService.getInstance().sendServiceTransportResponse(mTransport,result);
     * }
     */
}
