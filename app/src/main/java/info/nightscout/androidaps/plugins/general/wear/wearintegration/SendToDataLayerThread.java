package info.nightscout.androidaps.plugins.general.wear.wearintegration;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by emmablack on 12/26/14.
 */
class SendToDataLayerThread extends AsyncTask<DataMap,Void,Void> {
    private GoogleApiClient googleApiClient;
    private static final String TAG = "SendToDataLayerThread";
    private String path;
    private String logPrefix = ""; // "WR: ";
    private static int concurrency = 0;
    private static int state = 0;
    private static final ReentrantLock lock = new ReentrantLock();
    private static long lastlock = 0;
    private static final boolean testlockup = false; // always false in production


    SendToDataLayerThread(String path, GoogleApiClient pGoogleApiClient) {
        // Log.d(TAG, logPrefix + "SendToDataLayerThread: " + path);
        this.path = path;
        googleApiClient = pGoogleApiClient;
    }


    @Override
    protected void onPreExecute() {
        concurrency++;
        if ((concurrency > 12) || ((concurrency > 3 && (lastlock != 0) && (tsl() - lastlock) > 300000))) {
            // error if 9 concurrent threads or lock held for >5 minutes with concurrency of 4
            final String err = "Wear Integration deadlock detected!! " + ((lastlock != 0) ? "locked" : "") + " state:"
                + state + " @" + hourMinuteString(tsl());
            // Home.toaststaticnext(err);
            Log.e(TAG, logPrefix + err);
        }
        if (concurrency < 0)
            Log.d(TAG, logPrefix + "Wear Integration impossible concurrency!!");

        Log.d(TAG, logPrefix + "SendDataToLayerThread pre-execute concurrency: " + concurrency);
    }


    @Override
    protected Void doInBackground(DataMap... params) {
        if (testlockup) {
            try {
                Log.e(TAG, logPrefix + "WARNING RUNNING TEST LOCK UP CODE - NEVER FOR PRODUCTION");
                Thread.sleep(1000000); // DEEEBBUUGGGG
            } catch (Exception e) {
            }
        }
        sendToWear(params);
        concurrency--;
        Log.d(TAG, logPrefix + "SendDataToLayerThread post-execute concurrency: " + concurrency);
        return null;
    }


    // Debug function to expose where it might be locking up
    private synchronized void sendToWear(final DataMap... params) {
        if (!lock.tryLock()) {
            Log.d(TAG, logPrefix + "Concurrent access - waiting for thread unlock");
            lock.lock(); // enforce single threading
            Log.d(TAG, logPrefix + "Thread unlocked - proceeding");
        }
        lastlock = tsl();
        try {
            if (state != 0) {
                Log.e(TAG, logPrefix + "WEAR STATE ERROR: state=" + state);
            }
            state = 1;
            final NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await(15,
                TimeUnit.SECONDS);

            Log.d(TAG, logPrefix + "Nodes: " + nodes);

            state = 2;
            for (Node node : nodes.getNodes()) {
                state = 3;
                for (DataMap dataMap : params) {
                    state = 4;
                    PutDataMapRequest putDMR = PutDataMapRequest.create(path);
                    state = 5;
                    putDMR.getDataMap().putAll(dataMap);
                    putDMR.setUrgent();
                    state = 6;
                    PutDataRequest request = putDMR.asPutDataRequest();
                    state = 7;
                    DataApi.DataItemResult result = Wearable.DataApi.putDataItem(googleApiClient, request).await(15,
                        TimeUnit.SECONDS);
                    state = 8;
                    if (result.getStatus().isSuccess()) {
                        Log.d(TAG, logPrefix + "DataMap: " + dataMap + " sent to: " + node.getDisplayName());
                    } else {
                        Log.e(TAG, logPrefix + "ERROR: failed to send DataMap");
                        result = Wearable.DataApi.putDataItem(googleApiClient, request).await(30, TimeUnit.SECONDS);
                        if (result.getStatus().isSuccess()) {
                            Log.d(TAG, logPrefix + "DataMap retry: " + dataMap + " sent to: " + node.getDisplayName());
                        } else {
                            Log.e(TAG, logPrefix + "ERROR on retry: failed to send DataMap: "
                                + result.getStatus().toString());
                        }
                    }
                    state = 9;
                }
            }
            state = 0;
        } catch (Exception e) {
            Log.e(TAG, logPrefix + "Got exception in sendToWear: " + e.toString());
        } finally {
            lastlock = 0;
            lock.unlock();
        }
    }


    private static long tsl() {
        return System.currentTimeMillis();
    }


    private static String hourMinuteString(long timestamp) {
        return android.text.format.DateFormat.format("kk:mm", timestamp).toString();
    }
}
