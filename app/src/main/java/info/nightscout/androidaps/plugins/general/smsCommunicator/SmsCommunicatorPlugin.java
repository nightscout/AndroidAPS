package info.nightscout.androidaps.plugins.general.smsCommunicator;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;

import com.squareup.otto.Subscribe;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus;
import info.nightscout.androidaps.data.IobTotal;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.db.BgReading;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.general.smsCommunicator.events.EventSmsCommunicatorUpdateGui;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.CobInfo;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.services.Intents;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.SafeParse;
import info.nightscout.androidaps.utils.XdripCalibrations;

/**
 * Created by mike on 05.08.2016.
 */
public class SmsCommunicatorPlugin extends PluginBase {
    private static Logger log = LoggerFactory.getLogger(L.SMS);

    private static SmsCommunicatorPlugin smsCommunicatorPlugin;

    public static SmsCommunicatorPlugin getPlugin() {

        if (smsCommunicatorPlugin == null) {
            smsCommunicatorPlugin = new SmsCommunicatorPlugin();
        }
        return smsCommunicatorPlugin;
    }

    List<String> allowedNumbers = new ArrayList<>();

    AuthRequest messageToConfirm = null;

    long lastRemoteBolusTime = 0;

    ArrayList<Sms> messages = new ArrayList<>();

    SmsCommunicatorPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.GENERAL)
                .fragmentClass(SmsCommunicatorFragment.class.getName())
                .pluginName(R.string.smscommunicator)
                .shortName(R.string.smscommunicator_shortname)
                .preferencesId(R.xml.pref_smscommunicator)
                .description(R.string.description_sms_communicator)
        );
        processSettings(null);
    }

    @Override
    protected void onStart() {
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void processSettings(final EventPreferenceChange ev) {
        if (ev == null || ev.isChanged(R.string.key_smscommunicator_allowednumbers)) {
            String settings = SP.getString(R.string.key_smscommunicator_allowednumbers, "");

            String pattern = ";";

            String[] substrings = settings.split(pattern);
            for (String number : substrings) {
                String cleaned = number.replaceAll("\\s+", "");
                allowedNumbers.add(cleaned);
                log.debug("Found allowed number: " + cleaned);
            }
        }
    }

    boolean isCommand(String command, String number) {
        switch (command.toUpperCase()) {
            case "BG":
            case "LOOP":
            case "TREATMENTS":
            case "NSCLIENT":
            case "PUMP":
            case "BASAL":
            case "BOLUS":
            case "EXTENDED":
            case "CAL":
            case "PROFILE":
                return true;
        }
        if (messageToConfirm != null && messageToConfirm.requester.phoneNumber.equals(number))
            return true;
        return false;
    }

    boolean isAllowedNumber(String number) {
        for (String num : allowedNumbers) {
            if (num.equals(number)) return true;
        }
        return false;
    }

    public void handleNewData(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus != null) {
            // For every SMS message received
            for (Object pdu : pdus) {
                SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                processSms(new Sms(message));
            }
        }
    }

    void processSms(final Sms receivedSms) {
        if (!isEnabled(PluginType.GENERAL)) {
            log.debug("Ignoring SMS. Plugin disabled.");
            return;
        }
        if (!isAllowedNumber(receivedSms.phoneNumber)) {
            log.debug("Ignoring SMS from: " + receivedSms.phoneNumber + ". Sender not allowed");
            receivedSms.ignored = true;
            messages.add(receivedSms);
            MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
            return;
        }

        messages.add(receivedSms);
        log.debug(receivedSms.toString());

        String[] splitted = receivedSms.text.split("\\s+");
        boolean remoteCommandsAllowed = SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false);

        if (splitted.length > 0 && isCommand(splitted[0].toUpperCase(), receivedSms.phoneNumber)) {
            switch (splitted[0].toUpperCase()) {
                case "BG":
                    processBG(splitted, receivedSms);
                    break;
                case "LOOP":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2 || splitted.length == 3)
                        processLOOP(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "TREATMENTS":
                    if (splitted.length == 2)
                        processTREATMENTS(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "NSCLIENT":
                    if (splitted.length == 2)
                        processNSCLIENT(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "PUMP":
                    processPUMP(splitted, receivedSms);
                    break;
                case "PROFILE":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2 || splitted.length == 3)
                        processPROFILE(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "BASAL":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2 || splitted.length == 3)
                        processBASAL(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "EXTENDED":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2 || splitted.length == 3)
                        processEXTENDED(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "BOLUS":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2 && DateUtil.now() - lastRemoteBolusTime < Constants.remoteBolusMinDistance)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotebolusnotallowed));
                    else if (splitted.length == 2 && ConfigBuilderPlugin.getPlugin().getActivePump().isSuspended())
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.pumpsuspended));
                    else if (splitted.length == 2)
                        processBOLUS(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                case "CAL":
                    if (!remoteCommandsAllowed)
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_remotecommandnotallowed));
                    else if (splitted.length == 2)
                        processCAL(splitted, receivedSms);
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                    break;
                default: // expect passCode here
                    if (messageToConfirm != null && messageToConfirm.requester.phoneNumber.equals(receivedSms.phoneNumber)) {
                        messageToConfirm.action(splitted[0]);
                        messageToConfirm = null;
                    } else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_unknowncommand));
                    break;
            }
        }

        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
    }

    @SuppressWarnings("unused")
    private void processBG(String[] splitted, Sms receivedSms) {
        BgReading actualBG = DatabaseHelper.actualBg();
        BgReading lastBG = DatabaseHelper.lastBg();

        String reply = "";

        String units = ProfileFunctions.getInstance().getProfileUnits();

        if (actualBG != null) {
            reply = MainApp.gs(R.string.sms_actualbg) + " " + actualBG.valueToUnitsToString(units) + ", ";
        } else if (lastBG != null) {
            Long agoMsec = System.currentTimeMillis() - lastBG.date;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            reply = MainApp.gs(R.string.sms_lastbg) + " " + lastBG.valueToUnitsToString(units) + " " + String.format(MainApp.gs(R.string.sms_minago), agoMin) + ", ";
        }
        GlucoseStatus glucoseStatus = GlucoseStatus.getGlucoseStatusData();
        if (glucoseStatus != null)
            reply += MainApp.gs(R.string.sms_delta) + " " + Profile.toUnitsString(glucoseStatus.delta, glucoseStatus.delta * Constants.MGDL_TO_MMOLL, units) + " " + units + ", ";

        TreatmentsPlugin.getPlugin().updateTotalIOBTreatments();
        IobTotal bolusIob = TreatmentsPlugin.getPlugin().getLastCalculationTreatments().round();
        TreatmentsPlugin.getPlugin().updateTotalIOBTempBasals();
        IobTotal basalIob = TreatmentsPlugin.getPlugin().getLastCalculationTempBasals().round();

        String cobText = MainApp.gs(R.string.value_unavailable_short);
        CobInfo cobInfo = IobCobCalculatorPlugin.getPlugin().getCobInfo(false, "SMS COB");

        reply += MainApp.gs(R.string.sms_iob) + " " + DecimalFormatter.to2Decimal(bolusIob.iob + basalIob.basaliob) + "U ("
                + MainApp.gs(R.string.sms_bolus) + " " + DecimalFormatter.to2Decimal(bolusIob.iob) + "U "
                + MainApp.gs(R.string.sms_basal) + " " + DecimalFormatter.to2Decimal(basalIob.basaliob) + "U), "
                + MainApp.gs(R.string.cob) + ": " + cobInfo.generateCOBString();

        sendSMS(new Sms(receivedSms.phoneNumber, reply));
        receivedSms.processed = true;
    }

    private void processLOOP(String[] splitted, Sms receivedSms) {
        String reply;
        switch (splitted[1].toUpperCase()) {
            case "DISABLE":
            case "STOP":
                LoopPlugin loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                if (loopPlugin != null && loopPlugin.isEnabled(PluginType.LOOP)) {
                    loopPlugin.setPluginEnabled(PluginType.LOOP, false);
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                        @Override
                        public void run() {
                            MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_STOP"));
                            String reply = MainApp.gs(R.string.smscommunicator_loophasbeendisabled) + " " +
                                    MainApp.gs(result.success ? R.string.smscommunicator_tempbasalcanceled : R.string.smscommunicator_tempbasalcancelfailed);
                            sendSMS(new Sms(receivedSms.phoneNumber, reply));
                        }
                    });
                } else {
                    sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopisdisabled));
                }
                receivedSms.processed = true;
                break;
            case "ENABLE":
            case "START":
                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                if (loopPlugin != null && !loopPlugin.isEnabled(PluginType.LOOP)) {
                    loopPlugin.setPluginEnabled(PluginType.LOOP, true);
                    sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_loophasbeenenabled));
                    MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_START"));
                } else {
                    sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopisenabled));
                }
                receivedSms.processed = true;
                break;
            case "STATUS":
                loopPlugin = MainApp.getSpecificPlugin(LoopPlugin.class);
                if (loopPlugin != null) {
                    if (loopPlugin.isEnabled(PluginType.LOOP)) {
                        if (loopPlugin.isSuspended())
                            reply = String.format(MainApp.gs(R.string.loopsuspendedfor), loopPlugin.minutesToEndOfSuspend());
                        else
                            reply = MainApp.gs(R.string.smscommunicator_loopisenabled);
                    } else {
                        reply = MainApp.gs(R.string.smscommunicator_loopisdisabled);
                    }
                    sendSMS(new Sms(receivedSms.phoneNumber, reply));
                }
                receivedSms.processed = true;
                break;
            case "RESUME":
                LoopPlugin.getPlugin().suspendTo(0);
                MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_RESUME"));
                NSUpload.uploadOpenAPSOffline(0);
                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_loopresumed));
                break;
            case "SUSPEND":
                int duration = 0;
                if (splitted.length == 3)
                    duration = SafeParse.stringToInt(splitted[2]);
                duration = Math.max(0, duration);
                duration = Math.min(180, duration);
                if (duration == 0) {
                    receivedSms.processed = true;
                    sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_wrongduration));
                    return;
                } else {
                    String passCode = generatePasscode();
                    reply = String.format(MainApp.gs(R.string.smscommunicator_suspendreplywithcode), duration, passCode);
                    receivedSms.processed = true;
                    messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(duration) {
                        @Override
                        public void run() {
                            ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                                @Override
                                public void run() {
                                    if (result.success) {
                                        LoopPlugin.getPlugin().suspendTo(System.currentTimeMillis() + anInteger * 60L * 1000);
                                        NSUpload.uploadOpenAPSOffline(anInteger * 60);
                                        MainApp.bus().post(new EventRefreshOverview("SMS_LOOP_SUSPENDED"));
                                        String reply = MainApp.gs(R.string.smscommunicator_loopsuspended) + " " +
                                                MainApp.gs(result.success ? R.string.smscommunicator_tempbasalcanceled : R.string.smscommunicator_tempbasalcancelfailed);
                                        sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                                    } else {
                                        String reply = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed);
                                        reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                        sendSMS(new Sms(receivedSms.phoneNumber, reply));
                                    }
                                }
                            });

                        }
                    });
                }
                break;
            default:
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
                break;
        }
    }

    private void processTREATMENTS(String[] splitted, Sms receivedSms) {
        if (splitted[1].toUpperCase().equals("REFRESH")) {
            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
            TreatmentsPlugin.getPlugin().getService().resetTreatments();
            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
            List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
            String reply = "TREATMENTS REFRESH " + q.size() + " receivers";
            sendSMS(new Sms(receivedSms.phoneNumber, reply));
            receivedSms.processed = true;
        } else
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
    }

    private void processNSCLIENT(String[] splitted, Sms receivedSms) {
        if (splitted[1].toUpperCase().equals("RESTART")) {
            Intent restartNSClient = new Intent(Intents.ACTION_RESTART);
            MainApp.instance().getApplicationContext().sendBroadcast(restartNSClient);
            List<ResolveInfo> q = MainApp.instance().getApplicationContext().getPackageManager().queryBroadcastReceivers(restartNSClient, 0);
            String reply = "NSCLIENT RESTART " + q.size() + " receivers";
            sendSMS(new Sms(receivedSms.phoneNumber, reply));
            receivedSms.processed = true;
        } else
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
    }

    @SuppressWarnings("unused")
    private void processPUMP(String[] splitted, Sms receivedSms) {
        ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("SMS", new Callback() {
            @Override
            public void run() {
                PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                if (result.success) {
                    if (pump != null) {
                        String reply = pump.shortStatus(true);
                        sendSMS(new Sms(receivedSms.phoneNumber, reply));
                    }
                } else {
                    String reply = MainApp.gs(R.string.readstatusfailed);
                    sendSMS(new Sms(receivedSms.phoneNumber, reply));
                }
            }
        });
        receivedSms.processed = true;
    }

    private void processPROFILE(String[] splitted, Sms receivedSms) {
        // load profiles
        ProfileInterface anInterface = ConfigBuilderPlugin.getPlugin().getActiveProfileInterface();
        if (anInterface == null) {
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.notconfigured));
            receivedSms.processed = true;
            return;
        }
        ProfileStore store = anInterface.getProfile();
        if (store == null) {
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.notconfigured));
            receivedSms.processed = true;
            return;
        }
        final ArrayList<CharSequence> list = store.getProfileList();

        if (splitted[1].toUpperCase().equals("STATUS")) {
            sendSMS(new Sms(receivedSms.phoneNumber, ProfileFunctions.getInstance().getProfileName()));
        } else if (splitted[1].toUpperCase().equals("LIST")) {
            if (list.isEmpty())
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.invalidprofile));
            else {
                String reply = "";
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0)
                        reply += "\n";
                    reply += (i + 1) + ". ";
                    reply += list.get(i);
                }
                sendSMS(new Sms(receivedSms.phoneNumber, reply));
            }
        } else {

            int pindex = SafeParse.stringToInt(splitted[1]);
            int percentage = 100;
            if (splitted.length > 2)
                percentage = SafeParse.stringToInt(splitted[2]);

            if (pindex > list.size())
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else if (percentage == 0)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else if (pindex == 0)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else {
                final Profile profile = store.getSpecificProfile((String) list.get(pindex - 1));
                if (profile == null)
                    sendSMS(new Sms(receivedSms.phoneNumber, R.string.noprofile));
                else {
                    String passCode = generatePasscode();
                    String reply = String.format(MainApp.gs(R.string.smscommunicator_profilereplywithcode), list.get(pindex - 1), percentage, passCode);
                    receivedSms.processed = true;
                    int finalPercentage = percentage;
                    messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction((String) list.get(pindex - 1), finalPercentage) {
                        @Override
                        public void run() {
                            ProfileFunctions.doProfileSwitch(store, (String) list.get(pindex - 1), 0, finalPercentage, 0);
                            sendSMS(new Sms(receivedSms.phoneNumber, R.string.profileswitchcreated));
                        }
                    });
                }
            }
        }
        receivedSms.processed = true;
    }

    private void processBASAL(String[] splitted, Sms receivedSms) {
        if (splitted[1].toUpperCase().equals("CANCEL") || splitted[1].toUpperCase().equals("STOP")) {
            String passCode = generatePasscode();
            String reply = String.format(MainApp.gs(R.string.smscommunicator_basalstopreplywithcode), passCode);
            receivedSms.processed = true;
            messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction() {
                @Override
                public void run() {
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, new Callback() {
                        @Override
                        public void run() {
                            if (result.success) {
                                String reply = MainApp.gs(R.string.smscommunicator_tempbasalcanceled);
                                reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                            } else {
                                String reply = MainApp.gs(R.string.smscommunicator_tempbasalcancelfailed);
                                reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply));
                            }
                        }
                    });
                }
            });
        } else if (splitted[1].endsWith("%")) {
            int tempBasalPct = SafeParse.stringToInt(StringUtils.removeEnd(splitted[1], "%"));
            int duration = 30;
            if (splitted.length > 2)
                duration = SafeParse.stringToInt(splitted[2]);
            final Profile profile = ProfileFunctions.getInstance().getProfile();

            if (profile == null)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.noprofile));
            else if (tempBasalPct == 0 && !splitted[1].equals("0%"))
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else if (duration == 0)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else {
                tempBasalPct = MainApp.getConstraintChecker().applyBasalPercentConstraints(new Constraint<>(tempBasalPct), profile).value();
                String passCode = generatePasscode();
                String reply = String.format(MainApp.gs(R.string.smscommunicator_basalpctreplywithcode), tempBasalPct, duration, passCode);
                receivedSms.processed = true;
                messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(tempBasalPct, duration) {
                    @Override
                    public void run() {
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalPercent(anInteger, secondInteger, true, profile, new Callback() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    String reply;
                                    if (result.isPercent)
                                        reply = String.format(MainApp.gs(R.string.smscommunicator_tempbasalset_percent), result.percent, result.duration);
                                    else
                                        reply = String.format(MainApp.gs(R.string.smscommunicator_tempbasalset), result.absolute, result.duration);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                                } else {
                                    String reply = MainApp.gs(R.string.smscommunicator_tempbasalfailed);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply));
                                }
                            }
                        });
                    }
                });
            }
        } else {
            Double tempBasal = SafeParse.stringToDouble(splitted[1]);
            int duration = 30;
            if (splitted.length > 2)
                duration = SafeParse.stringToInt(splitted[2]);
            final Profile profile = ProfileFunctions.getInstance().getProfile();
            if (profile == null)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.noprofile));
            else if (tempBasal == 0 && !splitted[1].equals("0"))
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else if (duration == 0)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else {
                tempBasal = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(tempBasal), profile).value();
                String passCode = generatePasscode();
                String reply = String.format(MainApp.gs(R.string.smscommunicator_basalreplywithcode), tempBasal, duration, passCode);
                receivedSms.processed = true;
                messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(tempBasal, duration) {
                    @Override
                    public void run() {
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().tempBasalAbsolute(aDouble, secondInteger, true, profile, new Callback() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    String reply;
                                    if (result.isPercent)
                                        reply = String.format(MainApp.gs(R.string.smscommunicator_tempbasalset_percent), result.percent, result.duration);
                                    else
                                        reply = String.format(MainApp.gs(R.string.smscommunicator_tempbasalset), result.absolute, result.duration);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                                } else {
                                    String reply = MainApp.gs(R.string.smscommunicator_tempbasalfailed);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply));
                                }
                            }
                        });
                    }
                });
            }
        }
    }

    private void processEXTENDED(String[] splitted, Sms receivedSms) {
        if (splitted[1].toUpperCase().equals("CANCEL") || splitted[1].toUpperCase().equals("STOP")) {
            String passCode = generatePasscode();
            String reply = String.format(MainApp.gs(R.string.smscommunicator_extendedstopreplywithcode), passCode);
            receivedSms.processed = true;
            messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction() {
                @Override
                public void run() {
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelExtended(new Callback() {
                        @Override
                        public void run() {
                            if (result.success) {
                                String reply = MainApp.gs(R.string.smscommunicator_extendedcanceled);
                                reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                            } else {
                                String reply = MainApp.gs(R.string.smscommunicator_extendedcancelfailed);
                                reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                sendSMS(new Sms(receivedSms.phoneNumber, reply));
                            }
                        }
                    });
                }
            });
        } else if (splitted.length != 3) {
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
        } else {
            Double extended = SafeParse.stringToDouble(splitted[1]);
            int duration = SafeParse.stringToInt(splitted[2]);
            extended = MainApp.getConstraintChecker().applyExtendedBolusConstraints(new Constraint<>(extended)).value();
            if (extended == 0 || duration == 0)
                sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
            else {
                String passCode = generatePasscode();
                String reply = String.format(MainApp.gs(R.string.smscommunicator_extendedreplywithcode), extended, duration, passCode);
                receivedSms.processed = true;
                messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(extended, duration) {
                    @Override
                    public void run() {
                        ConfigBuilderPlugin.getPlugin().getCommandQueue().extendedBolus(aDouble, secondInteger, new Callback() {
                            @Override
                            public void run() {
                                if (result.success) {
                                    String reply = String.format(MainApp.gs(R.string.smscommunicator_extendedset), aDouble, duration);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                                } else {
                                    String reply = MainApp.gs(R.string.smscommunicator_extendedfailed);
                                    reply += "\n" + ConfigBuilderPlugin.getPlugin().getActivePump().shortStatus(true);
                                    sendSMS(new Sms(receivedSms.phoneNumber, reply));
                                }
                            }
                        });
                    }
                });
            }
        }
    }


    private void processBOLUS(String[] splitted, Sms receivedSms) {
        Double bolus = SafeParse.stringToDouble(splitted[1]);
        bolus = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(bolus)).value();
        if (bolus > 0d) {
            String passCode = generatePasscode();
            String reply = String.format(MainApp.gs(R.string.smscommunicator_bolusreplywithcode), bolus, passCode);
            receivedSms.processed = true;
            messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(bolus) {
                @Override
                public void run() {
                    DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
                    detailedBolusInfo.insulin = aDouble;
                    detailedBolusInfo.source = Source.USER;
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().bolus(detailedBolusInfo, new Callback() {
                        @Override
                        public void run() {
                            final boolean resultSuccess = result.success;
                            final double resultBolusDelivered = result.bolusDelivered;
                            ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("SMS", new Callback() {
                                @Override
                                public void run() {
                                    PumpInterface pump = ConfigBuilderPlugin.getPlugin().getActivePump();
                                    if (resultSuccess) {
                                        String reply = String.format(MainApp.gs(R.string.smscommunicator_bolusdelivered), resultBolusDelivered);
                                        if (pump != null)
                                            reply += "\n" + pump.shortStatus(true);
                                        lastRemoteBolusTime = DateUtil.now();
                                        sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, reply));
                                    } else {
                                        String reply = MainApp.gs(R.string.smscommunicator_bolusfailed);
                                        if (pump != null)
                                            reply += "\n" + pump.shortStatus(true);
                                        sendSMS(new Sms(receivedSms.phoneNumber, reply));
                                    }
                                }
                            });
                        }
                    });
                }
            });
        } else
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
    }

    private void processCAL(String[] splitted, Sms receivedSms) {
        Double cal = SafeParse.stringToDouble(splitted[1]);
        if (cal > 0d) {
            String passCode = generatePasscode();
            String reply = String.format(MainApp.gs(R.string.smscommunicator_calibrationreplywithcode), cal, passCode);
            receivedSms.processed = true;
            messageToConfirm = new AuthRequest(this, receivedSms, reply, passCode, new SmsAction(cal) {
                @Override
                public void run() {
                    boolean result = XdripCalibrations.sendIntent(aDouble);
                    if (result)
                        sendSMSToAllNumbers(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_calibrationsent));
                    else
                        sendSMS(new Sms(receivedSms.phoneNumber, R.string.smscommunicator_calibrationfailed));
                }
            });
        } else
            sendSMS(new Sms(receivedSms.phoneNumber, R.string.wrongformat));
    }

    public void sendNotificationToAllNumbers(String text) {
        for (int i = 0; i < allowedNumbers.size(); i++) {
            Sms sms = new Sms(allowedNumbers.get(i), text);
            sendSMS(sms);
        }
    }

    private void sendSMSToAllNumbers(Sms sms) {
        for (String number : allowedNumbers) {
            sms.phoneNumber = number;
            sendSMS(sms);
        }
    }

    void sendSMS(Sms sms) {
        SmsManager smsManager = SmsManager.getDefault();
        sms.text = stripAccents(sms.text);

        try {
            if (L.isEnabled(L.SMS))
                log.debug("Sending SMS to " + sms.phoneNumber + ": " + sms.text);
            if (sms.text.getBytes().length <= 140)
                smsManager.sendTextMessage(sms.phoneNumber, null, sms.text, null, null);
            else {
                ArrayList<String> parts = smsManager.divideMessage(sms.text);
                smsManager.sendMultipartTextMessage(sms.phoneNumber, null, parts,
                        null, null);
            }

            messages.add(sms);
        } catch (IllegalArgumentException e) {
            Notification notification = new Notification(Notification.INVALID_PHONE_NUMBER, MainApp.gs(R.string.smscommunicator_invalidphonennumber), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        } catch (java.lang.SecurityException e) {
            Notification notification = new Notification(Notification.MISSING_SMS_PERMISSION, MainApp.gs(R.string.smscommunicator_missingsmspermission), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        }
        MainApp.bus().post(new EventSmsCommunicatorUpdateGui());
    }

    private String generatePasscode() {
        int startChar1 = 'A'; // on iphone 1st char is uppercase :)
        String passCode = Character.toString((char) (startChar1 + Math.random() * ('z' - 'a' + 1)));
        int startChar2 = Math.random() > 0.5 ? 'a' : 'A';
        passCode += Character.toString((char) (startChar2 + Math.random() * ('z' - 'a' + 1)));
        int startChar3 = Math.random() > 0.5 ? 'a' : 'A';
        passCode += Character.toString((char) (startChar3 + Math.random() * ('z' - 'a' + 1)));
        passCode.replace('l', 'k').replace('I', 'J');
        return passCode;
    }

    private static String stripAccents(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }
}
