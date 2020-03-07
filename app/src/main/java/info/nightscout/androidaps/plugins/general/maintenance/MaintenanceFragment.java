package info.nightscout.androidaps.plugins.general.maintenance;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.food.FoodPlugin;
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.OKDialog;

/**
 *
 */
public class MaintenanceFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maintenance_fragment, container, false);

        view.findViewById(R.id.log_send).setOnClickListener(view1 -> MaintenancePlugin.getPlugin().sendLogs());

        view.findViewById(R.id.log_delete).setOnClickListener(view1 -> MaintenancePlugin.getPlugin().deleteLogs());

        view.findViewById(R.id.nav_resetdb).setOnClickListener(view1 ->
                OKDialog.showConfirmation(getContext(), MainApp.gs(R.string.maintenance), MainApp.gs(R.string.reset_db_confirm), () -> {
                    MainApp.getDbHelper().resetDatabases();
                    // should be handled by Plugin-Interface and
                    // additional service interface and plugin registry
                    FoodPlugin.getPlugin().getService().resetFood();
                    TreatmentsPlugin.getPlugin().getService().resetTreatments();
                })
        );
        view.findViewById(R.id.nav_export).setOnClickListener(view1 -> {
            // start activity for checking permissions...
            ImportExportPrefs.verifyStoragePermissions(this);
            ImportExportPrefs.exportSharedPreferences(this);
        });

        view.findViewById(R.id.nav_import).setOnClickListener(view1 -> {
            // start activity for checking permissions...
            ImportExportPrefs.verifyStoragePermissions(this);
            ImportExportPrefs.importSharedPreferences(this);
        });

        view.findViewById(R.id.nav_logsettings).setOnClickListener(view1 -> {
            startActivity(new Intent(getActivity(), LogSettingActivity.class));
        });

        return view;
    }

}
