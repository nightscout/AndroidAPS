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

import javax.inject.Inject;

import dagger.android.AndroidInjection;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.activities.PreferencesActivity;
import info.nightscout.androidaps.activities.SingleFragmentActivity;
import info.nightscout.androidaps.activities.StatsActivity;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRebuildTabs;
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.constraints.versionChecker.VersionCheckerUtils;
import info.nightscout.androidaps.plugins.general.nsclient.data.NSSettingsStatus;
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin;
import info.nightscout.androidaps.setupwizard.SetupWizardActivity;
import info.nightscout.androidaps.tabs.TabPageAdapter;
import info.nightscout.androidaps.utils.AndroidPermission;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.LocaleHelper;
import info.nightscout.androidaps.utils.OKDialog;
import info.nightscout.androidaps.utils.buildHelper.BuildHelper;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import info.nightscout.androidaps.utils.protection.ProtectionCheck;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

import static info.nightscout.androidaps.utils.extensions.EspressoTestHelperKt.isRunningRealPumpTest;

public class MainActivity extends NoSplashAppCompatActivity {

    private CompositeDisposable disposable = new CompositeDisposable();
    private ActionBarDrawerToggle actionBarDrawerToggle;
    private MenuItem pluginPreferencesMenuItem;

    @Inject AAPSLogger aapsLogger;
    @Inject RxBusWrapper rxBus;
    @Inject AndroidPermission androidPermission;
    @Inject SP sp;
    @Inject ResourceHelper resourceHelper;
    @Inject VersionCheckerUtils versionCheckerUtils;
    @Inject SmsCommunicatorPlugin smsCommunicatorPlugin;
    @Inject LoopPlugin loopPlugin;
    @Inject NSSettingsStatus nsSettingsStatus;
    @Inject BuildHelper buildHelper;
    @Inject ActivePluginProvider activePlugin;
    @Inject FabricPrivacy fabricPrivacy;
    @Inject ProtectionCheck protectionCheck;


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
        processPreferenceChange(new EventPreferenceChange(resourceHelper.gs(R.string.key_keep_screen_on)));

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
        if (!loopPlugin.isEnabled(PluginType.LOOP))
            versionCheckerUtils.triggerCheckVersion();

        fabricPrivacy.setUserStats();

        setupTabs();
        setupViews();

        disposable.add(rxBus
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
                }, exception -> fabricPrivacy.logException(exception))
        );
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::processPreferenceChange, exception -> fabricPrivacy.logException(exception))
        );

        if (!sp.getBoolean(R.string.key_setupwizard_processed, false) && !isRunningRealPumpTest()) {
            Intent intent = new Intent(this, SetupWizardActivity.class);
            startActivity(intent);
        }

        androidPermission.notifyForStoragePermission(this);
        androidPermission.notifyForBatteryOptimizationPermission(this);
        if (Config.PUMPDRIVERS) {
            androidPermission.notifyForLocationPermissions(this);
            androidPermission.notifyForSMSPermissions(this, smsCommunicatorPlugin);
            androidPermission.notifyForSystemWindowPermissions(this);
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

    @Override
    protected void onResume() {
        super.onResume();
        protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, null, this::finish, this::finish);
    }

    private void setWakeLock() {
        boolean keepScreenOn = sp.getBoolean(R.string.key_keep_screen_on, false);
        if (keepScreenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void processPreferenceChange(final EventPreferenceChange ev) {
        if (ev.isChanged(resourceHelper, R.string.key_keep_screen_on))
            setWakeLock();
    }

    private void setupViews() {
        TabPageAdapter pageAdapter = new TabPageAdapter(getSupportFragmentManager(), this);
        NavigationView navigationView = findViewById(R.id.navigation_view);
        navigationView.setNavigationItemSelectedListener(menuItem -> true);
        Menu menu = navigationView.getMenu();
        menu.clear();
        for (PluginBase p : activePlugin.getPluginsList()) {
            pageAdapter.registerNewFragment(p);
            if (p.hasFragment() && !p.isFragmentVisible() && p.isEnabled(p.getPluginDescription().getType()) && !p.getPluginDescription().neverVisible) {
                MenuItem menuItem = menu.add(p.getName());
                menuItem.setCheckable(true);
                menuItem.setOnMenuItemClickListener(item -> {
                    Intent intent = new Intent(this, SingleFragmentActivity.class);
                    intent.putExtra("plugin", activePlugin.getPluginsList().indexOf(p));
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
        if (sp.getBoolean("short_tabtitles", false)) {
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions.length != 0) {
            if (ActivityCompat.checkSelfPermission(this, permissions[0]) == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case AndroidPermission.CASE_STORAGE:
                        //show dialog after permission is granted
                        OKDialog.show(this, "", resourceHelper.gs(R.string.alert_dialog_storage_permission_text));
                        break;
                    case AndroidPermission.CASE_LOCATION:
                    case AndroidPermission.CASE_SMS:
                    case AndroidPermission.CASE_BATTERY:
                    case AndroidPermission.CASE_PHONE_STATE:
                    case AndroidPermission.CASE_SYSTEM_WINDOW:
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
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, () -> {
                    Intent i = new Intent(this, PreferencesActivity.class);
                    i.putExtra("id", -1);
                    startActivity(i);
                });
                return true;
            case R.id.nav_historybrowser:
                startActivity(new Intent(this, HistoryBrowseActivity.class));
                return true;
            case R.id.nav_setupwizard:
                startActivity(new Intent(this, SetupWizardActivity.class));
                return true;
            case R.id.nav_about:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(resourceHelper.gs(R.string.app_name) + " " + BuildConfig.VERSION);
                builder.setIcon(resourceHelper.getIcon());
                String message = "Build: " + BuildConfig.BUILDVERSION + "\n";
                message += "Flavor: " + BuildConfig.FLAVOR + BuildConfig.BUILD_TYPE + "\n";
                message += resourceHelper.gs(R.string.configbuilder_nightscoutversion_label) + " " + nsSettingsStatus.getNightscoutVersionName();
                if (buildHelper.isEngineeringMode())
                    message += "\n" + resourceHelper.gs(R.string.engineering_mode_enabled);
                message += resourceHelper.gs(R.string.about_link_urls);
                final SpannableString messageSpanned = new SpannableString(message);
                Linkify.addLinks(messageSpanned, Linkify.WEB_URLS);
                builder.setMessage(messageSpanned);
                builder.setPositiveButton(resourceHelper.gs(R.string.ok), null);
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                ((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                return true;
            case R.id.nav_exit:
                aapsLogger.debug(LTag.CORE, "Exiting");
                rxBus.send(new EventAppExit());
                finish();
                System.runFinalization();
                System.exit(0);
                return true;
            case R.id.nav_plugin_preferences:
                ViewPager viewPager = findViewById(R.id.pager);
                final PluginBase plugin = ((TabPageAdapter) viewPager.getAdapter()).getPluginAt(viewPager.getCurrentItem());
                protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, () -> {
                    Intent i = new Intent(this, PreferencesActivity.class);
                    i.putExtra("id", plugin.getPreferencesId());
                    startActivity(i);
                });
                return true;
/*
            case R.id.nav_survey:
                startActivity(new Intent(this, SurveyActivity.class));
                return true;
*/
            case R.id.nav_stats:
                startActivity(new Intent(this, StatsActivity.class));
                return true;
        }
        return actionBarDrawerToggle.onOptionsItemSelected(item);
    }

}
