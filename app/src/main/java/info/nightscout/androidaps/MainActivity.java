package info.nightscout.androidaps;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import info.nightscout.androidaps.plugins.ProfileViewer.ProfileViewerFragment;
import info.nightscout.androidaps.plugins.TempBasals.TempBasalsFragment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsFragment;
import info.nightscout.androidaps.tabs.*;
import info.nightscout.androidaps.plugins.Objectives.ObjectivesFragment;
import info.nightscout.androidaps.plugins.Test.TestFragment;
import info.nightscout.client.broadcasts.Intents;

public class MainActivity extends AppCompatActivity
        implements ObjectivesFragment.OnFragmentInteractionListener,
                   TreatmentsFragment.OnFragmentInteractionListener,
                   TempBasalsFragment.OnFragmentInteractionListener {
    private static Logger log = LoggerFactory.getLogger(MainActivity.class);

    private Toolbar toolbar;
    private SlidingTabLayout mTabs;
    private ViewPager mPager;
    private TabPageAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register all tabs in app here
        mAdapter = new TabPageAdapter(getSupportFragmentManager());
        mAdapter.registerNewFragment("Test", TestFragment.newInstance());
        mAdapter.registerNewFragment("Treatments", TreatmentsFragment.newInstance());
        mAdapter.registerNewFragment("TempBasals", TempBasalsFragment.newInstance());
        mAdapter.registerNewFragment("Profile", ProfileViewerFragment.newInstance());
        mAdapter.registerNewFragment("Objectives", ObjectivesFragment.newInstance());

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);
        mTabs = (SlidingTabLayout) findViewById(R.id.tabs);
        mTabs.setViewPager(mPager);

        registerBus();

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


    @Override
    public void onFragmentInteraction(String param) {

    }

    private void registerBus() {
        try {
            MainApp.bus().unregister(this);
        } catch (RuntimeException x) {
            // Ignore
        }
        MainApp.bus().register(this);
    }


}
