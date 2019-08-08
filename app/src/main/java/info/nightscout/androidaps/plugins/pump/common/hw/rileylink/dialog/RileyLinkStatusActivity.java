package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;

public class RileyLinkStatusActivity extends NoSplashAppCompatActivity {

    TextView connectionStatus;
    TextView configuredAddress;
    TextView connectedDevice;
    TextView connectionError;
    RileyLinkServiceData rileyLinkServiceData;

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private FloatingActionButton floatingActionButton;
    private TabLayout tabLayout;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rileylink_status);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.rileylink_settings_container);
        // mViewPager.setAdapter(mSectionsPagerAdapter);
        setupViewPager(mViewPager);

        tabLayout = (TabLayout) findViewById(R.id.rileylink_settings_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        floatingActionButton = (FloatingActionButton) findViewById(R.id.rileylink_settings_fab);
        floatingActionButton.setOnClickListener(v -> {

            RefreshableInterface selectableInterface = (RefreshableInterface) mSectionsPagerAdapter
                    .getItem(tabLayout.getSelectedTabPosition());
            selectableInterface.refreshData();

            // refreshData(tabLayout.getSelectedTabPosition());

            // Toast.makeText(getApplicationContext(), "Test pos: " + tabLayout.getSelectedTabPosition(),
            // Toast.LENGTH_LONG);
        });

        this.connectionStatus = findViewById(R.id.rls_t1_connection_status);
        this.configuredAddress = findViewById(R.id.rls_t1_configured_address);
        this.connectedDevice = findViewById(R.id.rls_t1_connected_device);
        this.connectionError = findViewById(R.id.rls_t1_connection_error);

        rileyLinkServiceData = RileyLinkUtil.getRileyLinkServiceData();

        // // 7-12
        // int[] ids = {R.id.rls_t1_tv02, R.id.rls_t1_tv03, R.id.rls_t1_tv04, R.id.rls_t1_tv05, R.id.rls_t1_tv07, //
        // R.id.rls_t1_tv08, R.id.rls_t1_tv09, R.id.rls_t1_tv10, R.id.rls_t1_tv11, R.id.rls_t1_tv12};
        //
        // for (int id : ids) {
        //
        // TextView tv = (TextView) findViewById(id);
        // tv.setText(tv.getText() + ":");
        // }

        // refreshData(0);
        // refreshData(1);

    }


    public void refreshData(int position) {
        if (position == 0) {
            // FIXME i18n
            this.connectionStatus.setText(rileyLinkServiceData.serviceState.name());
            this.configuredAddress.setText(rileyLinkServiceData.rileylinkAddress);
            // FIXME
            this.connectedDevice.setText("???");
            // FIXME i18n
            this.connectionError.setText(rileyLinkServiceData.errorCode.name());
        } else {

        }

    }


    public void setupViewPager(ViewPager pager) {

        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        mSectionsPagerAdapter.addFragment(new RileyLinkStatusGeneral(), MainApp.gs(R.string.rileylink_settings_tab1));
        mSectionsPagerAdapter.addFragment(new RileyLinkStatusHistory(), MainApp.gs(R.string.rileylink_settings_tab2));
        //mSectionsPagerAdapter.addFragment(new RileyLinkStatusDevice(), "Medtronic");

        mViewPager.setAdapter(mSectionsPagerAdapter);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        List<Fragment> fragmentList = new ArrayList<>();
        List<String> fragmentTitle = new ArrayList<>();
        int lastSelectedPosition = 0;


        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }


        @Override
        public Fragment getItem(int position) {
            this.lastSelectedPosition = position;
            return fragmentList.get(position);
        }


        @Override
        public int getCount() {
            // Show 3 total pages.
            return fragmentList.size();
        }


        public void addFragment(Fragment fragment, String title) {
            this.fragmentList.add(fragment);
            this.fragmentTitle.add(title);
        }


        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentTitle.get(position);
        }
    }
}
