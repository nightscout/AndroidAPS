package info.nightscout.androidaps.plugins.pump.omnipod.dash;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.List;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.TimeChangeType;

public class OmnipodDashPumpPlugin implements PumpInterface {
    @Override public boolean isInitialized() {
        return false;
    }

    @Override public boolean isSuspended() {
        return false;
    }

    @Override public boolean isBusy() {
        return false;
    }

    @Override public boolean isConnected() {
        return false;
    }

    @Override public boolean isConnecting() {
        return false;
    }

    @Override public boolean isHandshakeInProgress() {
        return false;
    }

    @Override public void finishHandshaking() {

    }

    @Override public void connect(String reason) {

    }

    @Override public void disconnect(String reason) {

    }

    @Override public void stopConnecting() {

    }

    @Override public void getPumpStatus(String reason) {

    }

    @NotNull @Override public PumpEnactResult setNewBasalProfile(Profile profile) {
        return null;
    }

    @Override public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override public long lastDataTime() {
        return 0;
    }

    @Override public double getBaseBasalRate() {
        return 0;
    }

    @Override public double getReservoirLevel() {
        return 0;
    }

    @Override public int getBatteryLevel() {
        return 0;
    }

    @NotNull @Override public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return null;
    }

    @Override public void stopBolusDelivering() {

    }

    @NotNull @Override public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    @NotNull @Override public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    @NotNull @Override public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return null;
    }

    @NotNull @Override public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        return null;
    }

    @NotNull @Override public PumpEnactResult cancelExtendedBolus() {
        return null;
    }

    @NotNull @Override public JSONObject getJSONStatus(Profile profile, String profileName, String version) {
        return null;
    }

    @NotNull @Override public ManufacturerType manufacturer() {
        return null;
    }

    @NotNull @Override public PumpType model() {
        return null;
    }

    @NotNull @Override public String serialNumber() {
        return null;
    }

    @NotNull @Override public PumpDescription getPumpDescription() {
        return null;
    }

    @NotNull @Override public String shortStatus(boolean veryShort) {
        return null;
    }

    @Override public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @NotNull @Override public PumpEnactResult loadTDDs() {
        return null;
    }

    @Override public boolean canHandleDST() {
        return false;
    }

    @Nullable @Override public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override public void executeCustomAction(CustomActionType customActionType) {

    }

    @Nullable @Override public PumpEnactResult executeCustomCommand(CustomCommand customCommand) {
        return null;
    }

    @Override public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {

    }
}
