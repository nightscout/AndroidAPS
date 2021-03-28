package info.nightscout.androidaps.interfaces;

import org.json.JSONObject;

import info.nightscout.androidaps.db.DbRequest;

public interface UploadQueueInterface {

    long size();

    void add(DbRequest dbRequest);

    void removeByNsClientIdIfExists(JSONObject record);

    void removeByMongoId(final String action, final String _id);

    String textList();
}
