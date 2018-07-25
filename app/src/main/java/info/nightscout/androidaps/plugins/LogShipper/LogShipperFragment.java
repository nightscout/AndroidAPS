package info.nightscout.androidaps.plugins.LogShipper;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import info.nightscout.androidaps.R;

/**
 *
 */
public class LogShipperFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.logshipper_fragment, container, false);

        view.findViewById(R.id.log_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LogShipperPlugin.getPlugin().sendLogs();
            }
        });

        view.findViewById(R.id.log_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LogShipperPlugin.getPlugin().deleteLogs();
            }
        });

        return view;
    }

}
