package info.nightscout.androidaps.plugins.PumpDanaR.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRv2.DanaRv2Plugin;
import info.nightscout.utils.NumberPicker;

/**
 * Created by Rumen Georgiev on 5/31/2018.
 */

public class DanaRUserOptionsActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(DanaRUserOptionsActivity.class);

    Switch timeFormat;
    Switch buttonScroll;
    Switch beep;
    RadioGroup pumpAlarm;
    RadioButton pumpAlarmSound;
    RadioButton pumpAlarmVibrate;
    RadioButton pumpAlarmBoth;
    Switch pumpUnits;
    NumberPicker screenTimeout;
    NumberPicker backlightTimeout;
    NumberPicker shutdown;
    NumberPicker lowReservoir;
    Button saveToPumpButton;

    @Override
    protected void onResume() {
        super.onResume();
        MainApp.bus().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MainApp.bus().unregister(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.danar_user_options);

        timeFormat = (Switch) findViewById(R.id.danar_timeformat);
        buttonScroll = (Switch) findViewById(R.id.danar_buttonscroll);
        beep = (Switch) findViewById(R.id.danar_beep);
        pumpAlarm = (RadioGroup) findViewById(R.id.danar_pumpalarm);
        pumpAlarmSound = (RadioButton) findViewById(R.id.danar_pumpalarm_sound);
        pumpAlarmVibrate = (RadioButton) findViewById(R.id.danar_pumpalarm_vibrate);
        pumpAlarmBoth = (RadioButton) findViewById(R.id.danar_pumpalarm_both);
        screenTimeout = (NumberPicker) findViewById(R.id.danar_screentimeout);
        backlightTimeout = (NumberPicker) findViewById(R.id.danar_backlight);
        pumpUnits = (Switch) findViewById(R.id.danar_units);
        shutdown = (NumberPicker) findViewById(R.id.danar_shutdown);
        lowReservoir = (NumberPicker) findViewById(R.id.danar_lowreservoir);
        saveToPumpButton = (Button) findViewById(R.id.save_user_options);

        saveToPumpButton.setOnClickListener(v -> onSaveClick());

        DanaRPump pump = DanaRPump.getInstance();
        //used for debugging
        log.debug("UserOptionsLoaded:" + (System.currentTimeMillis() - pump.lastConnection) / 1000 + " s ago"
                + "\ntimeDisplayType:" + pump.timeDisplayType
                + "\nbuttonScroll:" + pump.buttonScrollOnOff
                + "\ntimeDisplayType:" + pump.timeDisplayType
                + "\nlcdOnTimeSec:" + pump.lcdOnTimeSec
                + "\nbacklight:" + pump.backlightOnTimeSec
                + "\npumpUnits:" + pump.units
                + "\nlowReservoir:" + pump.lowReservoirRate);

        screenTimeout.setParams((double) pump.lcdOnTimeSec, 5d, 240d, 5d, new DecimalFormat("1"), false);
        backlightTimeout.setParams((double) pump.backlightOnTimeSec, 1d, 60d, 1d, new DecimalFormat("1"), false);
        shutdown.setParams((double) pump.shutdownHour, 0d, 24d, 1d, new DecimalFormat("1"), true);
        lowReservoir.setParams((double) pump.lowReservoirRate, 10d, 60d, 10d, new DecimalFormat("10"), false);
        switch (pump.beepAndAlarm) {
            case 1: pumpAlarmSound.setChecked(true);
                break;
            case 2: pumpAlarmVibrate.setChecked(true);
                break;
            case 3: pumpAlarmBoth.setChecked(true);
                break;
            case 5: pumpAlarmSound.setChecked(true);
                beep.setChecked(true);
                break;
            case 6: pumpAlarmVibrate.setChecked(true);
                beep.setChecked(true);
                break;
            case 7: pumpAlarmBoth.setChecked(true);
                beep.setChecked(true);
                break;
        }
        if (pump.lastSettingsRead == 0)
            log.debug("No settings loaded from pump!");
        else
            setData();
    }

    public void setData() {
        DanaRPump pump = DanaRPump.getInstance();

        timeFormat.setChecked(pump.timeDisplayType != 0);
        buttonScroll.setChecked(pump.buttonScrollOnOff != 0);
        beep.setChecked(pump.beepAndAlarm > 4);
        screenTimeout.setValue((double) pump.lcdOnTimeSec);
        backlightTimeout.setValue((double) pump.backlightOnTimeSec);
        pumpUnits.setChecked(pump.getUnits() != null && pump.getUnits().equals(Constants.MMOL));
        shutdown.setValue((double) pump.shutdownHour);
        lowReservoir.setValue((double) pump.lowReservoirRate);
    }

    @Subscribe
    public void onEventInitializationChanged(EventInitializationChanged ignored) {
        runOnUiThread(this::setData);
    }

    public void onSaveClick() {
        boolean isRS = MainApp.getSpecificPlugin(DanaRSPlugin.class) != null && MainApp.getSpecificPlugin(DanaRSPlugin.class).isEnabled(PluginType.PUMP);
        boolean isDanaR = MainApp.getSpecificPlugin(DanaRPlugin.class) != null && MainApp.getSpecificPlugin(DanaRPlugin.class).isEnabled(PluginType.PUMP);
        boolean isDanaRv2 = MainApp.getSpecificPlugin(DanaRv2Plugin.class) != null && MainApp.getSpecificPlugin(DanaRv2Plugin.class).isEnabled(PluginType.PUMP);
        if (!isRS && !isDanaR && !isDanaRv2) {
            //exit if pump is not DanaRS, Dana!, or DanaR with upgraded firmware
            return;
        }
        DanaRPump pump = DanaRPump.getInstance();

        if (timeFormat.isChecked())
            pump.timeDisplayType = 1;
        else
            pump.timeDisplayType = 0;
        if (buttonScroll.isChecked())
            pump.buttonScrollOnOff = 1;
        else
            pump.buttonScrollOnOff = 0;

        pump.beepAndAlarm = 1; // default
        if (pumpAlarmSound.isChecked()) pump.beepAndAlarm = 1;
        else if (pumpAlarmVibrate.isChecked()) pump.beepAndAlarm = 2;
        else if (pumpAlarmBoth.isChecked()) pump.beepAndAlarm = 3;
        if (beep.isChecked()) pump.beepAndAlarm += 4;


        // step is 5 seconds
        int screenTimeoutValue = !screenTimeout.getText().isEmpty() ? (Integer.parseInt(screenTimeout.getText().toString()) / 5) * 5: 5;
        if (screenTimeoutValue > 4 && screenTimeoutValue < 241) {
            pump.lcdOnTimeSec = screenTimeoutValue;
        } else {
            pump.lcdOnTimeSec = 5;
        }
        int backlightTimeoutValue = !backlightTimeout.getText().isEmpty() ? Integer.parseInt(backlightTimeout.getText().toString()): 1;
        if (backlightTimeoutValue > 0 && backlightTimeoutValue < 61) {
            pump.backlightOnTimeSec = backlightTimeoutValue;
        }
        if (pumpUnits.isChecked()) {
            pump.units = 1;
        } else {
            pump.units = 0;
        }
        int shutDownValue = !shutdown.getText().isEmpty() ? Integer.parseInt(shutdown.getText().toString()) : 0;
        if (shutDownValue > -1 && shutDownValue < 25) {
            pump.shutdownHour = shutDownValue;
        } else {
            pump.shutdownHour = 0;
        }
        int lowReservoirValue = !lowReservoir.getText().isEmpty() ? (Integer.parseInt(lowReservoir.getText().toString()) * 10) / 10 : 10;
        if (lowReservoirValue > 9 && lowReservoirValue < 51) {
            pump.lowReservoirRate = lowReservoirValue;
        } else
            pump.lowReservoirRate = 10;

        MainApp.getConfigBuilder().getCommandQueue().setUserOptions(null);
        finish();
    }

}
