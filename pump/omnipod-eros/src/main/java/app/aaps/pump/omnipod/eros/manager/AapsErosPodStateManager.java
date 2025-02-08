package app.aaps.pump.omnipod.eros.manager;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.EventDismissNotification;
import app.aaps.core.keys.interfaces.Preferences;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosActiveAlertsChanged;
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosFaultEventChanged;
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosTbrChanged;
import app.aaps.pump.omnipod.eros.event.EventOmnipodErosUncertainTbrRecovered;
import app.aaps.pump.omnipod.eros.keys.ErosStringNonPreferenceKey;

@Singleton
public class AapsErosPodStateManager extends ErosPodStateManager {
    private final Preferences preferences;
    private final RxBus rxBus;

    @Inject
    public AapsErosPodStateManager(AAPSLogger aapsLogger, Preferences preferences, RxBus rxBus) {
        super(aapsLogger);
        this.preferences = preferences;
        this.rxBus = rxBus;
    }

    @Override
    protected String readPodState() {
        return preferences.get(ErosStringNonPreferenceKey.PodState);
    }

    @Override
    protected void storePodState(String podState) {
        preferences.put(ErosStringNonPreferenceKey.PodState, podState);
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
