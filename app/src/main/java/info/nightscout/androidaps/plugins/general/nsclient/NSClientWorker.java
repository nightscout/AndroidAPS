package info.nightscout.androidaps.plugins.general.nsclient;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import dagger.android.HasAndroidInjector;

// cannot be inner class because of needed injection
public class NSClientWorker extends Worker {

    public NSClientWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        ((HasAndroidInjector) context.getApplicationContext()).androidInjector().inject(this);
    }

    @Inject NSClientPlugin nsClientPlugin;

    @NotNull
    @Override
    public Result doWork() {
        Bundle bundle = new Gson().fromJson(getInputData().getString("data"), Bundle.class);
        String action = getInputData().getString("action");
        nsClientPlugin.handleNewDataFromNSClient(action, bundle);
        return Result.success();
    }
}

