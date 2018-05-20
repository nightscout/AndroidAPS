package info.nightscout.androidaps.plugins.PumpMedtronic.service;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.gxwtech.roundtrip2.RT2Const;
import com.gxwtech.roundtrip2.RoundtripService.Tasks.ServiceTask;
import com.gxwtech.roundtrip2.ServiceData.ServiceNotification;
import com.gxwtech.roundtrip2.ServiceData.ServiceResult;
import com.gxwtech.roundtrip2.ServiceData.ServiceTransport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.plugins.PumpCommon.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkService;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.PumpCommon.utils.ByteUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.MedtronicPumpPlugin;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.data.Page;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicConst;
import info.nightscout.utils.SP;

/**
 * RileyLinkMedtronicService is intended to stay running when the gui-app is closed.
 */
public class RileyLinkMedtronicService extends RileyLinkService {

    private static final Logger LOG = LoggerFactory.getLogger(RileyLinkMedtronicService.class);

    private static RileyLinkMedtronicService instance;

    private IBinder mBinder = new LocalBinder();

    // saved settings
    //private String pumpIDString;
    //private byte[] pumpIDBytes;

    // cache of most recently received set of pump history pages. Probably shouldn't be here.
    //ArrayList<Page> mHistoryPages;
    //PumpHistoryManager pumpHistoryManager;

    public MedtronicCommunicationManager medtronicCommunicationManager;
    private static ServiceTask currentTask = null;

    MedtronicPumpStatus pumpStatus = (MedtronicPumpStatus) MedtronicPumpPlugin.getPlugin().getPumpStatusData();


    public RileyLinkMedtronicService() {
        super();
        instance = this;
        LOG.debug("RileyLinkMedtronicService newly constructed");

        pumpStatus = (MedtronicPumpStatus) MedtronicPumpPlugin.getPlugin().getPumpStatusData();
    }


    public static RileyLinkMedtronicService getInstance() {
        return instance;
    }


    public static MedtronicCommunicationManager getCommunicationManager() {
        return instance.medtronicCommunicationManager;
    }


