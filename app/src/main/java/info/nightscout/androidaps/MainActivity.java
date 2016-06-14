package info.nightscout.androidaps;

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

import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.LowSuspend.LowSuspendFragment;
import info.nightscout.androidaps.plugins.NSProfileViewer.NSProfileViewerFragment;
import info.nightscout.androidaps.plugins.OpenAPSMA.OpenAPSMAFragment;
import info.nightscout.androidaps.plugins.Overview.OverviewFragment;
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

    ArrayList<Fragment> pluginsList = new ArrayList<Fragment>();

    public static TreatmentsFragment treatmentsFragment;
    public static TempBasalsFragment tempBasalsFragment;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register all tabs in app here
        pluginsList.add(OverviewFragment.newInstance());
        pluginsList.add((VirtualPumpFragment) MainApp.setActivePump(VirtualPumpFragment.newInstance()));
        pluginsList.add(LowSuspendFragment.newInstance());
        pluginsList.add(OpenAPSMAFragment.newInstance());
        pluginsList.add(treatmentsFragment = TreatmentsFragment.newInstance());
        pluginsList.add(tempBasalsFragment = TempBasalsFragment.newInstance());
        pluginsList.add(NSProfileViewerFragment.newInstance());
        pluginsList.add(ObjectivesFragment.newInstance());
        pluginsList.add(ConfigBuilderFragment.newInstance());

/*
        pageAdapter.registerNewFragment(OverviewFragment.newInstance());
        pageAdapter.registerNewFragment((VirtualPumpFragment) MainApp.setActivePump(VirtualPumpFragment.newInstance()));
        pageAdapter.registerNewFragment(LowSuspendFragment.newInstance());
        pageAdapter.registerNewFragment(OpenAPSMAFragment.newInstance());
        pageAdapter.registerNewFragment(treatmentsFragment = TreatmentsFragment.newInstance());
        pageAdapter.registerNewFragment(tempBasalsFragment = TempBasalsFragment.newInstance());
        pageAdapter.registerNewFragment(NSProfileViewerFragment.newInstance());
        pageAdapter.registerNewFragment(ObjectivesFragment.newInstance());
        pageAdapter.registerNewFragment(ConfigBuilderFragment.newInstance());
*/
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        registerBus();

        setUpTabs(false);
    }

    @Subscribe
    public void onStatusEvent(final EventRefreshGui ev) {
        setUpTabs(true);
    }

    private void setUpTabs(boolean switchToLast) {
        pageAdapter = new TabPageAdapter(getSupportFragmentManager());
        for(Fragment f: pluginsList) {
            pageAdapter.registerNewFragment(f);
        }
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(pageAdapter);
        mTabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mTabs.setViewPager(mPager);
        if (switchToLast)
            mPager.setCurrentItem(pageAdapter.getCount()-1, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

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

    public static TabPageAdapter getPageAdapter() {
        return pageAdapter;
    }
}
