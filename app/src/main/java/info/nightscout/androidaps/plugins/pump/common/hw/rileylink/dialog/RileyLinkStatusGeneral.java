package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import org.joda.time.LocalDateTime;

import java.util.Locale;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkTargetDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.StringUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;

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
    OmnipodPumpStatus omnipodPumpStatus;
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
            int[] ids = {R.id.rls_t1_tv02, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
                    R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12, R.id.rls_t1_tv13};

            for (int id : ids) {

                TextView tv = (TextView) getActivity().findViewById(id);
                tv.setText(tv.getText() + ":");
            }

            first = true;
        }

        refreshData();
    }


    public void refreshData() {

        RileyLinkTargetDevice targetDevice = RileyLinkUtil.getTargetDevice();

        if (RileyLinkUtil.getServiceState() == null)
            this.connectionStatus.setText(MainApp.gs(RileyLinkServiceState.NotStarted.getResourceId(targetDevice)));
        else
            this.connectionStatus.setText(MainApp.gs(RileyLinkUtil.getServiceState().getResourceId(targetDevice)));

        if (rileyLinkServiceData != null) {
            this.configuredAddress.setText(rileyLinkServiceData.rileylinkAddress);
            this.connectionError.setText(rileyLinkServiceData.errorCode == null ? //
                    "-"
                    : MainApp.gs(rileyLinkServiceData.errorCode.getResourceId(targetDevice)));


            RileyLinkFirmwareVersion firmwareVersion = rileyLinkServiceData.versionCC110;

            if (firmwareVersion == null) {
                this.firmwareVersion.setText("BLE113: -\nCC110: -");
            } else {
                this.firmwareVersion.setText("BLE113: " + rileyLinkServiceData.versionBLE113 + //
                        "\nCC110: " + firmwareVersion.toString());
            }

        }

        if (MedtronicUtil.isMedtronicPump()) {

            this.medtronicPumpStatus = MedtronicUtil.getPumpStatus();

            if (medtronicPumpStatus != null) {
                this.deviceType.setText(MainApp.gs(RileyLinkTargetDevice.MedtronicPump.getResourceId()));
                this.deviceModel.setText(medtronicPumpStatus.pumpType.getDescription());
                this.serialNumber.setText(medtronicPumpStatus.serialNumber);
                this.pumpFrequency.setText(MainApp.gs(medtronicPumpStatus.pumpFrequency.equals("medtronic_pump_frequency_us_ca") ? R.string.medtronic_pump_frequency_us_ca : R.string.medtronic_pump_frequency_worldwide));

                if (MedtronicUtil.getMedtronicPumpModel() != null)
                    this.connectedDevice.setText("Medtronic " + MedtronicUtil.getMedtronicPumpModel().getPumpModel());
                else
                    this.connectedDevice.setText("???");

                if (rileyLinkServiceData.lastGoodFrequency != null)
                    this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                            rileyLinkServiceData.lastGoodFrequency));

                if (medtronicPumpStatus.lastConnection != 0)
                    this.lastDeviceContact.setText(StringUtil.toDateTimeString(new LocalDateTime(
                            medtronicPumpStatus.lastDataTime)));
                else
                    this.lastDeviceContact.setText(MainApp.gs(R.string.common_never));
            }
        } else {

            // if (OmnipodUtil.isOmnipodDash())
            // TODO add handling for Omnipod Dash pump status
            this.omnipodPumpStatus = OmnipodUtil.getPumpStatus();

            if (omnipodPumpStatus != null) {
                this.deviceType.setText(MainApp.gs(RileyLinkTargetDevice.Omnipod.getResourceId()));
                this.deviceModel.setText(omnipodPumpStatus.pumpType == PumpType.Insulet_Omnipod ? "Eros" : "Dash");
                this.pumpFrequency.setText(MainApp.gs(R.string.omnipod_frequency));

                if (omnipodPumpStatus.podAvailable) {
                    this.serialNumber.setText(omnipodPumpStatus.podLotNumber);
                    this.connectedDevice.setText(omnipodPumpStatus.pumpType == PumpType.Insulet_Omnipod ? "Eros Pod" : "Dash Pod");
                } else {
                    this.serialNumber.setText("??");
                    this.connectedDevice.setText("-");
                }

                if (rileyLinkServiceData.lastGoodFrequency != null)
                    this.lastUsedFrequency.setText(String.format(Locale.ENGLISH, "%.2f MHz",
                            rileyLinkServiceData.lastGoodFrequency));

                if (omnipodPumpStatus.lastConnection != 0)
                    this.lastDeviceContact.setText(StringUtil.toDateTimeString(new LocalDateTime(
                            omnipodPumpStatus.lastDataTime)));
                else
                    this.lastDeviceContact.setText(MainApp.gs(R.string.common_never));
            }

        }

    }

}
