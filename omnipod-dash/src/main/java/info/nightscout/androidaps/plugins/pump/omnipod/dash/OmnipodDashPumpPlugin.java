package info.nightscout.androidaps.plugins.pump.omnipod.dash;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.OmnipodDashOverviewFragment;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

@Singleton
public class OmnipodDashPumpPlugin extends PumpPluginBase implements PumpInterface {
    private static final PumpDescription PUMP_DESCRIPTION = new PumpDescription(PumpType.Omnipod_Dash);

    private final AAPSLogger aapsLogger;
    private final ResourceHelper resourceHelper;
    private final CommandQueueProvider commandQueue;

    @Inject
    public OmnipodDashPumpPlugin(HasAndroidInjector injector, AAPSLogger aapsLogger, ResourceHelper resourceHelper, CommandQueueProvider commandQueue) {
        super(new PluginDescription() //
                .mainType(PluginType.PUMP) //
                .fragmentClass(OmnipodDashOverviewFragment.class.getName()) //
                .pluginIcon(R.drawable.ic_pod_128)
                .pluginName(R.string.omnipod_dash_name) //
                .shortName(R.string.omnipod_dash_name_short) //
                .preferencesId(R.xml.omnipod_dash_preferences) //
                .description(R.string.omnipod_dash_pump_description), injector, aapsLogger, resourceHelper, commandQueue);
        this.aapsLogger = aapsLogger;
        this.resourceHelper = resourceHelper;
        this.commandQueue = commandQueue;
    }

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

    @Override public PumpEnactResult setNewBasalProfile(Profile profile) {
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

    @Override public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return null;
    }

    @Override public void stopBolusDelivering() {

    }

    @Override public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    @Override public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    @Override public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return null;
    }

    @Override public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        return null;
    }

    @Override public PumpEnactResult cancelExtendedBolus() {
        return null;
    }

    @Override public JSONObject getJSONStatus(Profile profile, String profileName, String version) {
        return null;
    }

    @Override public ManufacturerType manufacturer() {
        return getPumpDescription().pumpType.getManufacturer();
    }

    @Override public PumpType model() {
        return getPumpDescription().pumpType;
    }

    @Override public String serialNumber() {
        return null;
    }

    @Override public PumpDescription getPumpDescription() {
        return PUMP_DESCRIPTION;
    }

    @Override public String shortStatus(boolean veryShort) {
        return null;
    }

    @Override public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override public PumpEnactResult loadTDDs() {
        return null;
    }

    @Override public boolean canHandleDST() {
        return false;
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return Collections.emptyList();
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        aapsLogger.warn(LTag.PUMP, "Unknown custom action: " + customActionType);
    }

    @Nullable @Override public PumpEnactResult executeCustomCommand(CustomCommand customCommand) {
        return null;
    }

    @Override public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {

    }
}
