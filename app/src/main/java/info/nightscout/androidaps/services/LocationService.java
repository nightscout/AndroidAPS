package info.nightscout.androidaps.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.app.ActivityCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventLocationChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.persistentNotification.PersistentNotificationPlugin;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.SP;
import info.nightscout.androidaps.utils.T;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class LocationService extends Service {
    private static Logger log = LoggerFactory.getLogger(L.LOCATION);
    private CompositeDisposable disposable = new CompositeDisposable();

    private LocationManager mLocationManager = null;
    private static final float LOCATION_DISTANCE = 10f;

    private static final long LOCATION_INTERVAL_ACTIVE = T.mins(5).msecs();
    private static final long LOCATION_INTERVAL_PASSIVE = T.mins(1).msecs(); // this doesn't cost more power

    private static Location mLastLocation;

    private class LocationListener implements android.location.LocationListener {

        LocationListener(String provider) {
            if (L.isEnabled(L.LOCATION))
                log.debug("LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            if (L.isEnabled(L.LOCATION))
                log.debug("onLocationChanged: " + location);
            mLastLocation.set(location);
            RxBus.INSTANCE.send(new EventLocationChange(location));
        }

        @Override
        public void onProviderDisabled(String provider) {
            if (L.isEnabled(L.LOCATION))
                log.debug("onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            if (L.isEnabled(L.LOCATION))
                log.debug("onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            if (L.isEnabled(L.LOCATION))
                log.debug("onStatusChanged: " + provider);
        }
    }

    LocationListener mLocationListener;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (L.isEnabled(L.LOCATION))
            log.debug("onStartCommand");
        startForeground(PersistentNotificationPlugin.ONGOING_NOTIFICATION_ID, PersistentNotificationPlugin.getPlugin().getLastNotification());
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(PersistentNotificationPlugin.ONGOING_NOTIFICATION_ID, PersistentNotificationPlugin.getPlugin().getLastNotification());

        if (L.isEnabled(L.LOCATION))
            log.debug("onCreate");

        initializeLocationManager();

        try {
            if (SP.getString(R.string.key_location, "NONE").equals("NETWORK"))
                mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        LOCATION_INTERVAL_ACTIVE,
                        LOCATION_DISTANCE,
                        mLocationListener = new LocationListener(LocationManager.NETWORK_PROVIDER)
                );
            if (SP.getString(R.string.key_location, "NONE").equals("GPS"))
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_INTERVAL_ACTIVE,
                        LOCATION_DISTANCE,
                        mLocationListener = new LocationListener(LocationManager.GPS_PROVIDER)
                );
            if (SP.getString(R.string.key_location, "NONE").equals("PASSIVE"))
                mLocationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        LOCATION_INTERVAL_PASSIVE,
                        LOCATION_DISTANCE,
                        mLocationListener = new LocationListener(LocationManager.PASSIVE_PROVIDER)
                );
        } catch (java.lang.SecurityException ex) {
            log.error("fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            log.error("network provider does not exist, " + ex.getMessage());
        }
        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (L.isEnabled(L.CORE)) log.debug("EventAppExit received");
                    stopSelf();
                }, FabricPrivacy::logException)
        );
    }

    @Override
    public void onDestroy() {
        if (L.isEnabled(L.LOCATION))
            log.debug("onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            try {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mLocationManager.removeUpdates(mLocationListener);
            } catch (Exception ex) {
                log.error("fail to remove location listener, ignore", ex);
            }
        }
        disposable.clear();
    }

    private void initializeLocationManager() {
        if (L.isEnabled(L.LOCATION))
            log.debug("initializeLocationManager - Provider: " + SP.getString(R.string.key_location, "NONE"));
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    public static Location getLastLocation() {
        return mLastLocation;
    }
}
