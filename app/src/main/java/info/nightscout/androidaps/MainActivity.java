package info.nightscout.androidaps;

import android.content.Intent;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.squareup.otto.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;

import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.Loop.LoopFragment;
import info.nightscout.androidaps.plugins.LowSuspend.LowSuspendFragment;
import info.nightscout.androidaps.plugins.NSProfileViewer.NSProfileViewerFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
import info.nightscout.androidaps.plugins.SafetyFragment.SafetyFragment;
import info.nightscout.androidaps.plugins.SimpleProfile.SimpleProfileFragment;
import info.nightscout.androidaps.plugins.SourceNSClient.SourceNSClientFragment;
import info.nightscout.androidaps.plugins.SourceXdrip.SourceXdripFragment;
import info.nightscout.androidaps.plugins.TempBasals.TempBasalsFragment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.androidaps.plugins.VirtualPump.VirtualPumpFragment;
import info.nightscout.androidaps.tabs.*;
import info.nightscout.androidaps.plugins.Objectives.ObjectivesFragment;

public class MainActivity extends AppCompatActivity {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    private Toolbar toolbar;
    private SlidingTabLayout mTabs;
    private ViewPager mPager;
    private static TabPageAdapter pageAdapter;

    private static ArrayList<PluginBase> pluginsList = null;

    private static ConfigBuilderFragment configBuilderFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Config.logFunctionCalls)
            log.debug("onCreate");

        if (pluginsList == null) {
            pluginsList = new ArrayList<PluginBase>();
            // Register all tabs in app here
            pluginsList.add(OverviewFragment.newInstance());
            pluginsList.add(VirtualPumpFragment.newInstance());
            if (Config.LOOPENABLED) pluginsList.add(LoopFragment.newInstance());
            if (Config.LOWSUSPEDENABLED) pluginsList.add(LowSuspendFragment.newInstance());
            if (Config.OPENAPSMAENABLED) pluginsList.add(OpenAPSMAFragment.newInstance());
            pluginsList.add(NSProfileViewerFragment.newInstance());
            pluginsList.add(SimpleProfileFragment.newInstance());
            pluginsList.add(TreatmentsFragment.newInstance());
            pluginsList.add(TempBasalsFragment.newInstance());
            pluginsList.add(SafetyFragment.newInstance());
            if (Config.OPENAPSMAENABLED) pluginsList.add(ObjectivesFragment.newInstance());
            pluginsList.add(SourceXdripFragment.newInstance());
            pluginsList.add(SourceNSClientFragment.newInstance());
            pluginsList.add(configBuilderFragment = ConfigBuilderFragment.newInstance());
            toolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            registerBus();

            configBuilderFragment.initialize();
            MainApp.setConfigBuilder(configBuilderFragment);
        }
        setUpTabs(false);
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        setUpTabs(true);
    }

    private void setUpTabs(boolean switchToLast) {
        pageAdapter = new TabPageAdapter(getSupportFragmentManager());
        for (PluginBase f : pluginsList) {
            pageAdapter.registerNewFragment((Fragment) f);
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
            case R.id.nav_preferences: {
                Intent i = new Intent(getApplicationContext(), PreferencesActivity.class);
                startActivity(i);
                break;
            }
            case R.id.nav_resetdb:
                MainApp.getDbHelper().resetDatabases();
                break;
            case R.id.nav_exit:
                log.debug("Exiting");
                //chancelAlarmManager();

                //MainApp.bus().post(new StopEvent());
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

    public static ArrayList<PluginBase> getPluginsList() {
        return pluginsList;
    }

    public static ArrayList<PluginBase> getSpecificPluginsList(int type) {
        ArrayList<PluginBase> newList = new ArrayList<PluginBase>();

        Iterator<PluginBase> it = pluginsList.iterator();
        while (it.hasNext()) {
            PluginBase p = it.next();
            if (p.getType() == type)
                newList.add(p);
        }
        return newList;
    }

    public static PluginBase getSpecificPlugin(Class pluginClass) {
        Iterator<PluginBase> it = pluginsList.iterator();
        while (it.hasNext()) {
            PluginBase p = it.next();
            if (p.getClass() == pluginClass)
               return p;
        }
        return null;
    }
}
