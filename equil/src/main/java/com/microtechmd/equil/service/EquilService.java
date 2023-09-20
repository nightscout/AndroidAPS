package com.microtechmd.equil.service;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import javax.inject.Inject;

import dagger.android.DaggerService;
import info.nightscout.shared.sharedPreferences.SP;

public class EquilService extends DaggerService {
    private final IBinder mBinder = new LocalBinder();

    @Inject SP sp;

    public class LocalBinder extends Binder {
        public EquilService getServiceInstance() {
            return EquilService.this;
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override public void onCreate() {
        super.onCreate();
    }

    public boolean setNotInPreInit() {
        return false;
    }

    public boolean isConnected() {
        return true;
    }
}
