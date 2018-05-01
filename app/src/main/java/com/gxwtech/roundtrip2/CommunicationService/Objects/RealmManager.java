package com.gxwtech.roundtrip2.CommunicationService.Objects;

import io.realm.Realm;

/**
 * Created by Tim on 11/08/2016.
 */
public class RealmManager {
    private Realm realm;

    public RealmManager(){
        realm = Realm.getDefaultInstance();
    }

    public void closeRealm(){
        realm.close();
    }

    public Realm getRealm(){
        if (realm.isClosed() || realm.isEmpty()) realm = Realm.getDefaultInstance();
        return realm;
    }
}
