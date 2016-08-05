package info.nightscout.androidaps;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
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

    private Toolbar toolbar;
    private SlidingTabLayout mTabs;
    private ViewPager mPager;
    private static TabPageAdapter pageAdapter;
    private static KeepAliveReceiver keepAliveReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Iconify.with(new FontAwesomeModule());
        LocaleHelper.onCreate(this, "en");
        checkEula();
        setContentView(R.layout.activity_main);
        if (Config.logFunctionCalls)
            log.debug("onCreate");

        // show version in toolbar
        try {
            setTitle(getString(R.string.app_name) + " v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        registerBus();

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
            setUpTabs(true);
        } catch (IllegalStateException e) {
        }
    }

    private void setUpTabs(boolean switchToLast) {
        pageAdapter = new TabPageAdapter(getSupportFragmentManager(), this);
        for (PluginBase p : MainApp.getPluginsList()) {
            pageAdapter.registerNewFragment(p);
        }
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(pageAdapter);
        mTabs = (SlidingTabLayout) findViewById(R.id.tabs);
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
                MainApp.getDbHelper().resetDatabases();
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

}
