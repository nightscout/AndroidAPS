package info.nightscout.androidaps.interfaces;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public interface ActivePluginProvider {

    @NotNull BgSourceInterface getActiveBgSource(); // Forced to Dexcom

    @NotNull ProfileInterface getActiveProfileInterface(); // Forced to LocalProfile if not changed

    @NotNull InsulinInterface getActiveInsulin(); // Forced to RapidActing if not changed

    @NotNull APSInterface getActiveAPS(); // Forced to SMB

    @NotNull PumpInterface getActivePump(); // Use in places not reachable without active pump. Otherwise IllegalStateException is thrown

    @NotNull SensitivityInterface getActiveSensitivity(); // Forced to oref1 if not changed

    @NotNull TreatmentsInterface getActiveTreatments(); // Forced to treatments

    @NotNull ArrayList<PluginBase> getPluginsList();

    @NotNull ArrayList<PluginBase> getSpecificPluginsVisibleInListByInterface(Class interfaceClass, PluginType type);

    @NotNull ArrayList<PluginBase> getSpecificPluginsVisibleInList(PluginType type);

    @NotNull ArrayList<PluginBase> getSpecificPluginsListByInterface(Class interfaceClass);

//    @NotNull ArrayList<PluginBase> getSpecificPluginsVisibleInList(Class interfaceClass);

    void verifySelectionInCategories();
}