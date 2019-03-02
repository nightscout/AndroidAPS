package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import java.util.Locale;

import org.joda.time.LocalDateTime;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;

/**
 * Created by andy on 5/19/18.
 */

public class RileyLinkStatusGeneral extends Fragment implements RefreshableInterface {

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
    TextView firmwareVersion;

    RileyLinkServiceData rileyLinkServiceData;

    MedtronicPumpStatus medtronicPumpStatus;
    boolean first = false;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.rileylink_status_general, container, false);

        return rootView;
    }


    @Override
    public void onStart() {
        super.onStart();
        rileyLinkServiceData = RileyLinkUtil.getRileyLinkServiceData();

        this.connectionStatus = getActivity().findViewById(R.id.rls_t1_connection_status);
        this.configuredAddress = getActivity().findViewById(R.id.rls_t1_configured_address);
        this.connectedDevice = getActivity().findViewById(R.id.rls_t1_connected_device);
        this.connectionError = getActivity().findViewById(R.id.rls_t1_connection_error);
        this.deviceType = getActivity().findViewById(R.id.rls_t1_device_type);
        this.deviceModel = getActivity().findViewById(R.id.rls_t1_device_model);
        this.serialNumber = getActivity().findViewById(R.id.rls_t1_serial_number);
        this.pumpFrequency = getActivity().findViewById(R.id.rls_t1_pump_frequency);
        this.lastUsedFrequency = getActivity().findViewById(R.id.rls_t1_last_used_frequency);
        this.lastDeviceContact = getActivity().findViewById(R.id.rls_t1_last_device_contact);
        this.firmwareVersion = getActivity().findViewById(R.id.rls_t1_firmware_version);

        if (!first) {

            // 7-12
            int[] ids = { R.id.rls_t1_tv02, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
            R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12, R.id.rls_t1_tv13 };

            for (int id : ids) {

                TextView tv = (TextView)getActivity().findViewById(id);
                tv.setText(tv.getText() + ":");
            }

            first = true;
        }

        refreshData();
    }


    public void refreshData() {

        RileyLinkTargetDevice targetDevice = RileyLinkUtil.getTargetDevice();

        this.connectionStatus.setText(MainApp.gs(RileyLinkUtil.getServiceState().getResourceId(targetDevice)));
        this.configuredAddress.setText(rileyLinkServiceData.rileylinkAddress);
        this.connectionError.setText(rileyLinkServiceData.errorCode == null ? //
        "-"
            : MainApp.gs(rileyLinkServiceData.errorCode.getResourceId(targetDevice)));

        this.firmwareVersion.setText("BLE113: " + rileyLinkServiceData.versionBLE113 + "\nCC110: "
            + rileyLinkServiceData.versionCC110.toString());

        // TODO add handling for Omnipod pump status
        this.medtronicPumpStatus = MedtronicUtil.getPumpStatus();

        if (medtronicPumpStatus != null) {
            this.deviceType.setText(MainApp.gs(RileyLinkUtil.getTargetDevice().getResourceId()));
            this.deviceModel.setText(medtronicPumpStatus.pumpType.getDescription());
            this.serialNumber.setText(medtronicPumpStatus.serialNumber);
            this.pumpFrequency.setText(medtronicPumpStatus.pumpFrequency);
            if (MedtronicUtil.getMedtronicPumpModel() != null) // FIXME change this when we have omnipod
                this.connectedDevice.setText("Medtronic " + MedtronicUtil.getMedtronicPumpModel().getPumpModel());
            else
                this.connectedDevice.setText("???");

            if (rileyLinkServiceData.lastGoodFrequency != null)
                this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                    rileyLinkServiceData.lastGoodFrequency));

            // FIXME
            if (medtronicPumpStatus.lastConnection != 0)
                this.lastDeviceContact.setText(StringUtil.toDateTimeString(new LocalDateTime(
                    medtronicPumpStatus.lastDataTime)));
            else
                this.lastDeviceContact.setText("Never");
        }

    }

}
