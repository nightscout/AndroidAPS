package info.nightscout.androidaps.plugins.pump.omnipod_dash;

import android.content.Context;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.omnipod.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod_dash.comm.OmnipodDashCommunicationManager;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
// FIXME this is just placeholder for now, but this should use most of OmnipodPumpPlugin implementation
public class OmnipodDashPumpPlugin extends OmnipodPumpPlugin implements OmnipodPumpPluginInterface {

    // TODO Dagger
    //private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    //private RileyLinkOmnipodService omnipodService;
    //private OmnipodPumpStatus pumpStatusLocal = null;

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean isRefresh = false;
    private boolean isBasalProfileInvalid = false;
    private boolean basalProfileChanged = false;
    private boolean isInitialized = false;

    public static boolean isBusy = false;
    //private List<Long> busyTimestamps = new ArrayList<>();
    //private boolean sentIdToFirebase = false;
    //private boolean hasTimeDateOrTimeZoneChanged = false;

    private Profile currentProfile;

    //@Inject
    public OmnipodDashPumpPlugin(HasAndroidInjector injector,
                                 AAPSLogger aapsLogger,
                                 RxBusWrapper rxBus,
                                 Context context,
                                 ResourceHelper resourceHelper,
                                 ActivePluginProvider activePlugin,
                                 SP sp,
                                 CommandQueueProvider commandQueue,
                                 FabricPrivacy fabricPrivacy,
                                 DateUtil dateUtil
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(OmnipodFragment.class.getName()) //
                        .pluginName(R.string.omnipod_dash_name) //
                        .shortName(R.string.omnipod_dash_name_short) //
                        .preferencesId(R.xml.pref_omnipod) //
                        .description(R.string.description_pump_omnipod_dash), //
                PumpType.Insulet_Omnipod_Dash,
                injector, aapsLogger, rxBus, context, resourceHelper, activePlugin, sp, commandQueue, fabricPrivacy, dateUtil
        );

        displayConnectionMessages = false;

        //OmnipodUtil.setOmnipodPodType(OmnipodPodType.Dash);

        if (omnipodCommunicationManager == null) {
            omnipodCommunicationManager = OmnipodDashCommunicationManager.getInstance();
        }

        // DG omnipodUIComm = new OmnipodUIComm(omnipodCommunicationManager, this, this.omnipodPumpStatus);

        //OmnipodUtil.setPlugin(this);

        // FIXME
//        serviceConnection = new ServiceConnection() {
//
//            public void onServiceDisconnected(ComponentName name) {
//                if (isLoggingEnabled())
//                    aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is disconnected");
//                omnipodService = null;
//            }
//
//            public void onServiceConnected(ComponentName name, IBinder service) {
//                if (isLoggingEnabled())
//                    aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is connected");
//                RileyLinkOmnipodService.LocalBinder mLocalBinder = (RileyLinkOmnipodService.LocalBinder) service;
//                omnipodService = mLocalBinder.getServiceInstance();
//
//                new Thread(() -> {
//
//                    for (int i = 0; i < 20; i++) {
//                        SystemClock.sleep(5000);
//
//                        if (OmnipodUtil.getPumpStatus() != null) {
//                            if (isLoggingEnabled())
//                                aapsLogger.debug(LTag.PUMP, "Starting OmniPod-RileyLink service");
//                            if (OmnipodUtil.getPumpStatus().setNotInPreInit()) {
//                                break;
//                            }
//                        }
//                    }
//                }).start();
//            }
//        };
    }

    private String getLogPrefix() {
        return "OmnipodPlugin::";
    }


    // FIXME
    public Class getServiceClass() {
        return RileyLinkOmnipodService.class;
    }


    @Override
    public String deviceID() {
        return "Omnipod Dash";
    }


    // Pump Plugin

    private boolean isServiceSet() {
        return true; //omnipodService != null;
    }

    private boolean isServiceInitialized() {
        return true;
    }


    @Override
    public boolean isInitialized() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isInitialized");
        return isServiceSet() && isInitialized;
    }


    @Override
    public boolean isConnected() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isConnected");
        return isServiceSet() && isServiceInitialized();
    }


    @Override
    public boolean isConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isConnecting");
        return !isServiceSet() || !isServiceInitialized();
    }


    @Override
    public void getPumpStatus() {

        if (firstRun) {
            initializePump(!isRefresh);
        }

//        getPodPumpStatus();
//
//        if (firstRun) {
//            initializePump(!isRefresh);
//        } else {
//            refreshAnyStatusThatNeedsToBeRefreshed();
//        }
//
//        MainApp.bus().post(new EventMedtronicPumpValuesChanged());
    }


    public void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }


    private void setRefreshButtonEnabled(boolean enabled) {
        rxBus.send(new EventOmnipodRefreshButtonState(enabled));
    }


    private void initializePump(boolean realInit) {

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "initializePump - start");

        if (omnipodCommunicationManager == null) {
            omnipodCommunicationManager = OmnipodDashCommunicationManager.getInstance();
        }

//        setRefreshButtonEnabled(false);
//
//        getPodPumpStatus();
//
//        if (isRefresh) {
//            if (isPumpNotReachable()) {
//                if (isLoggingEnabled())
//                    LOG.error(getLogPrefix() + "initializePump::Pump unreachable.");
//                MedtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable);
//
//                setRefreshButtonEnabled(true);
//
//                return;
//            }
//
//            MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);
//        }
//
//        this.pumpState = PumpDriverState.Connected;
//
//        pumpStatusLocal.setLastCommunicationToNow();
//        setRefreshButtonEnabled(true);

        // TODO need to read status and BasalProfile if pod inited and pod status and set correct commands enabled

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized;
        }

        if (!sentIdToFirebase) {
            Bundle params = new Bundle();
            params.putString("version", BuildConfig.VERSION);
            //fabricPrivacy.logCustom().logEvent("OmnipodPumpInit", params);

            sentIdToFirebase = true;
        }

        isInitialized = true;
        // this.pumpState = PumpDriverState.Initialized;

        this.firstRun = false;
    }


    protected void triggerUIChange() {
        rxBus.send(new EventOmnipodPumpValuesChanged());
    }


    // OPERATIONS not supported by Pump or Plugin

    //private List<CustomAction> customActions = null;


    @Override
    public List<CustomAction> getCustomActions() {

        if (customActions == null) {
            this.customActions = Arrays.asList(
//                    customActionPairAndPrime, //
//                    customActionFillCanullaSetBasalProfile, //
//                    customActionDeactivatePod, //
//                    customActionResetPod
            );
        }

        return this.customActions;
    }


    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {

//        if (isLoggingEnabled())
//            LOG.warn(getLogPrefix() + "Time, Date and/or TimeZone changed. ");
//
//        this.hasTimeDateOrTimeZoneChanged = true;
    }


}
