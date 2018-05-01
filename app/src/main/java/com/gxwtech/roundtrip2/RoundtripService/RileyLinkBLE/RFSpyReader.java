package com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE;

import android.content.Context;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import com.gxwtech.roundtrip2.RoundtripService.RileyLinkBLE.BLECommOperations.BLECommOperationResult;
import com.gxwtech.roundtrip2.util.ByteUtil;
import com.gxwtech.roundtrip2.util.ThreadUtil;

import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by geoff on 5/26/16.
 */
public class RFSpyReader {
    private static final String TAG = "RFSpyReader";
    private Context context;
    private RileyLinkBLE rileyLinkBle;
    private Semaphore waitForRadioData = new Semaphore(0,true);
    AsyncTask<Void,Void,Void> readerTask;
    private LinkedBlockingQueue<byte[]> mDataQueue = new LinkedBlockingQueue<>();
    private int acquireCount = 0;
    private int releaseCount = 0;

    public RFSpyReader(Context context, RileyLinkBLE rileyLinkBle) {
        this.context = context;
        this.rileyLinkBle = rileyLinkBle;
    }

    public void init(Context context, RileyLinkBLE rileyLinkBLE) {
        this.context = context;
        this.rileyLinkBle = rileyLinkBLE;
    }

    public void setRileyLinkBle(RileyLinkBLE rileyLinkBle) {
        if (readerTask != null) {
            readerTask.cancel(true);
        }
        this.rileyLinkBle = rileyLinkBle;
    }

    // This timeout must be coordinated with the length of the RFSpy radio operation or Bad Things Happen.
    public byte[] poll(int timeout_ms) {
        Log.v(TAG, ThreadUtil.sig()+"Entering poll at t=="+SystemClock.uptimeMillis()+", timeout is "+timeout_ms+" mDataQueue size is "+mDataQueue.size());
        if (mDataQueue.isEmpty())
        try {
            // block until timeout or data available.
            // returns null if timeout.
            byte[] dataFromQueue = mDataQueue.poll(timeout_ms, TimeUnit.MILLISECONDS);
            if (dataFromQueue != null) {
                Log.d(TAG, "Got data [" + ByteUtil.shortHexString(dataFromQueue) + "] at t==" + SystemClock.uptimeMillis());
            } else {
                Log.d(TAG, "Got data [null] at t==" + SystemClock.uptimeMillis());
            }
            return dataFromQueue;
        } catch (InterruptedException e) {
            Log.e(TAG,"poll: Interrupted waiting for data");
        }
        return null;
    }

    // Call this from the "response count" notification handler.
    public void newDataIsAvailable() {
        releaseCount++;
        Log.v(TAG,ThreadUtil.sig()+"waitForRadioData released(count="+releaseCount+") at t="+SystemClock.uptimeMillis());
        waitForRadioData.release();
    }

    public void start() {
        readerTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                UUID serviceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO);
                UUID radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA);
                BLECommOperationResult result;
                while (true) {
                    try {
                        acquireCount++;
                        waitForRadioData.acquire();
                        Log.v(TAG,ThreadUtil.sig()+"waitForRadioData acquired (count="+acquireCount+") at t="+SystemClock.uptimeMillis());
                        SystemClock.sleep(100);
                        SystemClock.sleep(1);
                        result = rileyLinkBle.readCharacteristic_blocking(serviceUUID, radioDataUUID);
                        SystemClock.sleep(100);

                        if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
                            // only data up to the first null is valid
                            for (int i=0; i<result.value.length; i++) {
                                if (result.value[i]==0) {
                                    result.value = ByteUtil.substring(result.value,0,i);
                                    break;
                                }
                            }
                            mDataQueue.add(result.value);
                        } else if (result.resultCode == BLECommOperationResult.RESULT_INTERRUPTED) {
                            Log.e(TAG, "Read operation was interrupted");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_TIMEOUT) {
                            Log.e(TAG, "Read operation on Radio Data timed out");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_BUSY) {
                            Log.e(TAG, "FAIL: RileyLinkBLE reports operation already in progress");
                        } else if (result.resultCode == BLECommOperationResult.RESULT_NONE) {
                            Log.e(TAG, "FAIL: got invalid result code: " + result.resultCode);
                        }
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting for data");
                    }
                }
            }
        }.execute();
    }

}
