package info.nightscout.androidaps.plugins.Wear.wearintegration;

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

/**
 * Created by stephenblack on 12/26/14.
 */
class SendToDataLayerThread extends AsyncTask<DataMap,Void,Void> {
    private GoogleApiClient googleApiClient;
    private static final String TAG = "SendDataThread";
    String path;

    SendToDataLayerThread(String path, GoogleApiClient pGoogleApiClient) {
        this.path = path;
        googleApiClient = pGoogleApiClient;
    }

    @Override
    protected Void doInBackground(DataMap... params) {
        try {
            final NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleApiClient).await(15, TimeUnit.SECONDS);
            for (Node node : nodes.getNodes()) {
                for (DataMap dataMap : params) {
                    PutDataMapRequest putDMR = PutDataMapRequest.create(path);
                    putDMR.getDataMap().putAll(dataMap);
                    PutDataRequest request = putDMR.asPutDataRequest();
                    DataApi.DataItemResult result = Wearable.DataApi.putDataItem(googleApiClient, request).await(15, TimeUnit.SECONDS);
                    if (result.getStatus().isSuccess()) {
                        Log.d(TAG, "DataMap: " + dataMap + " sent to: " + node.getDisplayName());
                    } else {
                        Log.d(TAG, "ERROR: failed to send DataMap");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Got exception sending data to wear: " + e.toString());
        }
        return null;
    }
}
