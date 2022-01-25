package info.nightscout.androidaps.data;

import android.annotation.TargetApi;
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
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.wear.tiles.TileService;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interaction.AAPSPreferences;
import info.nightscout.androidaps.interaction.actions.AcceptActivity;
import info.nightscout.androidaps.interaction.actions.CPPActivity;
import info.nightscout.androidaps.interaction.utils.Persistence;
import info.nightscout.androidaps.interaction.utils.WearUtil;
import info.nightscout.androidaps.tile.ActionsTileService;
import info.nightscout.androidaps.tile.QuickWizardTileService;
import info.nightscout.androidaps.tile.TempTargetTileService;
import info.nightscout.shared.SafeParse;

/**
 * Created by emmablack on 12/26/14.
 */
public class ListenerService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, ChannelApi.ChannelListener {

    @Inject WearUtil wearUtil;
    @Inject Persistence persistence;

    private static final String WEARABLE_RESEND_PATH = "/nightscout_watch_data_resend";
    private static final String WEARABLE_CANCELBOLUS_PATH = "/nightscout_watch_cancel_bolus";
    public static final String WEARABLE_CONFIRM_ACTIONSTRING_PATH = "/nightscout_watch_confirmactionstring";
    public static final String WEARABLE_INITIATE_ACTIONSTRING_PATH = "/nightscout_watch_initiateactionstring";

    private static final String OPEN_SETTINGS = "/openwearsettings";
    private static final String NEW_STATUS_PATH = "/sendstatustowear";
    private static final String NEW_PREFERENCES_PATH = "/sendpreferencestowear";
    private static final String QUICK_WIZARD_PATH = "/send_quick_wizard";
    public static final String BASAL_DATA_PATH = "/nightscout_watch_basal";
    public static final String BOLUS_PROGRESS_PATH = "/nightscout_watch_bolusprogress";
    public static final String ACTION_CONFIRMATION_REQUEST_PATH = "/nightscout_watch_actionconfirmationrequest";
    public static final String NEW_CHANGECONFIRMATIONREQUEST_PATH = "/nightscout_watch_changeconfirmationrequest";
    public static final String ACTION_CANCELNOTIFICATION_REQUEST_PATH = "/nightscout_watch_cancelnotificationrequest";

    public static final int BOLUS_PROGRESS_NOTIF_ID = 1;
    public static final int CONFIRM_NOTIF_ID = 2;
    public static final int CHANGE_NOTIF_ID = 556677;

    private static final String ACTION_RESEND = "com.dexdrip.stephenblack.nightwatch.RESEND_DATA";
    private static final String ACTION_CANCELBOLUS = "com.dexdrip.stephenblack.nightwatch.CANCELBOLUS";
    private static final String ACTION_CONFIRMATION = "com.dexdrip.stephenblack.nightwatch.CONFIRMACTION";
    private static final String ACTION_CONFIRMCHANGE = "com.dexdrip.stephenblack.nightwatch.CONFIRMCHANGE";
    private static final String ACTION_INITIATE_ACTION = "com.dexdrip.stephenblack.nightwatch.INITIATE_ACTION";

    private static final String AAPS_NOTIFY_CHANNEL_ID_OPENLOOP = "AndroidAPS-OpenLoop";
    private static final String AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS = "bolus progress vibration";
    private static final String AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT = "bolus progress  silent";

    GoogleApiClient googleApiClient;

    private DismissThread bolusprogressThread;
    private static final String TAG = "ListenerService";

    private final String logPrefix = ""; // "WR: "

