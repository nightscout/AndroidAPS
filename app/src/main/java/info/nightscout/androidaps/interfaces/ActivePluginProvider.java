package info.nightscout.androidaps.interfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

public interface ActivePluginProvider {

    @Nullable BgSourceInterface getActiveBgSource();

    @NotNull ProfileInterface getActiveProfileInterface(); // Forced to LocalProfile if not changed

    @NonNull InsulinInterface getActiveInsulin(); // Forced to RapidActing if not changed

    @Nullable APSInterface getActiveAPS();

    @Nullable PumpInterface getActivePumpPlugin(); // Use in UI to disable buttons or check if pump is selected

    @NotNull PumpInterface getActivePump(); // Use in places not reachable without active pump. Otherwise IllegalStateException is thrown

    @NotNull SensitivityInterface getActiveSensitivity(); // Forced to oref1 if not changed

    @NotNull TreatmentsInterface getActiveTreatments();
}