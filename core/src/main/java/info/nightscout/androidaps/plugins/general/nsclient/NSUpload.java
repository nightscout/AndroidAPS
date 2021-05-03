package info.nightscout.androidaps.plugins.general.nsclient;

import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.db.DbRequest;
import info.nightscout.androidaps.interfaces.UploadQueueInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 26.05.2017.
 */
@Singleton
public class NSUpload {

    private final AAPSLogger aapsLogger;
    private final SP sp;
    private final UploadQueueInterface uploadQueue;

    @Inject
    public NSUpload(
            AAPSLogger aapsLogger,
            SP sp,
            UploadQueueInterface uploadQueue
    ) {
        this.aapsLogger = aapsLogger;
        this.sp = sp;
        this.uploadQueue = uploadQueue;
    }

    public void uploadProfileStore(JSONObject profileStore) {
        if (sp.getBoolean(R.string.key_ns_uploadlocalprofile, false)) {
            uploadQueue.add(new DbRequest("dbAdd", "profile", profileStore, System.currentTimeMillis()));
        }
    }
}
