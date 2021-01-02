package info.nightscout.androidaps.db;

import java.util.concurrent.ScheduledFuture;

/**
 * Created by triplem on 05.01.18.
 */

public interface ICallback {

    void setPost(ScheduledFuture<?> post);

    ScheduledFuture<?> getPost();

}
