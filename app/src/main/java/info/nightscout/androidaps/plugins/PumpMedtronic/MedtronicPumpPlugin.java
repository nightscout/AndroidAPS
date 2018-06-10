package info.nightscout.androidaps.plugins.PumpMedtronic;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpDriver;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;

/**
 * Created by andy on 23.04.18.
 */

public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpPlugin.class);


    private RileyLinkMedtronicService medtronicService;
    protected static MedtronicPumpPlugin plugin = null;
    protected MedtronicPumpStatus pumpStatusLocal = null;

    public static MedtronicPumpPlugin getPlugin() {

        if (plugin == null)
            plugin = new MedtronicPumpPlugin();
        return plugin;
    }


    private MedtronicPumpPlugin() {
        super(new MedtronicPumpDriver(), //
                "MedtronicPump", //
                new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedtronicFragment.class.getName()) //
                        .pluginName(R.string.medtronic_name) //
                        .shortName(R.string.medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic), //
                PumpType.Minimed_512_712 // we default to most basic model, correct model from config is loaded later
        );

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                LOG.debug("Service is disconnected");
                medtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                LOG.debug("Service is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
                medtronicService = mLocalBinder.getServiceInstance();
            }
        };


    }


    @Override
    public void initPumpStatusData() {
        pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        pumpStatusLocal.refreshConfiguration();

        this.pumpStatus = pumpStatusLocal;

        if (pumpStatusLocal.maxBasal != null)
            pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // needs to be changed in configuration, after all functionalities are done
        pumpDescription.isBolusCapable = false;
        pumpDescription.isTempBasalCapable = true;
        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.isRefillingCapable = false;
        pumpDescription.storesCarbInfo = false;

    }

    public void onStartCustomActions() {
    }

    public Class getServiceClass() {
        return RileyLinkMedtronicService.class;
    }


    @Override
    public String deviceID() {
        return "Medtronic";
    }


    //@Override
    //public String shortStatus(boolean veryShort) {
    //    return "Medtronic Pump";
    //}

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }


    // Pump Plugin

    private boolean isServiceSet() {
        return medtronicService != null;
    }

    public boolean isInitialized() {
        return isServiceSet() && medtronicService.isInitialized();
    }

    public boolean isSuspended() {
        return isServiceSet() && medtronicService.isSuspended();
    }

    public boolean isBusy() {
        return isServiceSet() && medtronicService.isBusy();
    }


    public boolean isConnected() {
        return isServiceSet() && medtronicService.isConnected();
    }


    public boolean isConnecting() {
        return isServiceSet() && medtronicService.isConnecting();
    }


    public void connect(String reason) {
        if (isServiceSet()) {
            medtronicService.connect(reason);
        }
    }


    public void disconnect(String reason) {
        if (isServiceSet()) {
            medtronicService.disconnect(reason);
        }
    }


    public void stopConnecting() {
        if (isServiceSet()) {
            medtronicService.stopConnecting();
        }
    }


    public void getPumpStatus() {
        if (isServiceSet()) {
            medtronicService.getPumpStatus();
        }
    }

}
