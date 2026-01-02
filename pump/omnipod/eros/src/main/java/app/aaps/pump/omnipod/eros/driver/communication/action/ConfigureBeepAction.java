package app.aaps.pump.omnipod.eros.driver.communication.action;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import app.aaps.pump.omnipod.eros.driver.communication.message.command.BeepConfigCommand;
import app.aaps.pump.omnipod.eros.driver.communication.message.response.StatusResponse;
import app.aaps.pump.omnipod.eros.driver.definition.BeepConfigType;
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager;

public class ConfigureBeepAction implements OmnipodAction<StatusResponse> {
    @NonNull private final ErosPodStateManager podStateManager;
    private final BeepConfigType beepType;
    private final boolean basalCompletionBeep;
    private final Duration basalIntervalBeep;
    private final boolean tempBasalCompletionBeep;
    private final Duration tempBasalIntervalBeep;
    private final boolean bolusCompletionBeep;
    private final Duration bolusIntervalBeep;

    public ConfigureBeepAction(@NonNull ErosPodStateManager podState, @NonNull BeepConfigType beepType, boolean basalCompletionBeep, Duration basalIntervalBeep, boolean tempBasalCompletionBeep, Duration tempBasalIntervalBeep, boolean bolusCompletionBeep, Duration bolusIntervalBeep) {
        if (podState == null || beepType == null) {
            throw new IllegalArgumentException("Required parameter(s) missing");
        }

        this.beepType = beepType;
        this.basalCompletionBeep = basalCompletionBeep;
        this.basalIntervalBeep = basalIntervalBeep;
        this.tempBasalCompletionBeep = tempBasalCompletionBeep;
        this.tempBasalIntervalBeep = tempBasalIntervalBeep;
        this.bolusCompletionBeep = bolusCompletionBeep;
        this.bolusIntervalBeep = bolusIntervalBeep;
        this.podStateManager = podState;
    }

    public ConfigureBeepAction(ErosPodStateManager podState, @NonNull BeepConfigType beepType) {
        this(podState, beepType, false, Duration.ZERO, false, Duration.ZERO, false, Duration.ZERO);
    }

    @Override
    public StatusResponse execute(@NonNull OmnipodRileyLinkCommunicationManager communicationService) {
        return communicationService.sendCommand(
                StatusResponse.class, podStateManager,
                new BeepConfigCommand(beepType, basalCompletionBeep, basalIntervalBeep,
                        tempBasalCompletionBeep, tempBasalIntervalBeep,
                        bolusCompletionBeep, bolusIntervalBeep));
    }
}
