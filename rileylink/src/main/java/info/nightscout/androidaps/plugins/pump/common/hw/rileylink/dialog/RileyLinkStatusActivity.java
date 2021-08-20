package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.dialog;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import info.nightscout.androidaps.activities.NoSplashAppCompatActivity;
import info.nightscout.androidaps.plugins.pump.common.R;
import info.nightscout.androidaps.plugins.pump.common.dialog.RefreshableInterface;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

public class RileyLinkStatusActivity extends NoSplashAppCompatActivity {

    @Inject ResourceHelper resourceHelper;

    private SectionsPagerAdapter sectionsPagerAdapter;
    private TabLayout tabLayout;
    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager viewPager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rileylink_status);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.

        // Set up the ViewPager with the sections adapter.
        viewPager = findViewById(R.id.rileylink_settings_container);
        // mViewPager.setAdapter(mSectionsPagerAdapter);
        setupViewPager();

        tabLayout = findViewById(R.id.rileylink_settings_tabs);
        tabLayout.setupWithViewPager(viewPager);

        FloatingActionButton floatingActionButton = findViewById(R.id.rileylink_settings_fab);
        floatingActionButton.setOnClickListener(v -> {

            RefreshableInterface selectableInterface = (RefreshableInterface) sectionsPagerAdapter
                    .getItem(tabLayout.getSelectedTabPosition());
            selectableInterface.refreshData();
        });
    }

    public void setupViewPager() {
        sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        sectionsPagerAdapter.addFragment(new RileyLinkStatusGeneralFragment(), resourceHelper.gs(R.string.rileylink_settings_tab1));
        sectionsPagerAdapter.addFragment(new RileyLinkStatusHistoryFragment(), resourceHelper.gs(R.string.rileylink_settings_tab2));
        viewPager.setAdapter(sectionsPagerAdapter);
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
