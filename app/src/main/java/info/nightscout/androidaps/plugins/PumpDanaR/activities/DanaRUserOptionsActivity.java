package info.nightscout.androidaps.plugins.PumpDanaR.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;

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
        shutdown = (EditText) findViewById(R.id.danar_shutdown);
        lowReservoir = (EditText) findViewById(R.id.danar_lowreservoir);



    }
}
