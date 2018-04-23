package info.nightscout.androidaps.startupwizard;

import android.annotation.SuppressLint;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.List;

import info.nightscout.androidaps.R;

import static info.nightscout.androidaps.startupwizard.SWItem.Type.RADIOBUTTON;
import static info.nightscout.androidaps.startupwizard.SWItem.Type.STRING;
import static info.nightscout.androidaps.startupwizard.SWItem.Type.URL;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class SetupWizardActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private LinearLayout linearLayout;
    private TextView radioLabel;
    private RadioGroup radioGroup;
    private RadioButton[] radioButtons;
    private RadioButton radioButton1;
    private RadioButton radioButton2;
    private RadioButton radioButton3;
    private RadioButton radioButton4;
    private RadioButton radioButton5;
    private TextView screenName;
    private TextView label1;
    private EditText editText1;
    private TextView label2;
    private EditText editText2;
    private Button skipButton;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_setupwizard);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });


        SWDefinition swDefinition = SWDefinition.getInstance();
        List<SWScreen> screens = swDefinition.getScreens();
        if(screens.size() > 0){
            SWScreen currentScreen = screens.get(1);
            //Set screen name
            screenName = (TextView) findViewById(R.id.fullscreen_content);
            screenName.setText(currentScreen.getHeader());
            //Display screen items in the order entered
            linearLayout = (LinearLayout) findViewById(R.id.fullscreen_content_controls);
            // is it skippable ?
            if(currentScreen.skippable) {
                //display skip button
                skipButton = (Button) findViewById(R.id.skip_button);

            }
            for(int i = 0; i < currentScreen.items.size(); i++){
                SWItem currentItem = currentScreen.items.get(i);

                if(currentItem.type == URL){
                    label1 = (TextView) findViewById(R.id.textLabel1);
                    editText1 = (EditText) findViewById(R.id.editText1);
                    label1.setText(currentItem.getLabel());
                    label1.setVisibility(View.VISIBLE);
                    editText1.setText(currentItem.getComment());
                    editText1.setVisibility(View.VISIBLE);
                } else if(currentItem.type == STRING){
                    label2 = (TextView) findViewById(R.id.textLabel2);
                    editText2 = (EditText) findViewById(R.id.editText2);
                    label2.setText(currentItem.getLabel());
                    label2.setVisibility(View.VISIBLE);
                    editText2.setText(currentItem.getComment());
                    editText2.setVisibility(View.VISIBLE);
                } else if(currentItem.type == RADIOBUTTON){
                    ((LinearLayout) findViewById(R.id.radio_group_layout)).setVisibility(View.VISIBLE);
                    radioLabel = (TextView) findViewById(R.id.radio_group_label);
                    radioLabel.setText(currentScreen.getHeader());
                    SWRadioButton radioGroupItems = (SWRadioButton) currentItem;
                    addRadioButtons(radioGroupItems.labels().length, radioGroupItems.labels(), radioGroupItems.values());
                }

            }


        }

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.next_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void addRadioButtons(int number, String[] labels, String[] values) {

        for (int row = 0; row < 1; row++) {
            RadioGroup ll = new RadioGroup(this);
            ll.setOrientation(LinearLayout.VERTICAL);

            for (int i = 0; i < number; i++) {
                RadioButton rdbtn = new RadioButton(this);
                rdbtn.setId((row * 2) + i);
//                rdbtn.setText("Radio " + rdbtn.getId());
                rdbtn.setText(labels[i]);
                ll.addView(rdbtn);
            }
            ((RadioGroup) findViewById(R.id.radiogroup)).addView(ll);
            ((RadioGroup) findViewById(R.id.radiogroup)).setVisibility(View.VISIBLE);
        }

    }
}
