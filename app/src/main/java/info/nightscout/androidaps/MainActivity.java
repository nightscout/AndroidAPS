package info.nightscout.androidaps;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventFeatureRunning;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Food.FoodPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventSetWakeLock;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.androidaps.tabs.SlidingTabLayout;
import info.nightscout.androidaps.tabs.TabPageAdapter;
import info.nightscout.utils.ImportExportPrefs;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.LogDialog;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.PasswordProtection;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    static final int CASE_STORAGE = 0x1;
    static final int CASE_SMS = 0x2;
    static final int CASE_LOCATION = 0x3;

    private boolean askForSMS = false;
    private boolean askForLocation = true;

    ImageButton menuButton;

    protected PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Iconify.with(new FontAwesomeModule());
        LocaleHelper.onCreate(this, "en");
        setContentView(R.layout.activity_main);
        menuButton = (ImageButton) findViewById(R.id.overview_menuButton);
        menuButton.setOnClickListener(this);

        checkEula();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            askForPermission(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, CASE_STORAGE);
        }
        askForBatteryOptimizationPermission();
        doMigrations();
        if (Config.logFunctionCalls)
            log.debug("onCreate");

        onStatusEvent(new EventSetWakeLock(SP.getBoolean("lockscreen", false)));

        registerBus();
        setUpTabs(false);
    }

    @Subscribe
    public void onStatusEvent(final EventSetWakeLock ev) {
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (ev.lock) {
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "AAPS");
            if (!mWakeLock.isHeld())
                mWakeLock.acquire();
        } else {
            if (mWakeLock != null && mWakeLock.isHeld())
                mWakeLock.release();
        }
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        String lang = SP.getString("language", "en");
        LocaleHelper.setLocale(getApplicationContext(), lang);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (ev.recreate) {
                    recreate();
                } else {
                    try { // activity may be destroyed
                        setUpTabs(true);
                    } catch (IllegalStateException e) {
                        log.error("Unhandled exception", e);
                    }
                }

                boolean lockScreen = BuildConfig.NSCLIENTOLNY && SP.getBoolean("lockscreen", false);
                if (lockScreen)
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
    }

    private void setUpTabs(boolean switchToLast) {
        TabPageAdapter pageAdapter = new TabPageAdapter(getSupportFragmentManager(), this);
        for (PluginBase p : MainApp.getPluginsList()) {
            pageAdapter.registerNewFragment(p);
        }
        ViewPager mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(pageAdapter);
        SlidingTabLayout mTabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mTabs.setViewPager(mPager);
        if (switchToLast)
            mPager.setCurrentItem(pageAdapter.getCount() - 1, false);
    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }

    private void checkEula() {
        //SP.removeBoolean(R.string.key_i_understand);
        boolean IUnderstand = SP.getBoolean(R.string.key_i_understand, false);
        if (!IUnderstand) {
            Intent intent = new Intent(getApplicationContext(), AgreementActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void doMigrations() {

        checkUpgradeToProfileTarget();

        // guarantee that the unreachable threshold is at least 30 and of type String
        // Added in 1.57 at 21.01.2018
        Integer unreachable_threshold = SP.getInt(R.string.key_pump_unreachable_threshold, 30);
        SP.remove(R.string.key_pump_unreachable_threshold);
        if (unreachable_threshold < 30) unreachable_threshold = 30;
        SP.putString(R.string.key_pump_unreachable_threshold, unreachable_threshold.toString());
    }


    private void checkUpgradeToProfileTarget() { // TODO: can be removed in the future
        boolean oldKeyExists = SP.contains("openapsma_min_bg");
        if (oldKeyExists) {
            Profile profile = MainApp.getConfigBuilder().getProfile();
            String oldRange = SP.getDouble("openapsma_min_bg", 0d) + " - " + SP.getDouble("openapsma_max_bg", 0d);
            String newRange = "";
            if (profile != null) {
                newRange = profile.getTargetLow() + " - " + profile.getTargetHigh();
            }
            String message = "Target range is changed in current version.\n\nIt's not taken from preferences but from profile.\n\n!!! REVIEW YOUR SETTINGS !!!";
            message += "\n\nOld settings: " + oldRange;
            message += "\nProfile settings: " + newRange;
            OKDialog.show(this, "Target range change", message, new Runnable() {
                @Override
                public void run() {
                    SP.remove("openapsma_min_bg");
                    SP.remove("openapsma_max_bg");
                    SP.remove("openapsma_target_bg");
                }
            });
        }
    }

    //check for sms permission if enable in prefernces
    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_smscommunicator_remotecommandsallowed)) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                synchronized (this) {
                    if (SP.getBoolean(R.string.key_smscommunicator_remotecommandsallowed, false)) {
                        setAskForSMS();
                    }
                }
            }
        }
    }

    private synchronized void setAskForSMS() {
        askForSMS = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        askForSMSPermissions();
        askForLocationPermissions();
        MainApp.bus().post(new EventFeatureRunning(EventFeatureRunning.Feature.MAIN));
    }

    @Override
    public void onDestroy() {
        if (mWakeLock != null)
            if (mWakeLock.isHeld())
                mWakeLock.release();
        super.onDestroy();
    }

    private void askForBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();

            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                log.debug("Requesting ignore battery optimization");

                OKDialog.show(this, MainApp.gs(R.string.pleaseallowpermission), String.format(MainApp.gs(R.string.needwhitelisting), MainApp.gs(R.string.app_name)), new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final Intent intent = new Intent();

                            // ignoring battery optimizations required for constant connection
                            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);

                        } catch (ActivityNotFoundException e) {
                            final String msg = MainApp.gs(R.string.batteryoptimalizationerror);
                            ToastUtils.showToastInUiThread(getApplicationContext(), msg);
                            log.error(msg);
                        }
                    }
                });
            }
        }
    }

    private synchronized void askForSMSPermissions() {
        if (askForSMS) { //only when settings were changed an MainActivity resumes.
            askForSMS = false;
            if (SP.getBoolean(R.string.smscommunicator_remotecommandsallowed, false)) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                    askForPermission(new String[]{Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.SEND_SMS,
                            Manifest.permission.RECEIVE_MMS}, CASE_SMS);
                }
            }
        }
    }

    private synchronized void askForLocationPermissions() {
        if (askForLocation) { //only when settings were changed an MainActivity resumes.
            askForLocation = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                askForPermission(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION}, CASE_LOCATION);
            }
        }
    }

    private void askForPermission(String[] permission, Integer requestCode) {
        boolean test = false;
        for (int i = 0; i < permission.length; i++) {
            test = test || (ContextCompat.checkSelfPermission(this, permission[i]) != PackageManager.PERMISSION_GRANTED);
        }
        if (test) {
            ActivityCompat.requestPermissions(this, permission, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length != 0) {
            if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case CASE_STORAGE:
                        //show dialog after permission is granted
                        AlertDialog.Builder alert = new AlertDialog.Builder(this);
                        alert.setMessage(R.string.alert_dialog_storage_permission_text);
                        alert.setPositiveButton(R.string.ok, null);
                        alert.show();
                        break;
                    case CASE_LOCATION:
                    case CASE_SMS:
                        break;
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onClick(final View v) {
        final Activity activity = this;
        switch (v.getId()) {
            case R.id.overview_menuButton:
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                MenuInflater inflater = popup.getMenuInflater();
                inflater.inflate(R.menu.menu_main, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        int id = item.getItemId();
                        switch (id) {
                            case R.id.nav_preferences:
                                PasswordProtection.QueryPassword(v.getContext(), R.string.settings_password, "settings_password", new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent i = new Intent(v.getContext(), PreferencesActivity.class);
                                        i.putExtra("id", -1);
                                        startActivity(i);
                                    }
                                }, null);
                                break;
                            case R.id.nav_historybrowser:
                                startActivity(new Intent(v.getContext(), HistoryBrowseActivity.class));
                                break;
                            case R.id.nav_resetdb:
                                new AlertDialog.Builder(v.getContext())
                                        .setTitle(R.string.nav_resetdb)
                                        .setMessage(R.string.reset_db_confirm)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                MainApp.getDbHelper().resetDatabases();
                                                // should be handled by Plugin-Interface and
                                                // additional service interface and plugin registry
                                                FoodPlugin.getPlugin().getService().resetFood();
                                                TreatmentsPlugin.getPlugin().getService().resetTreatments();
                                            }
                                        })
                                        .create()
                                        .show();
                                break;
                            case R.id.nav_export:
                                ImportExportPrefs.verifyStoragePermissions(activity);
                                ImportExportPrefs.exportSharedPreferences(activity);
                                break;
                            case R.id.nav_import:
                                ImportExportPrefs.verifyStoragePermissions(activity);
                                ImportExportPrefs.importSharedPreferences(activity);
                                break;
                            case R.id.nav_show_logcat:
                                LogDialog.showLogcat(v.getContext());
                                break;
                            case R.id.nav_about:
                                AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
                                builder.setTitle(MainApp.gs(R.string.app_name) + " " + BuildConfig.VERSION);
                                if (Config.NSCLIENT || Config.G5UPLOADER)
                                    builder.setIcon(R.mipmap.yellowowl);
                                else
                                    builder.setIcon(R.mipmap.blueowl);
                                String message = "Build: " + BuildConfig.BUILDVERSION + "\n";
                                message += "Flavor: " + BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE + "\n";
                                message += MainApp.gs(R.string.configbuilder_nightscoutversion_label) + " " + ConfigBuilderPlugin.nightscoutVersionName;
                                if (MainApp.engineeringMode)
                                    message += "\n" + MainApp.gs(R.string.engineering_mode_enabled);
                                message += MainApp.gs(R.string.about_link_urls);
                                final SpannableString messageSpanned =  new SpannableString(message);
                                Linkify.addLinks(messageSpanned, Linkify.WEB_URLS);
                                builder.setMessage(messageSpanned);
                                builder.setPositiveButton(MainApp.gs(R.string.ok), null);
                                AlertDialog alertDialog = builder.create();
                                alertDialog.show();
                                ((TextView)alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                                break;
                            case R.id.nav_exit:
                                log.debug("Exiting");
                                MainApp.instance().stopKeepAliveService();
                                MainApp.bus().post(new EventAppExit());
                                MainApp.closeDbHelper();
                                finish();
                                System.runFinalization();
                                System.exit(0);
                                break;
                        }
                        return false;
                    }
                });
                popup.show();
                break;
        }
    }
}
