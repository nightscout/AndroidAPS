package info.nightscout.androidaps.interfaces;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

/**
 * Created by adrian on 2020-01-07.
 */

public interface ActivePluginProvider {
    @Nullable BgSourceInterface getActiveBgSource();

    @NotNull ProfileInterface getActiveProfileInterface();

    @Nullable InsulinInterface getActiveInsulin();

    @Nullable APSInterface getActiveAPS();

    @Nullable PumpInterface getActivePump();

    @Nullable SensitivityInterface getActiveSensitivity();
}
