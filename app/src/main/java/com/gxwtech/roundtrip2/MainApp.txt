package com.gxwtech.roundtrip2;

import android.app.Application;

import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by Tim on 15/06/2016.
 */
public class MainApp extends Application {

    private static MainApp sInstance;
    private static ServiceClientConnection serviceClientConnection;

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;
        serviceClientConnection = new ServiceClientConnection();

        //initialize Realm
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder(instance())
                .name("rt2.realm")
                .schemaVersion(0)
                .deleteRealmIfMigrationNeeded() // TODO: 03/08/2016 @TIM remove
                .build();
        Realm.setDefaultConfiguration(realmConfiguration);
    }




    public static MainApp instance() {
        return sInstance;
    }

    public static ServiceClientConnection getServiceClientConnection(){
        if (serviceClientConnection == null) {
            serviceClientConnection = new ServiceClientConnection();
        }
        return serviceClientConnection;
    }

    // TODO: 09/07/2016 @TIM uncomment ServiceClientConnection once class is added

}