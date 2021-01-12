package info.nightscout.androidaps.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.interaction.actions.AcceptActivity;
import info.nightscout.androidaps.interaction.actions.CPPActivity;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.SafeParse;
import info.nightscout.androidaps.interaction.utils.WearUtil;


/**
 * Created by emmablack on 12/26/14.
 */
public class ListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ChannelApi.ChannelListener {

    private static final String WEARABLE_DATA_PATH = "/nightscout_watch_data";
    private static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String WEARABLE_CANCELBOLUS_PATH = "/nightscout_watch_cancel_bolus";
    public static final String WEARABLE_CONFIRM_ACTIONSTRING_PATH = "/nightscout_watch_confirmactionstring";
    public static final String WEARABLE_INITIATE_ACTIONSTRING_PATH = "/nightscout_watch_initiateactionstring";

    private static final String OPEN_SETTINGS = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    private static final String NEW_PREFERENCES_PATH = "/sendpreferencestowear";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";
    public static final String BOLUS_PROGRESS_PATH = "/nightscout_watch_bolusprogress";
    public static final String ACTION_CONFIRMATION_REQUEST_PATH = "/nightscout_watch_actionconfirmationrequest";
    public static final String NEW_CHANGECONFIRMATIONREQUEST_PATH = "/nightscout_watch_changeconfirmationrequest";
    public static final String ACTION_CANCELNOTIFICATION_REQUEST_PATH = "/nightscout_watch_cancelnotificationrequest";


    public static final int BOLUS_PROGRESS_NOTIF_ID = 001;
    public static final int CONFIRM_NOTIF_ID = 002;
    public static final int CHANGE_NOTIF_ID = 556677;

    private static final String ACTION_RESEND = "com.dexdrip.stephenblack.nightwatch.RESEND_DATA";
    private static final String ACTION_CANCELBOLUS = "com.dexdrip.stephenblack.nightwatch.CANCELBOLUS";
    private static final String ACTION_CONFIRMATION = "com.dexdrip.stephenblack.nightwatch.CONFIRMACTION";
    private static final String ACTION_CONFIRMCHANGE = "com.dexdrip.stephenblack.nightwatch.CONFIRMCHANGE";
    private static final String ACTION_INITIATE_ACTION = "com.dexdrip.stephenblack.nightwatch.INITIATE_ACTION";


    private static final String ACTION_RESEND_BULK = "com.dexdrip.stephenblack.nightwatch.RESEND_BULK_DATA";
    private static final String AAPS_NOTIFY_CHANNEL_ID_OPENLOOP = "AndroidAPS-Openloop";
    private static final String AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS = "AndroidAPS-bolus-progress";


    GoogleApiClient googleApiClient;
    private long lastRequest = 0;
    private DismissThread bolusprogressThread;
    private static final String TAG = "ListenerService";

    private DataRequester mDataRequester = null;
    private static final int GET_CAPABILITIES_TIMEOUT_MS = 5000;

    // Phone
    private static final String CAPABILITY_PHONE_APP = "phone_app_sync_bgs";
    private static final String MESSAGE_PATH_PHONE = "/phone_message_path";
    // Wear
    private static final String CAPABILITY_WEAR_APP = "wear_app_sync_bgs";
    private static final String MESSAGE_PATH_WEAR = "/wear_message_path";
    private final String mPhoneNodeId = null;
    private String localnode = null;
    private final String logPrefix = ""; // "WR: "

    public class DataRequester extends AsyncTask<Void, Void, Void> {
        Context mContext;
        String path;
        byte[] payload;


        DataRequester(Context context, String thispath, byte[] thispayload) {
            path = thispath;
            payload = thispayload;
            // Log.d(TAG, logPrefix + "DataRequester DataRequester: " + thispath + " lastRequest:" + lastRequest);
        }



