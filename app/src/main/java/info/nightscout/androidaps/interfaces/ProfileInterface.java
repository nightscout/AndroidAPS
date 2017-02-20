package info.nightscout.androidaps.interfaces;

import android.support.annotation.Nullable;

import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;

/**
 * Created by mike on 14.06.2016.
 */
public interface ProfileInterface {
    @Nullable
    NSProfile getProfile();
}
