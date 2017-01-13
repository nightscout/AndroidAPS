package info.nightscout.androidaps.plugins.TempTargetRange;

import android.support.annotation.Nullable;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.squareup.otto.Subscribe;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.db.TempTarget;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.TempTargetRange.events.EventNewTempTargetRange;

/**
 * Created by mike on 13/01/17.
 */

public class TempTargetRangePlugin implements PluginBase {

    static boolean fragmentEnabled = true;
    static boolean fragmentVisible = true;

    private static List<TempTarget> tempTargets;

    TempTargetRangePlugin() {
        initializeData();
        MainApp.bus().register(this);
    }

    @Override
    public int getType() {
        return PluginBase.GENERAL;
    }

    @Override
    public String getFragmentClass() {
        return TempTargetRangeFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.sResources.getString(R.string.temptargetrange);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == GENERAL && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == GENERAL && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == GENERAL) {
            this.fragmentEnabled = fragmentEnabled;
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == GENERAL) this.fragmentVisible = fragmentVisible;
    }

    public static boolean isEnabled() {
        return fragmentEnabled;
    }

    @Subscribe
    public void onStatusEvent(final EventNewTempTargetRange ev) {

    }

    private void initializeData() {
        long fromMills = (long) (new Date().getTime() - 60 * 60 * 1000L * 24);
        tempTargets = MainApp.getDbHelper().getTemptargetsDataFromTime(fromMills, false);
    }

    public List<TempTarget> getList() {
        return tempTargets;
    }

    @Nullable
    public TempTarget getTempTargetInProgress(long time) {
        for (int i = tempTargets.size() - 1; i >= 0; i--) {
            if (tempTargets.get(i).timeStart.getTime() > time) continue;
            if (tempTargets.get(i).getPlannedTimeEnd().getTime() >= time) return tempTargets.get(i);
        }
        return null;
    }
}