        @Override
        protected Void doInBackground(Void... params) {
            // Log.d(TAG, logPrefix + "DataRequester: doInBack: " + params);

            try {

                forceGoogleApiConnect();
                DataMap datamap;

                if (isCancelled()) {
                    Log.d(TAG, "doInBackground CANCELLED programmatically");
                    return null;
                }

                if (googleApiClient != null) {
                    if (!googleApiClient.isConnected())
                        googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
                }

                // this code might not be needed in this way, but we need to see that later
                if ((googleApiClient != null) && (googleApiClient.isConnected())) {
                    if ((System.currentTimeMillis() - lastRequest > 20 * 1000)) {

                        // enforce 20-second debounce period
                        lastRequest = System.currentTimeMillis();

                        // NodeApi.GetConnectedNodesResult nodes =
                        // Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        if (localnode == null || (localnode != null && localnode.isEmpty()))
                            setLocalNodeName();

                        CapabilityInfo capabilityInfo = getCapabilities();

                        int count = 0;
                        Node phoneNode = null;

                        if (capabilityInfo != null) {
                            phoneNode = updatePhoneSyncBgsCapability(capabilityInfo);
                            count = capabilityInfo.getNodes().size();
                        }

                        Log.d(TAG, "doInBackground connected.  CapabilityApi.GetCapabilityResult mPhoneNodeID="
                            + (phoneNode != null ? phoneNode.getId() : "") + " count=" + count + " localnode="
                            + localnode);// KS

                        if (count > 0) {

                            for (Node node : capabilityInfo.getNodes()) {

                                // Log.d(TAG, "doInBackground path: " + path);

                                switch (path) {
                                // simple send as is payloads

                                    case WEARABLE_RESEND_PATH:
                                        Wearable.MessageApi.sendMessage(googleApiClient, node.getId(),
                                            WEARABLE_RESEND_PATH, null);
                                        break;
                                    case WEARABLE_DATA_PATH:
                                    case WEARABLE_CANCELBOLUS_PATH:
                                    case WEARABLE_CONFIRM_ACTIONSTRING_PATH:
                                    case WEARABLE_INITIATE_ACTIONSTRING_PATH:
                                    case OPEN_SETTINGS:
                                    case NEW_STATUS_PATH:
                                    case NEW_PREFERENCES_PATH:
                                    case BASAL_DATA_PATH:
                                    case BOLUS_PROGRESS_PATH:
                                    case ACTION_CONFIRMATION_REQUEST_PATH:
                                    case NEW_CHANGECONFIRMATIONREQUEST_PATH:
                                    case ACTION_CANCELNOTIFICATION_REQUEST_PATH: {
                                        Log.w(TAG, logPrefix + "Unhandled path");
                                        // sendMessagePayload(node, path, path, payload);
                                    }

                                    default:// SYNC_ALL_DATA
                                        // this fall through is messy and non-deterministic for new paths

                                }
                            }
                        } else {

                            Log.d(TAG, logPrefix + "doInBackground connected but getConnectedNodes returns 0.");

                        }
                    } else {
                        // no resend
                        Log.d(TAG, logPrefix + "Inside the timeout, will not be executed");

                    }
                } else {
                    Log.d(TAG, logPrefix + "Not connected for sending: api "
                        + ((googleApiClient == null) ? "is NULL!" : "not null"));
                    if (googleApiClient != null) {
                        googleApiClient.connect();
                    } else {
                        googleApiConnect();
                    }
                }

            } catch (Exception ex) {
                Log.e(TAG, logPrefix + "Error executing DataRequester in background. Exception: " + ex.getMessage());
            }

            return null;
        }
    }


    public CapabilityInfo getCapabilities() {

        CapabilityApi.GetCapabilityResult capabilityResult = Wearable.CapabilityApi.getCapability(googleApiClient,
            CAPABILITY_PHONE_APP, CapabilityApi.FILTER_REACHABLE).await(GET_CAPABILITIES_TIMEOUT_MS,
            TimeUnit.MILLISECONDS);

        if (!capabilityResult.getStatus().isSuccess()) {
            Log.e(TAG, logPrefix + "doInBackground Failed to get capabilities, status: "
                + capabilityResult.getStatus().getStatusMessage());
            return null;
        }

        return capabilityResult.getCapability();

    }

    public class BolusCancelTask extends AsyncTask<Void, Void, Void> {
        Context mContext;

        BolusCancelTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Log.d(TAG, logPrefix + "BolusCancelTask: doInBack: " + params);

