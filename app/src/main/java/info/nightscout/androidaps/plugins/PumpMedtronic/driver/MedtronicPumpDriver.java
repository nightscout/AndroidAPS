package info.nightscout.androidaps.plugins.PumpMedtronic.driver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.PumpCommon.utils.PumpUtil;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpDriver;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by andy on 4/28/18.
 */

public class MedtronicPumpDriver extends VirtualPumpDriver /*implements PumpInterface*/ {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpDriver.class);
    MedtronicPumpStatus pumpStatusLocal;

    public MedtronicPumpDriver() {
        pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        pumpStatusLocal.verifyConfiguration();

        this.pumpStatusData = pumpStatusLocal;

        if (pumpStatusLocal.pumpType != null)
            PumpUtil.setPumpDescription(pumpDescription, pumpStatusLocal.pumpType);

        if (pumpStatusLocal.maxBasal != null)
            pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // needs to be changed in configuration, after all functionalities are done
        pumpDescription.isBolusCapable = true;
        pumpDescription.isTempBasalCapable = true;
        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.isRefillingCapable = false;
        pumpDescription.storesCarbInfo = false;
    }


    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void connect(String reason) {
        // connection is established by each command specifically
    }

    @Override
    public void stopConnecting() {
        // we're not doing that
    }


    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {

        // FIXME
        // send Cancel Temp Basal
        return super.cancelTempBasal(enforceNew);
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {

        TemporaryBasal tempBasal = new TemporaryBasal()
                .date(System.currentTimeMillis())
                .absolute(absoluteRate)
                .duration(durationInMinutes)
                .source(Source.USER);

        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            LOG.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        pumpStatusData.setLastDataTimeToNow();
        return result;
    }


}
