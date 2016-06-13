package info.nightscout.androidaps.tabs;

import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import java.util.ArrayList;
import java.util.Iterator;

import info.nightscout.androidaps.plugins.PluginBase;

/**
 * Created by mike on 30.05.2016.
 */
public class TabPageAdapter extends FragmentStatePagerAdapter {

    ArrayList<PluginBase> fragmentList = new ArrayList<PluginBase>();
    ArrayList<PluginBase> visibleFragmentList = new ArrayList<PluginBase>();

    FragmentManager fm;

    public TabPageAdapter(FragmentManager fm) {
        super(fm);
        this.fm = fm;
    }

    @Override
    @Nullable
    public Fragment getItem(int position) {
        Fragment fragment = (Fragment) visibleFragmentList.get(position);
        return fragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return visibleFragmentList.get(position).getName();
    }

    @Override
    public int getCount() {
        return visibleFragmentList.size();
    }

    public void registerNewFragment(Fragment fragment) {
        PluginBase plugin = (PluginBase) fragment;
        fragmentList.add(plugin);
        if (plugin.isVisibleInTabs()) {
            visibleFragmentList.add(plugin);
            notifyDataSetChanged();
        }
    }

    public ArrayList<PluginBase> getPluginsList() {
        return fragmentList;
    }

    public ArrayList<PluginBase> getSpecificPluginsList(int type) {
        ArrayList<PluginBase> newList = new ArrayList<PluginBase>();

        Iterator<PluginBase> it = fragmentList.iterator();
        while (it.hasNext()) {
            PluginBase p = it.next();
            if (p.getType() == type)
                newList.add(p);
        }
        return newList;
    }
}
