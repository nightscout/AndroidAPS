package info.nightscout.androidaps.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.ViewGroup;

import java.util.ArrayList;

import info.nightscout.androidaps.interfaces.PluginBase;

/**
 * Created by mike on 30.05.2016.
 */
public class TabPageAdapter extends FragmentStatePagerAdapter {

    ArrayList<PluginBase> visibleFragmentList = new ArrayList<>();

    FragmentManager fm;
    Context context;

    public TabPageAdapter(FragmentManager fm, Context context) {
        super(fm);
        this.fm = fm;
        this.context = context;
    }

    @Override
    @Nullable
    public Fragment getItem(int position) {
        //Fragment fragment = (Fragment) visibleFragmentList.get(position);
        return Fragment.instantiate(context, visibleFragmentList.get(position).getFragmentClass());
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        try{
            super.finishUpdate(container);
        } catch (NullPointerException nullPointerException){
            System.out.println("Catch the NullPointerException in FragmentStatePagerAdapter.finishUpdate");
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if(preferences.getBoolean("short_tabtitles", false)){
            return visibleFragmentList.get(position).getNameShort();
        }
        return visibleFragmentList.get(position).getName();

    }

    @Override
    public int getCount() {
        return visibleFragmentList.size();
    }

    public void registerNewFragment(PluginBase plugin) {
        if (plugin.isVisibleInTabs(plugin.getType())) {
            visibleFragmentList.add(plugin);
            notifyDataSetChanged();
        }
    }


}
