package info.nightscout.androidaps.plugins.Maintenance;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.Food.FoodPlugin;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.ImportExportPrefs;

/**
 *
 */
public class MaintenanceFragment extends Fragment {

    private Fragment f;

    @Override
    public void onResume() {
        super.onResume();

        this.f = this;
    }

    @Override
    public void onPause() {
        super.onPause();

        this.f = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maintenance_fragment, container, false);

        final Fragment f = this;

        view.findViewById(R.id.log_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MaintenancePlugin.getPlugin().sendLogs();
            }
        });

        view.findViewById(R.id.log_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MaintenancePlugin.getPlugin().deleteLogs();
            }
        });

        view.findViewById(R.id.nav_resetdb).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(f.getContext())
                        .setTitle(R.string.nav_resetdb)
                        .setMessage(R.string.reset_db_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            MainApp.getDbHelper().resetDatabases();
                            // should be handled by Plugin-Interface and
                            // additional service interface and plugin registry
                            FoodPlugin.getPlugin().getService().resetFood();
                            TreatmentsPlugin.getPlugin().getService().resetTreatments();
                        })
                        .create()
                        .show();
            }
        });

        view.findViewById(R.id.nav_export).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // start activity for checking permissions...
                ImportExportPrefs.verifyStoragePermissions(f);
                ImportExportPrefs.exportSharedPreferences(f);
            }
        });

        view.findViewById(R.id.nav_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // start activity for checking permissions...
                ImportExportPrefs.verifyStoragePermissions(f);
                ImportExportPrefs.importSharedPreferences(f);
            }
        });


        return view;
    }

}