    public void addPumpSpecificIntents(IntentFilter intentFilter) {
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_fetchHistory);
        intentFilter.addAction(RT2Const.IPC.MSG_PUMP_fetchSavedHistory);
    }


    public void handlePumpSpecificIntents(Intent intent) {
        String action = intent.getAction();

        if (action.equals(RT2Const.IPC.MSG_PUMP_fetchHistory)) {

//            mHistoryPages = medtronicCommunicationManager.getAllHistoryPages();
//            final boolean savePages = true;
//            if (savePages) {
//                for (int i = 0; i < mHistoryPages.size(); i++) {
//                    String filename = "PumpHistoryPage-" + i;
//                    LOG.warn("Saving history page to file " + filename);
//                    FileOutputStream outputStream;
//                    try {
//                        outputStream = openFileOutput(filename, 0);
//                        byte[] rawData = mHistoryPages.get(i).getRawData();
//                        if (rawData != null) {
//                            outputStream.write(rawData);
//                        }
//                        outputStream.close();
//                    } catch (FileNotFoundException fnf) {
//                        fnf.printStackTrace();
//                    } catch (IOException ioe) {
//                        ioe.printStackTrace();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//
//                }
//            }
//
//            Message msg = Message.obtain(null, RT2Const.IPC.MSG_IPC, 0, 0);
//            // Create a bundle with the data
//            Bundle bundle = new Bundle();
//            bundle.putString(RT2Const.IPC.messageKey, RT2Const.IPC.MSG_PUMP_history);
//            ArrayList<Bundle> packedPages = new ArrayList<>();
//            for (Page page : mHistoryPages) {
//                packedPages.add(page.pack());
//            }
//            bundle.putParcelableArrayList(RT2Const.IPC.MSG_PUMP_history_key, packedPages);
//
//            // save it to SQL.
//            pumpHistoryManager.clearDatabase();
//            pumpHistoryManager.initFromPages(bundle);
//            // write html page to documents folder
//            pumpHistoryManager.writeHtmlPage();
//
//            // Set payload
//            msg.setData(bundle);
//            //rileyLinkIPCConnection.sendMessage(msg, null/*broadcast*/);
//            LOG.debug("sendMessage: sent Full history report");
        } else if (RT2Const.IPC.MSG_PUMP_fetchSavedHistory.equals(action)) {
            LOG.info("Fetching saved history");
//            FileInputStream inputStream;
//            ArrayList<Page> storedHistoryPages = new ArrayList<>();
//            for (int i = 0; i < 16; i++) {
//
//                String filename = "PumpHistoryPage-" + i;
//                try {
//                    inputStream = openFileInput(filename);
//                    byte[] buffer = new byte[1024];
//                    int numRead = inputStream.read(buffer, 0, 1024);
//                    if (numRead == 1024) {
//                        Page p = new Page();
//                        //p.parseFrom(buffer, PumpModel.MM522);
//                        // FIXME
//                        p.parseFrom(buffer, PumpModel.MM522);
//                        storedHistoryPages.add(p);
//                    } else {
//                        LOG.error(filename + " error: short file");
//                    }
//                } catch (FileNotFoundException fnf) {
//                    LOG.error("Failed to open " + filename + " for reading.");
//                } catch (IOException e) {
//                    LOG.error("Failed to read " + filename);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//            mHistoryPages = storedHistoryPages;
//            if (storedHistoryPages.isEmpty()) {
//                LOG.error("No stored history pages loaded");
//            } else {
//                Message msg = Message.obtain(null, RT2Const.IPC.MSG_IPC, 0, 0);
//                // Create a bundle with the data
//                Bundle bundle = new Bundle();
//                bundle.putString(RT2Const.IPC.messageKey, RT2Const.IPC.MSG_PUMP_history);
//                ArrayList<Bundle> packedPages = new ArrayList<>();
//                for (Page page : mHistoryPages) {
//                    packedPages.add(page.pack());
//                }
//                bundle.putParcelableArrayList(RT2Const.IPC.MSG_PUMP_history_key, packedPages);
//
//                // save it to SQL.
//                pumpHistoryManager.clearDatabase();
//                pumpHistoryManager.initFromPages(bundle);
//                // write html page to documents folder
//                pumpHistoryManager.writeHtmlPage();
//
//                // Set payload
//                msg.setData(bundle);
//                //rileyLinkIPCConnection.sendMessage(msg, null/*broadcast*/);
//
//            }
        }
    }

    @Override
    public void loadPumpCommunicationManager() {

    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        LOG.warn("onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    /**
     * If you have customized RileyLinkServiceData you need to override this
     */
    public void initRileyLinkServiceData() {
        rileyLinkServiceData = new RileyLinkServiceData(RileyLinkTargetDevice.MedtronicPump);

        setPumpIDString(SP.getString(MedtronicConst.Prefs.PumpSerial, "000000"));

        // init rileyLinkCommunicationManager
        pumpCommunicationManager = new MedtronicCommunicationManager(context, rfspy, false, rileyLinkServiceData.pumpIDBytes);
        medtronicCommunicationManager = (MedtronicCommunicationManager) pumpCommunicationManager;


        // FIXME remove
        //pumpHistoryManager = new PumpHistoryManager(getApplicationContext());

    }




    /* private functions */


    private void setPumpIDString(String pumpID) {
        if (pumpID.length() != 6) {
            LOG.error("setPumpIDString: invalid pump id string: " + pumpID);
            return;
        }

        byte[] pumpIDBytes = ByteUtil.fromHexString(pumpID);


        //SP.putString(MedtronicConst.Prefs.PumpSerial, pumpIDString);

        if (pumpIDBytes == null) {
            LOG.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpIDBytes.length != 3) {
            LOG.error("Invalid pump ID? " + ByteUtil.shortHexString(pumpIDBytes));

            rileyLinkServiceData.setPumpID("000000", new byte[]{0, 0, 0});

        } else if (pumpID.equals("000000")) {
            LOG.error("Using pump ID " + pumpID);

            rileyLinkServiceData.setPumpID(pumpID, new byte[]{0, 0, 0});

        } else {
            LOG.info("Using pump ID " + pumpID);

            rileyLinkServiceData.setPumpID(pumpID, pumpIDBytes);
        }

        //LOG.info("setPumpIDString: saved pumpID " + idString);
    }


    private void reportPumpFound() {
        //rileyLinkIPCConnection.sendMessage(RT2Const.IPC.MSG_PUMP_pumpFound);
    }


//    public void setCurrentTask(ServiceTask task) {
//        if (currentTask == null) {
//            currentTask = task;
//        } else {
//            LOG.error("setCurrentTask: Cannot replace current task");
//        }
//    }
//
//
//    public void finishCurrentTask(ServiceTask task) {
//        if (task != currentTask) {
//            LOG.error("finishCurrentTask: task does not match");
//        }
//        // hack to force deep copy of transport contents
//        ServiceTransport transport = task.getServiceTransport().clone();
//
//        if (transport.hasServiceResult()) {
//            sendServiceTransportResponse(transport, transport.getServiceResult());
//        }
//        currentTask = null;
//    }


    public void handleIncomingServiceTransport(Intent intent) {

        Bundle bundle = intent.getBundleExtra(RT2Const.IPC.bundleKey);

        ServiceTransport serviceTransport = new ServiceTransport(bundle);

        if (serviceTransport.getServiceCommand().isPumpCommand()) {
            switch (serviceTransport.getOriginalCommandName()) {
                case "ReadPumpClock":
                    //ServiceTaskExecutor.startTask(new ReadPumpClockTask(serviceTransport));
                    break;
                case "FetchPumpHistory":
                    //ServiceTaskExecutor.startTask(new FetchPumpHistoryTask(serviceTransport));
                    break;
                case "RetrieveHistoryPage":
                    //ServiceTask task = new RetrieveHistoryPageTask(serviceTransport);
                    //ServiceTaskExecutor.startTask(task);
                    break;
                case "ReadISFProfile":
                    //ServiceTaskExecutor.startTask(new ReadISFProfileTask(serviceTransport));
                /*
                ISFTable table = pumpCommunicationManager.getPumpISFProfile();
                ServiceResult result = new ServiceResult();
                if (table.isValid()) {
                    // convert from ISFTable to ISFProfile
                    Bundle map = result.getMap();
                    map.putIntArray("times", table.getTimes());
                    map.putFloatArray("rates", table.getRates());
                    map.putString("ValidDate", TimeFormat.standardFormatter().print(table.getValidDate()));
                    result.setMap(map);
                    result.setResultOK();
                }
                sendServiceTransportResponse(serviceTransport,result);
                */
                    break;
                case "ReadBolusWizardCarbProfile":
                    //ServiceTaskExecutor.startTask(new ReadBolusWizardCarbProfileTask());
                    break;
                case "UpdatePumpStatus":
                    //ServiceTaskExecutor.startTask(new UpdatePumpStatusTask());
                    break;
                case "WakeAndTune":
                    //sServiceTaskExecutor.startTask(new WakeAndTuneTask());
                default:
                    LOG.error("Failed to handle pump command: " + serviceTransport.getOriginalCommandName());
                    break;
            }
        } else {
            switch (serviceTransport.getOriginalCommandName()) {
                case "SetPumpID":
                    // This one is a command to RileyLinkMedtronicService, not to the MedtronicCommunicationManager
                    String pumpID = serviceTransport.getServiceCommand().getMap().getString("pumpID", "");
                    ServiceResult result = new ServiceResult();
                    if ((pumpID != null) && (pumpID.length() == 6)) {
                        setPumpIDString(pumpID);
                        result.setResultOK();
                    } else {
                        LOG.error("handleIncomingServiceTransport: SetPumpID bundle missing 'pumpID' value");
                        result.setResultError(-1, "Invalid parameter (missing pumpID)");
                    }
                    sendServiceTransportResponse(serviceTransport, result);
                    break;
                case "UseThisRileylink":
                    // If we are not connected, connect using the given address.
                    // If we are connected and the addresses differ, disconnect, connect to new.
                    // If we are connected and the addresses are the same, ignore.
                    String deviceAddress = serviceTransport.getServiceCommand().getMap().getString("rlAddress", "");
                    if ("".equals(deviceAddress)) {
                        LOG.error("handleIPCMessage: null RL address passed");
                    } else {
                        reconfigureRileylink(deviceAddress);
                    }
                    break;
                default:
                    LOG.error("handleIncomingServiceTransport: Failed to handle service command '" + serviceTransport.getOriginalCommandName() + "'");
                    break;
            }
        }
    }


    public void announceProgress(int progressPercent) {
        if (currentTask != null) {
            ServiceNotification note = new ServiceNotification(RT2Const.IPC.MSG_note_TaskProgress);
            note.getMap().putInt("progress", progressPercent);
            note.getMap().putString("task", currentTask.getServiceTransport().getOriginalCommandName());
            Integer senderHashcode = currentTask.getServiceTransport().getSenderHashcode();
            //rileyLinkIPCConnection.sendNotification(note, senderHashcode);
        } else {
            LOG.error("announceProgress: No current task");
        }
    }


    public void saveHistoryPage(int pagenumber, Page page) {
        if ((page == null) || (page.getRawData() == null)) {
            return;
        }
        String filename = "history-" + pagenumber;
        FileOutputStream os;
        try {
            os = openFileOutput(filename, Context.MODE_PRIVATE);
            os.write(page.getRawData());
            os.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // PumpInterface


    public boolean isSuspended() {
        return false;
    }

    public boolean isBusy() {
        return false;
    }


    public void getPumpStatus() {

    }

    public PumpEnactResult setNewBasalProfile(Profile profile) {
        return null;
    }

    public boolean isThisProfileSet(Profile profile) {
        return false;
    }


    public double getBaseBasalRate() {
        return 0;
    }

    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return null;
    }

    public void stopBolusDelivering() {

    }

    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return null;
    }

    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return null;
    }

    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        return null;
    }

    public PumpEnactResult cancelExtendedBolus() {
        return null;
    }


    public PumpEnactResult loadTDDs() {
        return null;
    }


    public class LocalBinder extends Binder {
        public RileyLinkMedtronicService getServiceInstance() {
            return RileyLinkMedtronicService.this;
        }
    }


}

