package info.nightscout.androidaps.interfaces;

import androidx.annotation.Nullable;

/**
 * Created by mike on 14.06.2016.
 */
public interface ProfileInterface {
    @Nullable
    ProfileStore getProfile();
    String getProfileName();
}
