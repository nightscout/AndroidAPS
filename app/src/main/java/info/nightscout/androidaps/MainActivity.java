package info.nightscout.androidaps;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;
import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService;
import info.nightscout.androidaps.receivers.KeepAliveReceiver;
import info.nightscout.androidaps.tabs.SlidingTabLayout;
import info.nightscout.androidaps.tabs.TabPageAdapter;
import info.nightscout.utils.ImportExportPrefs;
import info.nightscout.utils.LocaleHelper;

public class MainActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    private static KeepAliveReceiver keepAliveReceiver;

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
        if (Config.logFunctionCalls)
            log.debug("onCreate");

        // show version in toolbar
        try {
            setTitle(getString(R.string.app_name) + " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        registerBus();

        try {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.mipmap.ic_launcher);
        } catch (NullPointerException e) {
            // no action
        }

        if (keepAliveReceiver == null) {
            keepAliveReceiver = new KeepAliveReceiver();
            startService(new Intent(this, ExecutionService.class));
            keepAliveReceiver.setAlarm(this);
        }
        setUpTabs(false);
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String lang = SP.getString("language", "en");
        LocaleHelper.setLocale(getApplicationContext(), lang);
        recreate();
        try { // activity may be destroyed
            setUpTabs(ev.isSwitchToLast());
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
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
                Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
                startActivity(i);
                break;
            case R.id.nav_resetdb:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.nav_resetdb)
                        .setMessage(R.string.reset_db_confirm)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
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
                keepAliveReceiver.cancelAlarm(this);

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
        boolean IUnderstand = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("I_understand", false);
        if (!IUnderstand) {
            Intent intent = new Intent(getApplicationContext(), AgreementActivity.class);
            startActivity(intent);
            finish();
        }
    }

    //check for sms permission if enable in prefernces
    @Subscribe
    public void onStatusEvent(final EventPreferenceChange ev) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            SharedPreferences smssettings = PreferenceManager.getDefaultSharedPreferences(this);
            synchronized (this){
                if (smssettings.getBoolean("smscommunicator_remotecommandsallowed", false)) {
                    setAskForSMS();
                }
            }
        }
    }

    private synchronized void setAskForSMS() {
        askForSMS = true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        askForSMSPermissions();
    }

    private synchronized void askForSMSPermissions(){
        if (askForSMS) { //only when settings were changed an MainActivity resumes.
            askForSMS = false;
            SharedPreferences smssettings = PreferenceManager.getDefaultSharedPreferences(this);
            if (smssettings.getBoolean("smscommunicator_remotecommandsallowed", false)) {
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
        for (int i=0; i < permission.length; i++) {
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
}
