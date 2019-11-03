package info.nightscout.androidaps.tabs;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import android.view.ViewGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 30.05.2016.
 */
public class TabPageAdapter extends FragmentPagerAdapter {

    ArrayList<PluginBase> visibleFragmentList = new ArrayList<>();

    Context context;

    private static Logger log = LoggerFactory.getLogger(L.CORE);

    public TabPageAdapter(FragmentManager fm, Context context) {
        super(fm);
        this.context = context;
    }

    @Override
    @Nullable
    public Fragment getItem(int position) {
        //Fragment fragment = (Fragment) visibleFragmentList.get(position);
        return Fragment.instantiate(context, visibleFragmentList.get(position).pluginDescription.getFragmentClass());
    }

    public PluginBase getPluginAt(int position) {
        return visibleFragmentList.get(position);
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        try {
            super.finishUpdate(container);
        } catch (NullPointerException nullPointerException) {
            System.out.println("Catch the NullPointerException in FragmentStatePagerAdapter.finishUpdate");
        } catch (IllegalStateException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        if (SP.getBoolean(R.string.key_short_tabtitles, false)) {
            return visibleFragmentList.get(position).getNameShort();
        }
        return visibleFragmentList.get(position).getName();

    }

    @Override
    public int getCount() {
        return visibleFragmentList.size();
    }

    public void registerNewFragment(PluginBase plugin) {
        if (plugin.hasFragment() && plugin.isFragmentVisible()) {
            visibleFragmentList.add(plugin);
            notifyDataSetChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        return System.identityHashCode(visibleFragmentList.get(position));
    }
}
