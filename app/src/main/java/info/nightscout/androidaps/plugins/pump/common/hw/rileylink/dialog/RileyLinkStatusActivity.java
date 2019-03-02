package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import butterknife.BindView;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;

public class RileyLinkStatusActivity extends AppCompatActivity {

    @BindView(R.id.rls_t1_connection_status)
    TextView connectionStatus;

    @BindView(R.id.rls_t1_configured_address)
    TextView configuredAddress;

    @BindView(R.id.rls_t1_connected_device)
    TextView connectedDevice;

    @BindView(R.id.rls_t1_connection_error)
    TextView connectionError;
    RileyLinkServiceData rileyLinkServiceData;
    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private FloatingActionButton floatingActionButton;
    private TabLayout tabLayout;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rileylink_status);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager)findViewById(R.id.rileylink_settings_container);
        // mViewPager.setAdapter(mSectionsPagerAdapter);
        setupViewPager(mViewPager);

        tabLayout = (TabLayout)findViewById(R.id.rileylink_settings_tabs);
        tabLayout.setupWithViewPager(mViewPager);

        floatingActionButton = (FloatingActionButton)findViewById(R.id.rileylink_settings_fab);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                RefreshableInterface selectableInterface = (RefreshableInterface)mSectionsPagerAdapter
                    .getItem(tabLayout.getSelectedTabPosition());
                selectableInterface.refreshData();

                // refreshData(tabLayout.getSelectedTabPosition());

                // Toast.makeText(getApplicationContext(), "Test pos: " + tabLayout.getSelectedTabPosition(),
                // Toast.LENGTH_LONG);
            }
        });

        this.connectionStatus = (TextView)findViewById(R.id.rls_t1_connection_status);
        this.configuredAddress = (TextView)findViewById(R.id.rls_t1_configured_address);
        this.connectedDevice = (TextView)findViewById(R.id.rls_t1_connected_device);
        this.connectionError = (TextView)findViewById(R.id.rls_t1_connection_error);

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
        // mSectionsPagerAdapter.addFragment(new RileyLinkSettingsTab3(), "Tab 3");

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
