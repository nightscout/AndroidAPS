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

/**
 *
 */
public class MaintenanceFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.maintenance_fragment, container, false);

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

            }
        });

        return view;
    }

}
