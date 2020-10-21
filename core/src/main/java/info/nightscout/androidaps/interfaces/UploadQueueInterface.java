package info.nightscout.androidaps.interfaces;

import info.nightscout.androidaps.db.DbRequest;

public interface UploadQueueInterface {

    void add(DbRequest dbRequest);
}
