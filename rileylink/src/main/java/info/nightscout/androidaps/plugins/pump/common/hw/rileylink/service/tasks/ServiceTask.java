package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.data.ServiceTransport;

/**
 * Created by geoff on 7/9/16.
 */
public class ServiceTask implements Runnable {

    @Inject protected ActivePluginProvider activePlugin;

    public boolean completed = false;
    protected ServiceTransport mTransport;
    protected HasAndroidInjector injector;


    public ServiceTask(HasAndroidInjector injector) {
        this.injector = injector;
        injector.androidInjector().inject(this);
        init(new ServiceTransport());
    }


    public ServiceTask(HasAndroidInjector injector, ServiceTransport transport) {
        this.injector = injector;
        injector.androidInjector().inject(this);
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

    public boolean isRileyLinkDevice() {
        return (activePlugin.getActivePump() instanceof RileyLinkPumpDevice);
    }


}
