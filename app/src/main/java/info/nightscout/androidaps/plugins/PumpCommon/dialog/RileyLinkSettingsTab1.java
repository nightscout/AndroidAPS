package info.nightscout.androidaps.plugins.PumpCommon.dialog;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkSettingsTab1 extends Fragment implements RefreshableInterface {

    TextView connectionStatus;
    TextView configuredAddress;
    TextView connectedDevice;
    TextView connectionError;
    TextView deviceType;
    TextView deviceModel;
    TextView serialNumber;
    TextView pumpFrequency;
    TextView lastUsedFrequency;
    TextView lastDeviceContact;

    RileyLinkServiceData rileyLinkServiceData;

    MedtronicPumpStatus medtronicPumpStatus;
    boolean first = false;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_settings_tab1, container, false);

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();
        rileyLinkServiceData = RileyLinkUtil.getRileyLinkServiceData();

        this.connectionStatus = (TextView) getActivity().findViewById(R.id.rls_t1_connection_status);
        this.configuredAddress = (TextView) getActivity().findViewById(R.id.rls_t1_configured_address);
        this.connectedDevice = (TextView) getActivity().findViewById(R.id.rls_t1_connected_device);
        this.connectionError = (TextView) getActivity().findViewById(R.id.rls_t1_connection_error);
        this.deviceType = (TextView) getActivity().findViewById(R.id.rls_t1_device_type);
        this.deviceModel = (TextView) getActivity().findViewById(R.id.rls_t1_device_model);
        this.serialNumber = (TextView) getActivity().findViewById(R.id.rls_t1_serial_number);
        this.pumpFrequency = (TextView) getActivity().findViewById(R.id.rls_t1_pump_frequency);
        this.lastUsedFrequency = (TextView) getActivity().findViewById(R.id.rls_t1_last_used_frequency);
        this.lastDeviceContact = (TextView) getActivity().findViewById(R.id.rls_t1_last_device_contact);

        if (!first) {

            // 7-12
            int[] ids = {R.id.rls_t1_tv02, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
                    R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12};

            for (int id : ids) {

                TextView tv = (TextView) getActivity().findViewById(id);
                tv.setText(tv.getText() + ":");
            }

            first = true;
        }

        refreshData();
    }


    public void refreshData() {

        // FIXME i18n
        this.connectionStatus.setText(rileyLinkServiceData.serviceState.name());
        this.configuredAddress.setText(rileyLinkServiceData.rileylinkAddress);
        // FIXME
        this.connectedDevice.setText("???");
        // FIXME i18n


        this.connectionError.setText(rileyLinkServiceData.errorCode == null ? "-" : rileyLinkServiceData.errorCode.name());

        this.medtronicPumpStatus = RileyLinkUtil.getPumpStatus();

        if (medtronicPumpStatus != null) {
            this.deviceType.setText(RileyLinkTargetDevice.MedtronicPump.name());
            this.deviceModel.setText(medtronicPumpStatus.pumpType.getDescription());
            this.serialNumber.setText(medtronicPumpStatus.serialNumber);
            this.pumpFrequency.setText(medtronicPumpStatus.pumpFrequency);

            if (rileyLinkServiceData.lastGoodFrequency != null)
                this.lastUsedFrequency.setText(rileyLinkServiceData.lastGoodFrequency.toString());

            // FIXME
            if (medtronicPumpStatus.lastConnection == 0)
                this.lastDeviceContact.setText("" + medtronicPumpStatus.lastDataTime);
            else
                this.lastDeviceContact.setText("Never");
        }

    }

}
