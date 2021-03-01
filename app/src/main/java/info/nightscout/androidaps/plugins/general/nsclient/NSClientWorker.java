package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import androidx.annotation.NonNull;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.receivers.BundleStore;
import info.nightscout.androidaps.receivers.DataReceiver;

// cannot be inner class because of needed injection
public class NSClientWorker extends Worker {

    public NSClientWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        ((HasAndroidInjector) context.getApplicationContext()).androidInjector().inject(this);
    }

    @Inject NSClientPlugin nsClientPlugin;
    @Inject BundleStore bundleStore;

    @NonNull
    @Override
    public Result doWork() {
        Bundle bundle = bundleStore.pickup(getInputData().getLong(DataReceiver.STORE_KEY, -1));
        if (bundle == null) return Result.failure();
        String action = getInputData().getString(DataReceiver.ACTION_KEY);
        nsClientPlugin.handleNewDataFromNSClient(action, bundle);
        return Result.success();
    }
}

