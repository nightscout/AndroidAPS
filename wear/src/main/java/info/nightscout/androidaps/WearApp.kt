package info.nightscout.androidaps

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import info.nightscout.androidaps.comm.DataHandlerWear
import info.nightscout.androidaps.comm.DataLayerListenerServiceWear
import info.nightscout.androidaps.comm.ExceptionHandlerWear
import info.nightscout.androidaps.di.DaggerWearComponent
import info.nightscout.androidaps.events.EventWearPreferenceChange
import javax.inject.Inject

class WearApp : DaggerApplication(), OnSharedPreferenceChangeListener {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Suppress("unused")
    @Inject lateinit var dataHandlerWear: DataHandlerWear // instantiate only
    @Inject lateinit var exceptionHandlerWear: ExceptionHandlerWear

    override fun onCreate() {
        super.onCreate()
        exceptionHandlerWear.register()
        aapsLogger.debug(LTag.WEAR, "onCreate")
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        startService(Intent(this, DataLayerListenerServiceWear::class.java))
    }

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> =
        DaggerWearComponent
            .builder()
            .application(this)
            .build()

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key ?: return
        // We trigger update on Complications
        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DataLayerListenerServiceWear.INTENT_NEW_DATA))
        rxBus.send(EventWearPreferenceChange(key))
    }
}
