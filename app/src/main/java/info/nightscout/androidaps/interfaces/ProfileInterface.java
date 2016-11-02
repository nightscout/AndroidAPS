package info.nightscout.androidaps.interfaces;

import android.support.annotation.Nullable;

import info.nightscout.client.data.NSProfile;

/**
 * Created by mike on 14.06.2016.
 */
public interface ProfileInterface {
    @Nullable
    NSProfile getProfile();
}