    // Not derived from DaggerService, do injection here
    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();
    }

    public class BolusCancelTask extends AsyncTask<Void, Void, Void> {
        Context mContext;

        BolusCancelTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, logPrefix + "BolusCancelTask.doInBackground: " + params);
            if (!googleApiClient.isConnected()) {
                Log.i(TAG, "BolusCancelTask.doInBackground: not connected");
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
            }
            if (googleApiClient.isConnected()) {
                NodeApi.GetConnectedNodesResult nodes =
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_CANCELBOLUS_PATH, null);
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
            Log.i(TAG, "MessageActionTask.doInBackground: ");

            if (!googleApiClient.isConnected()) {
                Log.i(TAG, "MessageActionTask.doInBackground: not connected");
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
            }
            if (googleApiClient.isConnected()) {
                NodeApi.GetConnectedNodesResult nodes =
                        Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), mMessagePath, mActionstring.getBytes());
                }

            }
            return null;
        }
    }

    public class ResendDataTask extends AsyncTask<Void, Void, Void> {
        Context mContext;

        ResendDataTask(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.d(TAG, logPrefix + "ResendDataTask.doInBackground: " + params);

            if (!googleApiClient.isConnected()) {
                Log.i(TAG, "ResendDataTask.doInBackground: not connected");
                googleApiClient.blockingConnect(15, TimeUnit.SECONDS);
            }
            if (googleApiClient.isConnected()) {
                Log.i(TAG, "ResendDataTask.doInBackground: connected");
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await();
                for (Node node : nodes.getNodes()) {
                    Wearable.MessageApi.sendMessage(googleApiClient, node.getId(), WEARABLE_RESEND_PATH, null);
                }
            } else {
                Log.i(TAG, "ResendDataTask.doInBackground: could not connect");
            }
            return null;

        }
    }

    public void requestData() {
        new ResendDataTask(this).execute();
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Log.d(TAG, logPrefix + "onStartCommand: Intent: " + intent);

        if (intent != null && ACTION_RESEND.equals(intent.getAction())) {
            googleApiConnect();
            requestData();
        } else if (intent != null && ACTION_CANCELBOLUS.equals(intent.getAction())) {
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(BOLUS_PROGRESS_NOTIF_ID);

            //send cancel-request to phone.
            cancelBolus();


        } else if (intent != null && ACTION_CONFIRMATION.equals(intent.getAction())) {
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(CONFIRM_NOTIF_ID);

            String actionstring = intent.getStringExtra("actionstring");
            sendConfirmActionstring(actionstring);

        } else if (intent != null && ACTION_CONFIRMCHANGE.equals(intent.getAction())) {
            googleApiConnect();

            //dismiss notification
            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(ListenerService.this);
            notificationManager.cancel(CHANGE_NOTIF_ID);

            String actionstring = intent.getStringExtra("actionstring");
            sendConfirmActionstring(actionstring);

        } else if (intent != null && ACTION_INITIATE_ACTION.equals(intent.getAction())) {
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

                    if ("opencpp".equals(title) && actionstring.startsWith("opencpp")) {
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
                    persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                } else if (path.equals(BASAL_DATA_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Intent messageIntent = new Intent();
                    messageIntent.setAction(Intent.ACTION_SEND);
                    messageIntent.putExtra("basals", dataMap.toBundle());
                    persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                } else if (path.equals(NEW_PREFERENCES_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    String keyControl = getString(R.string.key_wear_control);
                    if (dataMap.containsKey(keyControl)) {
                        boolean previousWearControl = sharedPreferences.getBoolean(keyControl, false);
                        boolean wearControl = dataMap.getBoolean(keyControl, false);
                        editor.putBoolean(keyControl, wearControl);
                        editor.apply();
                        if (wearControl != previousWearControl) {
                            updateTiles();
                        }
                    }
                    String keyPercentage = getString(R.string.key_boluswizard_percentage);
                    if (dataMap.containsKey(keyPercentage)) {
                        int wpercentage = dataMap.getInt(keyPercentage, 100);
                        editor.putInt(keyPercentage, wpercentage);
                        editor.apply();
                    }
                    String keyUnits = getString(R.string.key_units_mgdl);
                    if (dataMap.containsKey(keyUnits)) {
                        boolean mgdl = dataMap.getBoolean(keyUnits, true);
                        editor.putBoolean(keyUnits, mgdl);
                        editor.apply();
                    }
                    String keyMaxCarbs = getString(R.string.key_treatmentssafety_maxcarbs);
                    if (dataMap.containsKey(keyMaxCarbs)) {
                        int maxCarbs = dataMap.getInt(keyMaxCarbs, 48);
                        editor.putInt(keyMaxCarbs, maxCarbs);
                        editor.apply();
                    }
                    String keyMaxBolus = getString(R.string.key_treatmentssafety_maxbolus);
                    if (dataMap.containsKey(keyMaxBolus)) {
                        float maxBolus = (float)dataMap.getDouble(keyMaxBolus, 3.0f);
                        editor.putFloat(keyMaxBolus, maxBolus);
                        editor.apply();
                    }

                } else if (path.equals(QUICK_WIZARD_PATH)) {
                    dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    Log.i(TAG, "onDataChanged: QUICK_WIZARD_PATH" + dataMap);
                    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                    dataMap.remove("timestamp");
                    String key = getString(R.string.key_quick_wizard_data_map);
                    String dataString = Base64.encodeToString(dataMap.toByteArray(), Base64.DEFAULT);
                    if (!dataString.equals(sharedPreferences.getString(key, ""))) {
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(key, dataString);
                        editor.apply();
                        // Todo maybe add debounce function, due to 20 seconds update limit?
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            TileService.getUpdater(this)
                                    .requestUpdate(QuickWizardTileService.class);
                        }
                        Log.i(TAG, "onDataChanged: updated QUICK_WIZARD");
                    } else {
                        Log.i(TAG, "onDataChanged: ignore update");
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
                    persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMap);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
                }
            }
        }
    }

    private void updateTiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TileService.getUpdater(this)
                    .requestUpdate(ActionsTileService.class);

            TileService.getUpdater(this)
                    .requestUpdate(TempTargetTileService.class);

            TileService.getUpdater(this)
                    .requestUpdate(QuickWizardTileService.class);
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
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

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

        long[] vibratePattern;
        boolean vibrate = PreferenceManager
                .getDefaultSharedPreferences(this).getBoolean("vibrateOnBolus", true);
        if (vibrate) {
            vibratePattern = new long[]{0, 50, 1000};
        } else {
            vibratePattern = new long[]{0, 1, 1000};
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createBolusProgressChannels();
        }

        Intent cancelIntent = new Intent(this, ListenerService.class);
        cancelIntent.setAction(ACTION_CANCELBOLUS);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, 0);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, vibrate ? AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS : AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT)
                        .setSmallIcon(R.drawable.ic_icon)
                        .setContentTitle(getString(R.string.bolus_progress))
                        .setContentText(progresspercent + "% - " + progresstatus)
                        .setSubText(getString(R.string.press_to_cancel))
                        .setContentIntent(cancelPendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVibrate(vibratePattern)
                        .addAction(R.drawable.ic_cancel, getString(R.string.cancel_bolus), cancelPendingIntent);

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);

        notificationManager.notify(BOLUS_PROGRESS_NOTIF_ID, notificationBuilder.build());
        notificationManager.cancel(CONFIRM_NOTIF_ID); // multiple watch setup


        if (progresspercent == 100) {
            scheduleDismissBolusprogress(5);
        }
    }

    @TargetApi(value = 26)
    private void createBolusProgressChannels() {
        createNotificationChannel(new long[]{0, 50, 1000}, AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS, getString(R.string.bolus_progress_channel_name), getString(R.string.bolus_progress_channel_description));
        createNotificationChannel(new long[]{0, 1, 1000}, AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT, getString(R.string.bolus_progress_silent_channel_name), getString(R.string.bolus_progress_silent_channel_description));
    }

    @TargetApi(value = 26)
    private void createNotificationChannel(long[] vibratePattern, String channelID, CharSequence name, String description) {
        NotificationChannel channel = new NotificationChannel(channelID, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.enableVibration(true);
        channel.setVibrationPattern(vibratePattern);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
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

    private class DismissThread extends Thread {
        private final int notificationID;
        private final int seconds;
        private boolean valid = true;

        DismissThread(int notificationID, int seconds) {
            this.notificationID = notificationID;
            this.seconds = seconds;
        }

        public synchronized void invalidate() {
            valid = false;
        }

        @Override
        public void run() {
            SystemClock.sleep(seconds * 1000);
            synchronized (this) {
                if (valid) {
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

    public static void initiateAction(Context context, @NotNull String actionstring) {
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

        Wearable.ChannelApi.addListener(googleApiClient, this);
        // requestData();
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
            Wearable.ChannelApi.removeListener(googleApiClient, this);
        }
    }
}
