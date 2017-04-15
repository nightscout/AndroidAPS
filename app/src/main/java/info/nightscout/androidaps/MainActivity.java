package info.nightscout.androidaps;

import android.Manifest;
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
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.tabs.SlidingTabLayout;
import info.nightscout.androidaps.tabs.TabPageAdapter;
import info.nightscout.utils.ImportExportPrefs;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.LogDialog;
import info.nightscout.utils.OKDialog;
import info.nightscout.utils.PasswordProtection;
import info.nightscout.utils.SP;
import info.nightscout.utils.ToastUtils;

public class MainActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    static final int CASE_STORAGE = 0x1;
    static final int CASE_SMS = 0x2;

    private boolean askForSMS = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Iconify.with(new FontAwesomeModule());
        LocaleHelper.onCreate(this, "en");
        setContentView(R.layout.activity_main);
        checkEula();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            askForPermission(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, CASE_STORAGE);
        }
        askForBatteryOptimizationPermission();
        if (Config.logFunctionCalls)
            log.debug("onCreate");

        // show version in toolbar
        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        registerBus();

        try {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            if (BuildConfig.NSCLIENTOLNY)
                getSupportActionBar().setIcon(R.mipmap.yellowowl);
            else
                getSupportActionBar().setIcon(R.mipmap.blueowl);
        } catch (NullPointerException e) {
            // no action
        }


        setUpTabs(false);
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        String lang = SP.getString("language", "en");
        LocaleHelper.setLocale(getApplicationContext(), lang);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recreate();
                try { // activity may be destroyed
                    setUpTabs(ev.isSwitchToLast());
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.nav_preferences:
                PasswordProtection.QueryPassword(this, R.string.settings_password, "settings_password", new Runnable() {
                    @Override
                    public void run() {
                        Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
                        startActivity(i);
                    }
                }, null);
                break;
            case R.id.nav_resetdb:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nav_resetdb)
                        .setMessage(R.string.reset_db_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MainApp.getDbHelper().resetDatabases();
                            }
                        })
                        .create()
                        .show();
                break;
            case R.id.nav_export:
                ImportExportPrefs.verifyStoragePermissions(this);
                ImportExportPrefs.exportSharedPreferences(this);
                break;
            case R.id.nav_import:
                ImportExportPrefs.verifyStoragePermissions(this);
                ImportExportPrefs.importSharedPreferences(this);
                break;
            case R.id.nav_show_logcat:
                LogDialog.showLogcat(this);
                break;
//            case R.id.nav_test_alarm:
//                final int REQUEST_CODE_ASK_PERMISSIONS = 2355;
//                int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW);
//                if (permission != PackageManager.PERMISSION_GRANTED) {
//                    // We don't have permission so prompt the user
//                    // On Android 6 give permission for alarming in Settings -> Apps -> Draw over other apps
//                    ActivityCompat.requestPermissions(
//                            this,
//                            new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW},
//                            REQUEST_CODE_ASK_PERMISSIONS
//                    );
//                }
//                Intent alertServiceIntent = new Intent(getApplicationContext(), AlertService.class);
//                alertServiceIntent.putExtra("alertText", getString(R.string.nav_test_alert));
//                getApplicationContext().startService(alertServiceIntent);
//                break;
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
        return super.onOptionsItemSelected(item);
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
    }

    private void askForBatteryOptimizationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();

            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                log.debug("Requesting ignore battery optimization");

                OKDialog.show(this, getString(R.string.pleaseallowpermission), String.format(getString(R.string.needwhitelisting), getString(R.string.app_name)), new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final Intent intent = new Intent();

                            // ignoring battery optimizations required for constant connection
                            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + packageName));
                            startActivity(intent);

                        } catch (ActivityNotFoundException e) {
                            final String msg = getString(R.string.batteryoptimalizationerror);
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
}
