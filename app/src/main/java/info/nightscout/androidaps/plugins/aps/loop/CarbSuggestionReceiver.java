package info.nightscout.androidaps.plugins.aps.loop;

import android.content.Context;
import android.content.Intent;

import javax.inject.Inject;

import dagger.android.DaggerBroadcastReceiver;

public class CarbSuggestionReceiver extends DaggerBroadcastReceiver {

    @Inject LoopPlugin loopPlugin;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        int duartion = intent.getIntExtra("ignoreDuration", 5);
        loopPlugin.disableCarbSuggestions(duartion);
    }
}
