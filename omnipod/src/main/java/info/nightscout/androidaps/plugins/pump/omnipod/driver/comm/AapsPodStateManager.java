package info.nightscout.androidaps.plugins.pump.omnipod.driver.comm;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class AapsPodStateManager extends PodStateManager {
    private final AAPSLogger aapsLogger;
    private final SP sp;
    private final RxBusWrapper rxBus;

    @Inject
    public AapsPodStateManager(AAPSLogger aapsLogger, SP sp, RxBusWrapper rxBus) {
        super(aapsLogger);

        if (aapsLogger == null) {
            throw new IllegalArgumentException("aapsLogger can not be null");
        }
        if (sp == null) {
            throw new IllegalArgumentException("sp can not be null");
        }
        if (rxBus == null) {
            throw new IllegalArgumentException("rxBus can not be null");
        }

        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.rxBus = rxBus;
    }

    @Override
    protected String readPodState() {
        return sp.getString(OmnipodConst.Prefs.PodState, "");
    }

    @Override
    protected void storePodState(String podState) {
        sp.putString(OmnipodConst.Prefs.PodState, podState);
    }

    @Override
    protected void notifyPodStateChanged() {
        aapsLogger.debug(LTag.PUMP, "Pod State changed. Sending events.");

        sendEvent(new EventOmnipodPumpValuesChanged());
    }

    private void sendEvent(Event event) {
        rxBus.send(event);
    }
}
