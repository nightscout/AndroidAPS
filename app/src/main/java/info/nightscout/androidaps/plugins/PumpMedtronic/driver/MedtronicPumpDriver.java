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
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.PumpVirtual.VirtualPumpDriver;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;

/**
 * Created by andy on 4/28/18.
 */
@Deprecated
public class MedtronicPumpDriver extends VirtualPumpDriver /* implements PumpInterface */{

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpDriver.class);


    // MedtronicPumpStatus pumpStatusLocal;

    public MedtronicPumpDriver() {

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
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
            boolean enforceNew) {

        TemporaryBasal tempBasal = new TemporaryBasal().date(System.currentTimeMillis()).absolute(absoluteRate)
            .duration(durationInMinutes).source(Source.USER);

        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        if (L.isEnabled(L.PUMP))
            LOG.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        getPumpStatusData().setLastCommunicationToNow();
        return result;
    }

}
