package info.nightscout.androidaps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.activities.AgreementActivity;
import info.nightscout.androidaps.activities.HistoryBrowseActivity;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.activities.PreferencesActivity;
import info.nightscout.androidaps.activities.SingleFragmentActivity;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtilsKt;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.setupwizard.SetupWizardActivity;
import info.nightscout.androidaps.tabs.TabPageAdapter;
import info.nightscout.androidaps.utils.AndroidPermission;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.PasswordProtection;
import info.nightscout.androidaps.utils.SP;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class MainActivity extends NoSplashAppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(L.CORE);
    private CompositeDisposable disposable = new CompositeDisposable();

    private ActionBarDrawerToggle actionBarDrawerToggle;

    private MenuItem pluginPreferencesMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Iconify.with(new FontAwesomeModule());
        LocaleHelper.INSTANCE.update(getApplicationContext());

        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_navigation, R.string.close_navigation);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        actionBarDrawerToggle.syncState();

        // initialize screen wake lock
        processPreferenceChange(new EventPreferenceChange(R.string.key_keep_screen_on));

        doMigrations();

        final ViewPager viewPager = findViewById(R.id.pager);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                checkPluginPreferences(viewPager);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        //Check here if loop plugin is disabled. Else check via constraints
        if (!LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
            VersionCheckerUtilsKt.triggerCheckVersion();

        FabricPrivacy.setUserStats();

        setupTabs();
        setupViews();

        disposable.add(RxBus.INSTANCE
                .toObservable(EventRebuildTabs.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    LocaleHelper.INSTANCE.update(getApplicationContext());
                    if (event.getRecreate()) {
                        recreate();
                    } else {
                        setupTabs();
                        setupViews();
                    }
                    setWakeLock();
                }, FabricPrivacy::logException)
        );
        disposable.add(RxBus.INSTANCE
                .toObservable(EventPreferenceChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processPreferenceChange, FabricPrivacy::logException)
        );

        if (!SP.getBoolean(R.string.key_setupwizard_processed, false)) {
            Intent intent = new Intent(this, SetupWizardActivity.class);
            startActivity(intent);
        } else {
            checkEula();
        }

        AndroidPermission.notifyForStoragePermission(this);
        AndroidPermission.notifyForBatteryOptimizationPermission(this);
        if (Config.PUMPDRIVERS) {
            AndroidPermission.notifyForLocationPermissions(this);
            AndroidPermission.notifyForSMSPermissions(this);
        }
    }

    private void checkPluginPreferences(ViewPager viewPager) {
        if (pluginPreferencesMenuItem == null) return;
        if (((TabPageAdapter) viewPager.getAdapter()).getPluginAt(viewPager.getCurrentItem()).getPreferencesId() != -1)
            pluginPreferencesMenuItem.setEnabled(true);
        else pluginPreferencesMenuItem.setEnabled(false);
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onPostCreate(savedInstanceState, persistentState);
        actionBarDrawerToggle.syncState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposable.clear();
    }

    private void setWakeLock() {
        boolean keepScreenOn = SP.getBoolean(R.string.key_keep_screen_on, false);
        if (keepScreenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void processPreferenceChange(final EventPreferenceChange ev) {
        if (ev.isChanged(R.string.key_keep_screen_on))
            setWakeLock();
    }

    private void setupViews() {
        TabPageAdapter pageAdapter = new TabPageAdapter(getSupportFragmentManager(), this);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(menuItem -> true);
        Menu menu = navigationView.getMenu();
        menu.clear();
        for (PluginBase p : MainApp.getPluginsList()) {
            pageAdapter.registerNewFragment(p);
            if (p.hasFragment() && !p.isFragmentVisible() && p.isEnabled(p.pluginDescription.getType()) && !p.pluginDescription.neverVisible) {
                MenuItem menuItem = menu.add(p.getName());
                menuItem.setCheckable(true);
                menuItem.setOnMenuItemClickListener(item -> {
                    Intent intent = new Intent(this, SingleFragmentActivity.class);
                    intent.putExtra("plugin", MainApp.getPluginsList().indexOf(p));
                    startActivity(intent);
                    ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawers();
                    return true;
                });
            }
        }
        ViewPager mPager = findViewById(R.id.pager);
        mPager.setAdapter(pageAdapter);
        //if (switchToLast)
        //    mPager.setCurrentItem(pageAdapter.getCount() - 1, false);
        checkPluginPreferences(mPager);
    }

    private void setupTabs() {
        ViewPager viewPager = findViewById(R.id.pager);
        TabLayout normalTabs = findViewById(R.id.tabs_normal);
        normalTabs.setupWithViewPager(viewPager, true);
        TabLayout compactTabs = findViewById(R.id.tabs_compact);
        compactTabs.setupWithViewPager(viewPager, true);
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (SP.getBoolean("short_tabtitles", false)) {
            normalTabs.setVisibility(View.GONE);
            compactTabs.setVisibility(View.VISIBLE);
            toolbar.setLayoutParams(new LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, (int) getResources().getDimension(R.dimen.compact_height)));
        } else {
            normalTabs.setVisibility(View.VISIBLE);
            compactTabs.setVisibility(View.GONE);
            TypedValue typedValue = new TypedValue();
            if (getTheme().resolveAttribute(R.attr.actionBarSize, typedValue, true)) {
                toolbar.setLayoutParams(new LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT,
                        TypedValue.complexToDimensionPixelSize(typedValue.data, getResources().getDisplayMetrics())));
            }
        }
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
        int unreachable_threshold = SP.getInt(R.string.key_pump_unreachable_threshold, 30);
        SP.remove(R.string.key_pump_unreachable_threshold);
        if (unreachable_threshold < 30) unreachable_threshold = 30;
        SP.putString(R.string.key_pump_unreachable_threshold, Integer.toString(unreachable_threshold));
    }


    private void checkUpgradeToProfileTarget() { // TODO: can be removed in the future
        boolean oldKeyExists = SP.contains("openapsma_min_bg");
        if (oldKeyExists) {
            Profile profile = ProfileFunctions.getInstance().getProfile();
            String oldRange = SP.getDouble("openapsma_min_bg", 0d) + " - " + SP.getDouble("openapsma_max_bg", 0d);
            String newRange = "";
            if (profile != null) {
                newRange = profile.getTargetLow() + " - " + profile.getTargetHigh();
            }
            String message = "Target range is changed in current version.\n\nIt's not taken from preferences but from profile.\n\n!!! REVIEW YOUR SETTINGS !!!";
            message += "\n\nOld settings: " + oldRange;
            message += "\nProfile settings: " + newRange;
            OKDialog.show(this, "Target range change", message, () -> {
                SP.remove("openapsma_min_bg");
                SP.remove("openapsma_max_bg");
                SP.remove("openapsma_target_bg");
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length != 0) {
            if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case AndroidPermission.CASE_STORAGE:
                        //show dialog after permission is granted
                        AlertDialog.Builder alert = new AlertDialog.Builder(this);
                        alert.setMessage(R.string.alert_dialog_storage_permission_text);
                        alert.setPositiveButton(R.string.ok, null);
                        alert.show();
                        break;
                    case AndroidPermission.CASE_LOCATION:
                    case AndroidPermission.CASE_SMS:
                    case AndroidPermission.CASE_BATTERY:
                    case AndroidPermission.CASE_PHONE_STATE:
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences);
        checkPluginPreferences(findViewById(R.id.pager));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.nav_preferences:
                PasswordProtection.QueryPassword(this, R.string.settings_password, "settings_password", () -> {
                    Intent i = new Intent(this, PreferencesActivity.class);
                    i.putExtra("id", -1);
                    startActivity(i);
                }, null);
                return true;
            case R.id.nav_historybrowser:
                startActivity(new Intent(this, HistoryBrowseActivity.class));
                return true;
            case R.id.nav_setupwizard:
                startActivity(new Intent(this, SetupWizardActivity.class));
                return true;
            case R.id.nav_about:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(MainApp.gs(R.string.app_name) + " " + BuildConfig.VERSION);
                builder.setIcon(MainApp.getIcon());
                String message = "Build: " + BuildConfig.BUILDVERSION + "\n";
                message += "Flavor: " + BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE + "\n";
                message += MainApp.gs(R.string.configbuilder_nightscoutversion_label) + " " + NSSettingsStatus.getInstance().nightscoutVersionName;
                if (MainApp.engineeringMode)
                    message += "\n" + MainApp.gs(R.string.engineering_mode_enabled);
                message += MainApp.gs(R.string.about_link_urls);
                final SpannableString messageSpanned = new SpannableString(message);
                Linkify.addLinks(messageSpanned, Linkify.WEB_URLS);
                builder.setMessage(messageSpanned);
                builder.setPositiveButton(MainApp.gs(R.string.ok), null);
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                ((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                return true;
            case R.id.nav_exit:
                log.debug("Exiting");
                MainApp.instance().stopKeepAliveService();
                RxBus.INSTANCE.send(new EventAppExit());
                MainApp.closeDbHelper();
                finish();
                System.runFinalization();
                System.exit(0);
                return true;
            case R.id.nav_plugin_preferences:
                ViewPager viewPager = findViewById(R.id.pager);
                final PluginBase plugin = ((TabPageAdapter) viewPager.getAdapter()).getPluginAt(viewPager.getCurrentItem());
                PasswordProtection.QueryPassword(this, R.string.settings_password, "settings_password", () -> {
                    Intent i = new Intent(this, PreferencesActivity.class);
                    i.putExtra("id", plugin.getPreferencesId());
                    startActivity(i);
                }, null);
                return true;
        }
        return actionBarDrawerToggle.onOptionsItemSelected(item);
    }
}
