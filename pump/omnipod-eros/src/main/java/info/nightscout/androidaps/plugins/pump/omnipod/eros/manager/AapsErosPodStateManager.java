package info.nightscout.androidaps.plugins.pump.omnipod.eros.manager;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.EventDismissNotification;
import app.aaps.core.interfaces.sharedPreferences.SP;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.OmnipodErosStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosActiveAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosFaultEventChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosTbrChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosUncertainTbrRecovered;

@Singleton
public class AapsErosPodStateManager extends ErosPodStateManager {
    private final SP sp;
    private final RxBus rxBus;

    @Inject
    public AapsErosPodStateManager(AAPSLogger aapsLogger, SP sp, RxBus rxBus) {
        super(aapsLogger);
        this.sp = sp;
        this.rxBus = rxBus;
    }

    @Override
    protected String readPodState() {
        return sp.getString(OmnipodErosStorageKeys.Preferences.POD_STATE, "");
    }

    @Override
    protected void storePodState(String podState) {
        sp.putString(OmnipodErosStorageKeys.Preferences.POD_STATE, podState);
    }

    @Override protected void onUncertainTbrRecovered() {
        rxBus.send(new EventOmnipodErosUncertainTbrRecovered());
    }

    @Override protected void onTbrChanged() {
        rxBus.send(new EventOmnipodErosTbrChanged());
    }

    @Override protected void onActiveAlertsChanged() {
        rxBus.send(new EventOmnipodErosActiveAlertsChanged());
    }

    @Override protected void onFaultEventChanged() {
        rxBus.send(new EventOmnipodErosFaultEventChanged());
    }

    @Override protected void onUpdatedFromResponse() {
        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED));
    }
}
