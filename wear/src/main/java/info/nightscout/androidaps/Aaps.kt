package info.nightscout.androidaps

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import info.nightscout.androidaps.di.DaggerWearComponent

class Aaps : DaggerApplication(), OnSharedPreferenceChangeListener {

    override fun onCreate() {
        super.onCreate()
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerWearComponent
            .builder()
            .application(this)
            .build()

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // we trigger update on Complications
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Intent.ACTION_SEND))
    }
}