            if (googleApiClient.isConnected()) {
                NodeApi.GetConnectedNodesResult nodes =
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_CANCELBOLUS_PATH, null);
                }

            } else {
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
                if (googleApiClient.isConnected()) {
                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_CANCELBOLUS_PATH, null);
                    }

                }
            }
            return null;
        }
    }

    public class MessageActionTask extends AsyncTask<Void, Void, Void> {
        Context mContext;
        String mActionstring;
        String mMessagePath;

        MessageActionTask(Context context, String messagePath, String actionstring) {
            mContext = context;
            mActionstring = actionstring;
            mMessagePath = messagePath;
        }

        @Override
        protected Void doInBackground(Void... params) {

            forceGoogleApiConnect();

            if (googleApiClient.isConnected()) {
                NodeApi.GetConnectedNodesResult nodes =
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), mMessagePath, mActionstring.getBytes());
                }

            } else {
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
                if (googleApiClient.isConnected()) {
                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), mMessagePath, mActionstring.getBytes());
                    }
                }
            }
            return null;
        }
    }

    public void requestData() {
        sendData(WEARABLE_RESEND_PATH, null);
    }

    public void cancelBolus() {
        new BolusCancelTask(this).execute();
    }

    private void sendConfirmActionstring(String actionstring) {
        new MessageActionTask(this, WEARABLE_CONFIRM_ACTIONSTRING_PATH, actionstring).execute();
    }

    private void sendInitiateActionstring(String actionstring) {
        new MessageActionTask(this, WEARABLE_INITIATE_ACTIONSTRING_PATH, actionstring).execute();
    }


    private Node updatePhoneSyncBgsCapability(CapabilityInfo capabilityInfo) {
        // Log.d(TAG, "CapabilityInfo: " + capabilityInfo);

        Set<Node> connectedNodes = capabilityInfo.getNodes();
        return pickBestNode(connectedNodes);
        // mPhoneNodeId = pickBestNodeId(connectedNodes);
    }


    private Node pickBestNode(Set<Node> nodes) {
        Node bestNode = null;
        // Find a nearby node or pick one arbitrarily
        for (Node node : nodes) {
            if (node.isNearby()) {
                return node;
            }
            bestNode = node;
        }
        return bestNode;
    }


    private synchronized void sendData(String path, byte[] payload) {
        // Log.d(TAG, "WR: sendData: path: " + path + ", payload=" + payload);

        if (path == null)
            return;
        if (mDataRequester != null) {
            // Log.d(TAG, logPrefix + "sendData DataRequester != null lastRequest:" +
            // WearUtil.dateTimeText(lastRequest));
            if (mDataRequester.getStatus() != AsyncTask.Status.FINISHED) {
                // Log.d(TAG, logPrefix + "sendData Should be canceled?  Let run 'til finished.");
                // mDataRequester.cancel(true);
            }
        }

        Log.d(TAG, logPrefix + "sendData: execute lastRequest:" + WearUtil.dateTimeText(lastRequest));
        mDataRequester = (DataRequester)new DataRequester(this, path, payload).execute();
        // executeTask(mDataRequester);

        // if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        // Log.d(TAG, "sendData SDK < M call execute lastRequest:" + WearUtil.dateTimeText(lastRequest));
        // mDataRequester = (DataRequester) new DataRequester(this, path, payload).execute();
        // } else {
        // Log.d(TAG, "sendData SDK >= M call executeOnExecutor lastRequest:" + WearUtil.dateTimeText(lastRequest));
        // // TODO xdrip executor
        // mDataRequester = (DataRequester) new DataRequester(this, path, payload).executeOnExecutor(xdrip.executor);
        // }
    }


    private void googleApiConnect() {
        if (googleApiClient != null) {
            // Remove old listener(s)
            try {
                Wearable.ChannelApi.removeListener(googleApiClient, this);
            } catch (Exception e) {
                //
            }
            try {
                Wearable.MessageApi.removeListener(googleApiClient, this);
            } catch (Exception e) {
                //
            }
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        Wearable.MessageApi.addListener(googleApiClient, this);
    }



    private void forceGoogleApiConnect() {
        if (googleApiClient == null || (!googleApiClient.isConnected() && !googleApiClient.isConnecting())) {
            try {
                Log.d(TAG, "forceGoogleApiConnect: forcing google api reconnection");
                googleApiConnect();
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                //
            }
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Log.d(TAG, logPrefix + "onStartCommand: Intent: " + intent);

        if (intent != null && ACTION_RESEND.equals(intent.getAction())) {
            googleApiConnect();
            requestData();
        } else if(intent != null && ACTION_CANCELBOLUS.equals(intent.getAction())){
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(BOLUS_PROGRESS_NOTIF_ID);

            //send cancel-request to phone.
            cancelBolus();


        } else if(intent != null && ACTION_CONFIRMATION.equals(intent.getAction())){
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(CONFIRM_NOTIF_ID);

            String actionstring = intent.getStringExtra("actionstring");
            sendConfirmActionstring(actionstring);

        } else if(intent != null && ACTION_CONFIRMCHANGE.equals(intent.getAction())){
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(CHANGE_NOTIF_ID);

            String actionstring = intent.getStringExtra("actionstring");
            sendConfirmActionstring(actionstring);

         } else if(intent != null && ACTION_INITIATE_ACTION.equals(intent.getAction())){
            googleApiConnect();

            String actionstring = intent.getStringExtra("actionstring");
            sendInitiateActionstring(actionstring);

        }

        return START_STICKY;
    }


    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {

        DataMap dataMap;
        // Log.d(TAG, logPrefix + "onDataChanged: DataEvents=" + dataEvents);

        for (DataEvent event : dataEvents) {

            if (event.getType() == DataEvent.TYPE_CHANGED) {

                String path = event.getDataItem().getUri().getPath();

                //Log.d(TAG, "WR: onDataChanged: Path: " + path + ", EventDataItem=" + event.getDataItem());

                if (path.equals(OPEN_SETTINGS)) {
                    Intent intent = new Intent(this, AAPSPreferences.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else if (path.equals(BOLUS_PROGRESS_PATH)) {
                    int progress = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getInt("progresspercent", 0);
                    String status = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("progressstatus", "");
                    showBolusProgress(progress, status);
                } else if (path.equals(ACTION_CONFIRMATION_REQUEST_PATH)) {
                    String title = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("title");
                    String message = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("message");
                    String actionstring = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("actionstring");

                    if("opencpp".equals(title) && actionstring.startsWith("opencpp")){
                        String[] act = actionstring.split("\\s+");
                        Intent intent = new Intent(this, CPPActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        //TODO adrian: parse actionstring and add parameters
                        Bundle params = new Bundle();
                        params.putInt("percentage", SafeParse.stringToInt(act[1]));
                        params.putInt("timeshift", SafeParse.stringToInt(act[2]));
                        intent.putExtras(params);
                        startActivity(intent);
                    } else {
                        showConfirmationDialog(title, message, actionstring);
                    }

                } else if (path.equals(NEW_STATUS_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("status", dataMap.toBundle());
                    Persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                } else if (path.equals(BASAL_DATA_PATH)){
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("basals", dataMap.toBundle());
                    Persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                } else if (path.equals(NEW_PREFERENCES_PATH)){
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    if(dataMap.containsKey("wearcontrol")) {
                        boolean wearcontrol = dataMap.getBoolean("wearcontrol", false);
                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean("wearcontrol", wearcontrol);
                        editor.commit();
                    }
                } else if (path.equals(NEW_CHANGECONFIRMATIONREQUEST_PATH)) {
                    String title = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("title");
                    String message = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("message");
                    String actionstring = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("actionstring");
                    notifyChangeRequest(title, message, actionstring);
                } else if (path.equals(ACTION_CANCELNOTIFICATION_REQUEST_PATH)) {
                    String actionstring = DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("actionstring");
                    cancelNotificationRequest(actionstring);
                } else {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("data", dataMap.toBundle());
                    Persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                }
            }
        }
    }

    private void notifyChangeRequest(String title, String message, String actionstring) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                CharSequence name = "AAPS Open Loop";
                String description = "Open Loop request notiffication";//getString(R.string.channel_description);
                NotificationChannel channel = new NotificationChannel(AAPS_NOTIFY_CHANNEL_ID_OPENLOOP, name, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(description);
                channel.enableVibration(true);

                // Register the channel with the system; you can't change the importance
                // or other notification behaviors after this
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.createNotificationChannel(channel);
            }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, AAPS_NOTIFY_CHANNEL_ID_OPENLOOP);

        builder = builder.setSmallIcon(R.drawable.notif_icon)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(Notification.PRIORITY_HIGH)
                .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});

        // Creates an explicit intent for an Activity in your app
        Intent intent = new Intent(this, AcceptActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle params = new Bundle();
        params.putString("title", title);
        params.putString("message", message);
        params.putString("actionstring", actionstring);
        intent.putExtras(params);

        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder = builder.setContentIntent(resultPendingIntent);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        mNotificationManager.notify(CHANGE_NOTIF_ID, builder.build());
    }

    private void cancelNotificationRequest(String actionstring) {
        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotificationManager.cancel(CHANGE_NOTIF_ID);
    }

    private void showBolusProgress(int progresspercent, String progresstatus) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "AAPS Bolus Progress";
            String description = "Bolus progress and cancel";
            NotificationChannel channel = new NotificationChannel(AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            channel.enableVibration(true);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent cancelIntent = new Intent(this, ListenerService.class);
        cancelIntent.setAction(ACTION_CANCELBOLUS);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, 0);

        long[] vibratePattern;
        boolean vibreate = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean("vibrateOnBolus", true);
        if(vibreate){
            vibratePattern = new long[]{0, 50, 1000};
        } else {
            vibratePattern = new long[]{0, 1, 1000};
        }

        // TODO: proper channel. Does cancel work?
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS)
                        .setSmallIcon(R.drawable.ic_icon)
                        .setContentTitle("Bolus Progress")
                        .setContentText(progresspercent + "% - " + progresstatus)
                        .setSubText("press to cancel")
                        .setContentIntent(cancelPendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(vibratePattern)
                        .addAction(R.drawable.ic_cancel, "CANCEL BOLUS", cancelPendingIntent);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(BOLUS_PROGRESS_NOTIF_ID, notificationBuilder.build());
        notificationManager.cancel(CONFIRM_NOTIF_ID); // multiple watch setup


        if (progresspercent == 100){
            scheduleDismissBolusprogress(5);
        }
    }

    private void showConfirmationDialog(String title, String message, String actionstring) {

        Intent intent = new Intent(this, AcceptActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle params = new Bundle();
        params.putString("title", title);
        params.putString("message", message);
        params.putString("actionstring", actionstring);
        intent.putExtras(params);
        startActivity(intent);
    }

    private void scheduleDismissBolusprogress(final int seconds) {
        bolusprogressThread = new DismissThread(BOLUS_PROGRESS_NOTIF_ID, seconds);
        bolusprogressThread.start();
    }



    private class DismissThread extends Thread{
        private final int notificationID;
        private final int seconds;
        private boolean valid = true;

        DismissThread(int notificationID, int seconds){
            this.notificationID = notificationID;
            this.seconds = seconds;
        }

        public synchronized void invalidate(){
            valid = false;
        }

        @Override
        public void run() {
            SystemClock.sleep(seconds * 1000);
            synchronized (this) {
                if(valid) {
                    NotificationManagerCompat notificationManager =
                            NotificationManagerCompat.from(ListenerService.this);
                    notificationManager.cancel(notificationID);
                }
            }
        }
    }

    public static void requestData(Context context) {
        Intent intent = new Intent(context, ListenerService.class);
        intent.setAction(ACTION_RESEND);
        context.startService(intent);
    }

    public static void initiateAction(Context context, String actionstring) {
        Intent intent = new Intent(context, ListenerService.class);
        intent.putExtra("actionstring", actionstring);
        intent.setAction(ACTION_INITIATE_ACTION);
        context.startService(intent);
    }

    public static void confirmAction(Context context, String actionstring) {
        Intent intent = new Intent(context, ListenerService.class);
        intent.putExtra("actionstring", actionstring);

        if (actionstring.equals("changeRequest")) {
            intent.setAction(ACTION_CONFIRMCHANGE);
        } else {
            intent.setAction(ACTION_CONFIRMATION);
        }
        context.startService(intent);
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Log.d(TAG, logPrefix + "onConnected call requestData");

        CapabilityApi.CapabilityListener capabilityListener = new CapabilityApi.CapabilityListener() {

            @Override
            public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                updatePhoneSyncBgsCapability(capabilityInfo);
                Log.d(TAG, logPrefix + "onConnected onCapabilityChanged mPhoneNodeID:" + mPhoneNodeId
                    + ", Capability: " + capabilityInfo);
            }
        };

        Wearable.CapabilityApi.addCapabilityListener(googleApiClient, capabilityListener, CAPABILITY_PHONE_APP);

        Wearable.ChannelApi.addListener(googleApiClient, this);
        requestData();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }


    private void setLocalNodeName() {
        forceGoogleApiConnect();
        PendingResult<NodeApi.GetLocalNodeResult> result = Wearable.NodeApi.getLocalNode(googleApiClient);
        result.setResultCallback(new ResultCallback<NodeApi.GetLocalNodeResult>() {

            @Override
            public void onResult(NodeApi.GetLocalNodeResult getLocalNodeResult) {
                if (!getLocalNodeResult.getStatus().isSuccess()) {
                    Log.e(TAG, "ERROR: failed to getLocalNode Status="
                        + getLocalNodeResult.getStatus().getStatusMessage());
                } else {
                    Log.d(TAG, "getLocalNode Status=: " + getLocalNodeResult.getStatus().getStatusMessage());
                    Node getnode = getLocalNodeResult.getNode();
                    localnode = getnode != null ? getnode.getDisplayName() + "|" + getnode.getId() : "";
                    Log.d(TAG, "setLocalNodeName.  localnode=" + localnode);
                }
            }
        });
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }

        if (googleApiClient != null) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
            Wearable.ChannelApi.removeListener(googleApiClient, this);
        }
  }
}
