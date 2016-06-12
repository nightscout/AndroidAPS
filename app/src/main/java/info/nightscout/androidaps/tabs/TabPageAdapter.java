package info.nightscout.androidaps.tabs;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.PluralsRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.PluginBase;

/**
 * Created by mike on 30.05.2016.
 */
public class TabPageAdapter extends FragmentPagerAdapter {

    int registeredTabs = 0;
    List<Fragment> fragmentList = new ArrayList<Fragment>();

    public TabPageAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    @Nullable
    public Fragment getItem(int position) {
        if (position > registeredTabs)
            return null;
        Fragment fragment = fragmentList.get(position);
        return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return fragmentList.get(position).getArguments().getString("name");
    }

    @Override
    public int getCount() {
        return registeredTabs;
    }

    public int registerNewFragment(String name, Fragment fragment) {
        if (((PluginBase) fragment).isFragmentVisible()){
            fragmentList.add(fragment);
            Bundle args = new Bundle();
            args.putString("name", name);
            fragment.setArguments(args);
            registeredTabs++;
            notifyDataSetChanged();
            return registeredTabs - 1;
        }
        return  registeredTabs;
    }
}
