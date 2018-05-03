package info.nightscout.androidaps.plugins.PumpMedtronic;

import com.gxwtech.roundtrip2.ServiceClientConnection;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.PumpMedtronic.medtronic.MedtronicPumpDriver;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpDriver;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

/**
 * Created by andy on 23.04.18.
 */

public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpPlugin.class);

    private ServiceClientConnection serviceClientConnection;


    public static PumpPluginAbstract getPlugin() {

        if (plugin == null)
            plugin = new MedtronicPumpPlugin();
        return plugin;
    }





    public MedtronicPumpPlugin()
    {
        super(new MedtronicPumpDriver(), //
                "MedtronicPump"//
        );
    }





    @Override
    protected String getInternalName() {
        return "MedtronicPump";
    }

    @Override
    protected void startPumpService() {

        //serviceClientConnection = new ServiceClientConnection();
    }

    @Override
    protected void stopPumpService() {

    }








    @Override
    public JSONObject getJSONStatus() {
        //if (!SP.getBoolean("virtualpump_uploadstatus", false)) {
        //    return null;
        //}
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", 90);
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", 66);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            LOG.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "Medtronic";
    }



    @Override
    public String shortStatus(boolean veryShort) {
        return "Medtronic Pump";
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }


    @Override
    public String getFragmentClass()
    {
        return MedtronicFragment.class.getName();
    }

    @Override
    public String getName() {
        return "Medtronic";
    }

    @Override
    public String getNameShort() {
        return "MEDT";
    }

    @Override
    public boolean isEnabled(int type) {
        // TODO might need tweaking
        if (type == PluginBase.PUMP)
            return fragmentEnabled;
        else if (type == PluginBase.CONSTRAINTS)
            return fragmentEnabled;
        return false;
    }


    @Override
    public int getPreferencesId() {
        return R.xml.pref_medtronic;
    }


    @Override
    public boolean isLoopEnabled() {
        // FIXME check if we need to override
        return getPumpStatusData().validBasalRateProfileSelectedOnPump;
    }

}
