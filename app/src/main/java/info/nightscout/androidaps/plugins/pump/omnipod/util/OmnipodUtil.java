package info.nightscout.androidaps.plugins.pump.omnipod.util;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.data.RLHistoryItem;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.OmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPodType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodDeviceState;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodSessionState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.omnipod.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod_dash.OmnipodDashPumpPlugin;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by andy on 4/8/19.
 */
// FIXME
public class OmnipodUtil extends RileyLinkUtil {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMPCOMM);

    private static RileyLinkOmnipodService omnipodService;
    private static OmnipodPumpStatus omnipodPumpStatus;
    private static OmnipodCommandType currentCommand;
    private static Gson gsonInstance = createGson();
    //private static PodSessionState podSessionState;
    //private static PodDeviceState podDeviceState;
    private static OmnipodPumpPluginInterface omnipodPumpPlugin;
    private static OmnipodPodType omnipodPodType;
    private static OmnipodDriverState driverState = OmnipodDriverState.NotInitalized;
    private static PumpType pumpType;

    public static Gson getGsonInstance() {
        return gsonInstance;
    }

    public static OmnipodCommunicationManagerInterface getOmnipodCommunicationManager() {
        return (OmnipodCommunicationManagerInterface) RileyLinkUtil.rileyLinkCommunicationManager;
    }

    public static RileyLinkOmnipodService getOmnipodService() {
        return OmnipodUtil.omnipodService;
    }

    public static void setOmnipodService(RileyLinkOmnipodService medtronicService) {
        OmnipodUtil.omnipodService = medtronicService;
    }

    public static OmnipodCommandType getCurrentCommand() {
        return OmnipodUtil.currentCommand;
    }

    // FIXME
    public static void setCurrentCommand(OmnipodCommandType currentCommand) {
        OmnipodUtil.currentCommand = currentCommand;

        if (currentCommand != null)
            historyRileyLink.add(new RLHistoryItem(currentCommand));
    }

    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.000001);
    }

    public static void displayNotConfiguredDialog(Context context) {
        OKDialog.show(context, MainApp.gs(R.string.combo_warning),
                MainApp.gs(R.string.omnipod_error_operation_not_possible_no_configuration), null);
    }

    public static OmnipodPumpStatus getPumpStatus() {
        return omnipodPumpStatus;
    }

    public static OmnipodDriverState getDriverState() {
        return OmnipodUtil.driverState;
    }

    public static void setDriverState(OmnipodDriverState state) {
        if (OmnipodUtil.driverState == state)
            return;

        OmnipodUtil.driverState = state;

        // TODO maybe remove
//        if (OmnipodUtil.omnipodPumpStatus != null) {
//            OmnipodUtil.omnipodPumpStatus.driverState = state;
//        }
//
//        if (OmnipodUtil.omnipodPumpPlugin != null) {
//            OmnipodUtil.omnipodPumpPlugin.setDriverState(state);
//        }
    }

    public static void setPumpStatus(OmnipodPumpStatus omnipodPumpStatus) {
        OmnipodUtil.omnipodPumpStatus = omnipodPumpStatus;
    }

    private static Gson createGson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(DateTime.class, (JsonSerializer<DateTime>) (dateTime, typeOfSrc, context) ->
                        new JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)))
                .registerTypeAdapter(DateTime.class, (JsonDeserializer<DateTime>) (json, typeOfT, context) ->
                        ISODateTimeFormat.dateTime().parseDateTime(json.getAsString()))
                .registerTypeAdapter(DateTimeZone.class, (JsonSerializer<DateTimeZone>) (timeZone, typeOfSrc, context) ->
                        new JsonPrimitive(timeZone.getID()))
                .registerTypeAdapter(DateTimeZone.class, (JsonDeserializer<DateTimeZone>) (json, typeOfT, context) ->
                        DateTimeZone.forID(json.getAsString()));

        return gsonBuilder.create();
    }

    public static void setPodSessionState(PodSessionState podSessionState) {
        omnipodPumpStatus.podSessionState = podSessionState;
        RxBus.INSTANCE.send(new EventOmnipodDeviceStatusChange(podSessionState));
    }


    public static void setPodDeviceState(PodDeviceState podDeviceState) {
        omnipodPumpStatus.podDeviceState = podDeviceState;
    }


    @NotNull
    public static OmnipodPumpPluginInterface getPlugin() {
        return OmnipodUtil.omnipodPumpPlugin;
    }


    @NotNull
    public static void setPlugin(OmnipodPumpPluginInterface pumpPlugin) {
        OmnipodUtil.omnipodPumpPlugin = pumpPlugin;
    }


    public static void setOmnipodPodType(OmnipodPodType omnipodPodType) {
        OmnipodUtil.omnipodPodType = omnipodPodType;
    }

    public static OmnipodPodType getOmnipodPodType() {
        return omnipodPodType;
    }

    public static PodDeviceState getPodDeviceState() {
        return omnipodPumpStatus.podDeviceState;
    }


    public static PodSessionState getPodSessionState() {
        return omnipodPumpStatus.podSessionState;
    }

    public static boolean isOmnipodEros() {
        return OmnipodPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
    }

    public static boolean isOmnipodDash() {
        return OmnipodDashPumpPlugin.getPlugin().isEnabled(PluginType.PUMP);
    }

    public static void setPumpType(PumpType pumpType) {
        OmnipodUtil.pumpType = pumpType;
    }

    public static PumpType getPumpType() {
        return pumpType;
    }

    public static Integer getNextPodAddress() {
        if(SP.contains(OmnipodConst.Prefs.NextPodAddress)) {
            int nextPodAddress = SP.getInt(OmnipodConst.Prefs.NextPodAddress, 0);
            if (OmnipodManager.isValidAddress(nextPodAddress)) {
                return nextPodAddress;
            }
        }
        return null;
    }

    public static boolean hasNextPodAddress() {
        return getNextPodAddress() != null;
    }

    public static void setNextPodAddress(int address) {
        SP.putInt(OmnipodConst.Prefs.NextPodAddress, address);
    }

    public static void removeNextPodAddress() {
        SP.remove(OmnipodConst.Prefs.NextPodAddress);
    }
}
