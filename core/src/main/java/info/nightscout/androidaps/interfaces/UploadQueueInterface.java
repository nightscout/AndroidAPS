package info.nightscout.androidaps.interfaces;

import org.json.JSONObject;

import info.nightscout.androidaps.db.DbRequest;

public interface UploadQueueInterface {

    long size();

    void add(DbRequest dbRequest);
}
