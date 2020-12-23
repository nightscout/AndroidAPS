package info.nightscout.androidaps.plugins.pump.mdi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.R;
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
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.InstanceId;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;


/**
 * Created by mike on 05.08.2016.
 */
@Singleton
public class MDIPlugin extends PumpPluginBase implements PumpInterface {

    private final TreatmentsPlugin treatmentsPlugin;
    private final PumpDescription pumpDescription = new PumpDescription();

    @Inject
    public MDIPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            CommandQueueProvider commandQueue,
            TreatmentsPlugin treatmentsPlugin
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.PUMP)
                        .pluginIcon(R.drawable.ic_ict)
                        .pluginName(R.string.mdi)
                        .description(R.string.description_pump_mdi),
                injector, aapsLogger, resourceHelper, commandQueue
        );
        this.treatmentsPlugin = treatmentsPlugin;

        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.5d;

        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isTempBasalCapable = false;
        pumpDescription.isSetBasalProfileCapable = false;
        pumpDescription.isRefillingCapable = false;
        pumpDescription.isBatteryReplaceable = false;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        PumpEnactResult result = new PumpEnactResult(getInjector());
        return result;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isSuspended() {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
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
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void connect(String reason) {
    }

    @Override
    public void disconnect(String reason) {
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus(String reason) {
    }

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        // Do nothing here. we are using ConfigBuilderPlugin.getPlugin().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = true;
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override
    public long lastDataTime() {
        return System.currentTimeMillis();
    }

    @Override
    public double getBaseBasalRate() {
        return 0d;
    }

    @Override
    public double getReservoirLevel() {
        return -1;
    }

    @Override
    public int getBatteryLevel() {
        return -1;
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
        treatmentsPlugin.addToHistoryTreatment(detailedBolusInfo, false);
        return result;
    }

    @Override
    public void stopBolusDelivering() {
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.pumperror);
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Setting temp basal absolute: " + result);
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.pumperror);
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Settings temp basal percent: " + result);
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.pumperror);
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Setting extended bolus: " + result);
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.pumperror);
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Cancel temp basal: " + result);
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.pumperror);
        getAapsLogger().debug(LTag.PUMPBTCOMM, "Canceling extended bolus: " + result);
        return result;
    }

    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName, String version) {
        long now = System.currentTimeMillis();
        JSONObject pump = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", "normal");
            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception e) {
            }
            status.put("timestamp", DateUtil.toISOString(now));

            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
        }
        return pump;
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.AndroidAPS;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.MDI;
    }

    @NonNull @Override
    public String serialNumber() {
        return InstanceId.INSTANCE.instanceId();
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        return model().getModel();
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
    }

    @Nullable @Override public PumpEnactResult executeCustomCommand(CustomCommand customCommand) {
        return null;
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType changeType) {

    }


}
