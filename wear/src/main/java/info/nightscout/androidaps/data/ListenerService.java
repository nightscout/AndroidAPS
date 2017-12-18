package info.nightscout.androidaps.data;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.actions.CPPActivity;
import info.nightscout.androidaps.interaction.utils.SafeParse;

/**
 * Created by emmablack on 12/26/14.
 */
public class ListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
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


    public static final int BOLUS_PROGRESS_NOTIF_ID = 001;
    public static final int CONFIRM_NOTIF_ID = 002;

    private static final String ACTION_RESEND = "com.dexdrip.stephenblack.nightwatch.RESEND_DATA";
    private static final String ACTION_CANCELBOLUS = "com.dexdrip.stephenblack.nightwatch.CANCELBOLUS";
    private static final String ACTION_CONFIRMATION = "com.dexdrip.stephenblack.nightwatch.CONFIRMACTION";
    private static final String ACTION_INITIATE_ACTION = "com.dexdrip.stephenblack.nightwatch.INITIATE_ACTION";


    private static final String ACTION_RESEND_BULK = "com.dexdrip.stephenblack.nightwatch.RESEND_BULK_DATA";
    GoogleApiClient googleApiClient;
    private long lastRequest = 0;
    private DismissThread confirmThread;
    private DismissThread bolusprogressThread;


    public class DataRequester extends AsyncTask<Void, Void, Void> {
        Context mContext;

        DataRequester(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (googleApiClient.isConnected()) {
                if (System.currentTimeMillis() - lastRequest > 20 * 1000) { // enforce 20-second debounce period
                    lastRequest = System.currentTimeMillis();

                    NodeApi.GetConnectedNodesResult nodes =
                            Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                    for (Node node : nodes.getNodes()) {
                        Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_RESEND_PATH, null);
                    }
                }
            } else {
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
                if (googleApiClient.isConnected()) {
                    if (System.currentTimeMillis() - lastRequest > 20 * 1000) { // enforce 20-second debounce period
                        lastRequest = System.currentTimeMillis();

                        NodeApi.GetConnectedNodesResult nodes =
                                Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                        for (Node node : nodes.getNodes()) {
                            Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_RESEND_PATH, null);
                        }
                    }
                }
            }
            return null;
        }
    }

    public class BolusCancelTask extends AsyncTask<Void, Void, Void> {
        Context mContext;

        BolusCancelTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
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
        new DataRequester(this).execute();
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

    public void googleApiConnect() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();
        Wearable.MessageApi.addListener(googleApiClient, this);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

        for (DataEvent event : dataEvents) {

            if (event.getType() == DataEvent.TYPE_CHANGED) {


                String path = event.getDataItem().getUri().getPath();
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

                }else if (path.equals(NEW_STATUS_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("status", dataMap.toBundle());
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                } else if (path.equals(BASAL_DATA_PATH)){
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("basals", dataMap.toBundle());
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
                } else {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("data", dataMap.toBundle());
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                }
            }
        }
    }

    private void showBolusProgress(int progresspercent, String progresstatus) {
        Intent cancelIntent = new Intent(this, ListenerService.class);
        cancelIntent.setAction(ACTION_CANCELBOLUS);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, 0);;

        long[] vibratePattern;
        boolean vibreate = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean("vibrateOnBolus", true);
        if(vibreate){
            vibratePattern = new long[]{0, 50, 1000};
        } else {
            vibratePattern = new long[]{0, 1, 1000};
        }

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_icon)
                        .setContentTitle("Bolus Progress")
                        .setContentText(progresspercent + "% - " + progresstatus)
                        .setContentIntent(cancelPendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(vibratePattern)
                        .addAction(R.drawable.ic_cancel, "CANCEL BOLUS", cancelPendingIntent);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        if(confirmThread != null){
            confirmThread.invalidate();
        }
        notificationManager.notify(BOLUS_PROGRESS_NOTIF_ID, notificationBuilder.build());
        notificationManager.cancel(CONFIRM_NOTIF_ID); // multiple watch setup


        if (progresspercent == 100){
            scheduleDismissBolusprogress(5);
        }
    }

    private void showConfirmationDialog(String title, String message, String actionstring) {

        if(confirmThread != null){
            confirmThread.invalidate();
        }

        Intent actionIntent = new Intent(this, ListenerService.class);
        actionIntent.setAction(ACTION_CONFIRMATION);
        actionIntent.putExtra("actionstring", actionstring);
        PendingIntent actionPendingIntent = PendingIntent.getService(this, 0, actionIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_UPDATE_CURRENT);;

        long[] vibratePattern = new long[]{0, 100, 50, 100, 50};

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_icon)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setContentIntent(actionPendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(vibratePattern)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .extend(new NotificationCompat.WearableExtender())
                        .addAction(R.drawable.ic_confirm, title, actionPendingIntent);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(CONFIRM_NOTIF_ID, notificationBuilder.build());

        // keep the confirmation dialog open for one minute.
        scheduleDismissConfirm(60);

    }

    private void scheduleDismissConfirm(final int seconds) {
        if(confirmThread != null){
            confirmThread.invalidate();
        }
        confirmThread = new DismissThread(CONFIRM_NOTIF_ID, seconds);
        confirmThread.start();
    }

    private void scheduleDismissBolusprogress(final int seconds) {
        if(confirmThread != null){
            confirmThread.invalidate();
        }
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

    @Override
    public void onConnected(Bundle bundle) {
        requestData();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        if (googleApiClient != null) {
            Wearable.MessageApi.removeListener(googleApiClient, this);
        }
    }
}
