package info.nightscout.androidaps.plugins.PumpDanaR.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.Spanned;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.DanaRKoreanPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRS.DanaRSPlugin;

/**
 * Created by Rumen Georgiev on 5/31/2018.
 */

public class DanaRUserOptionsActivity extends Activity {
    private static Logger log = LoggerFactory.getLogger(DanaRUserOptionsActivity.class);

    private Handler mHandler;
    private static HandlerThread mHandlerThread;
    LinearLayoutManager llm;
    RecyclerView recyclerView;

    Switch timeFormat;
    Switch buttonScroll;
    Switch beep;
    RadioGroup pumpAlarm;
    RadioButton pumpAlarmSound;
    RadioButton pumpAlarmVibrate;
    RadioButton pumpAlarmBoth;
    Switch pumpUnits;
    EditText screenTimeout;
    EditText backlightTimeout;
    EditText shutdown;
    EditText lowReservoir;


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
        screenTimeout = (EditText) findViewById(R.id.danar_screentimeout);
        backlightTimeout = (EditText) findViewById(R.id.danar_backlight);
        pumpUnits = (Switch) findViewById(R.id.danar_units);
        shutdown = (EditText) findViewById(R.id.danar_shutdown);
        lowReservoir = (EditText) findViewById(R.id.danar_lowreservoir);

        boolean isKorean = MainApp.getSpecificPlugin(DanaRKoreanPlugin.class) != null && MainApp.getSpecificPlugin(DanaRKoreanPlugin.class).isEnabled(PluginType.PUMP);
        boolean isRS = MainApp.getSpecificPlugin(DanaRSPlugin.class) != null && MainApp.getSpecificPlugin(DanaRSPlugin.class).isEnabled(PluginType.PUMP);


        Activity activity = this;
        if (activity != null)
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    DanaRPump pump = DanaRPump.getInstance();
                    //used for debugging
                    log.debug("UserOptionsLoadedd:"+(System.currentTimeMillis() - pump.lastConnection)/1000+" s ago"
                            +"\ntimeDisplayType:"+pump.timeDisplayType
                            +"\nbuttonScroll:"+pump.buttonScrollOnOff
                            +"\ntimeDisplayType:"+pump.timeDisplayType
                            +"\nlcdOnTimeSec:"+pump.lcdOnTimeSec
                            +"\nbacklight:"+pump.backlightOnTimeSec
                            +"\npumpUnits:"+pump.units
                            +"\nlowReservoir:"+pump.lowReservoirRate);


                    if (pump.timeDisplayType != 0) {
                        timeFormat.setChecked(false);
                    }

                    if(pump.buttonScrollOnOff != 0) {
                        buttonScroll.setChecked(true);
                    }
                    if (pump.beepAndAlarm != 0) {
                        beep.setChecked(true);
                    }

                    screenTimeout.setText(String.valueOf(pump.lcdOnTimeSec));
                    backlightTimeout.setText(String.valueOf(pump.backlightOnTimeSec));
                    if(pump.lastSettingsRead == 0)
                        backlightTimeout.setText(String.valueOf(666));
                    if (pump.getUnits() != null) {
                        if(pump.getUnits().equals(Constants.MMOL)) {
                            pumpUnits.setChecked(true);
                        }
                    }
                    shutdown.setText(String.valueOf(pump.shutdownHour));
                    lowReservoir.setText(String.valueOf(pump.lowReservoirRate));
                }
            });
    }

}
