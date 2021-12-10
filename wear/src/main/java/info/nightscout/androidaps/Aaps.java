package info.nightscout.androidaps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;
import info.nightscout.androidaps.di.DaggerAppComponent;

/**
 * Created for xDrip+ by Emma Black on 3/21/15.
 * Adapted for AAPS by dlvoy 2019-11-06.
 */

public class Aaps extends DaggerApplication implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreate() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);
        super.onCreate();
    }

    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        return DaggerAppComponent
                .builder()
                .application(this)
                .build();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // we trigger update on Complications
        Intent messageIntent = new Intent();
        messageIntent.setAction(Intent.ACTION_SEND);
        LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
    }
}
