package info.nightscout.androidaps.plugins.TuneProfile;

import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.android.apptools.OrmLiteBaseService;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.ICallback;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventNsTreatment;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.JsonHelper;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public  class AutotuneService extends OrmLiteBaseService<DatabaseHelper>  {
    private static Logger log = LoggerFactory.getLogger(L.DATATREATMENTS);

    //Add for Autotune
    public  List<TemporaryBasal> getTempBasalDataFromTime(long from, long to, boolean ascending) {
        try {
            Dao<TemporaryBasal, Long> daoTemporaryBasal = getTBDao();
            List<TemporaryBasal> tempbasal;
            QueryBuilder<TemporaryBasal, Long> queryBuilder = daoTemporaryBasal.queryBuilder();
            queryBuilder.orderBy("created_at", ascending);
            Where where = queryBuilder.where();
            String fromUTC= DateUtil.toISOAsUTC(from);
            String toUTC= DateUtil.toISOAsUTC(to);
            where.between("created_at", fromUTC, toUTC).and().eq("eventType","Temp Basal");
            PreparedQuery<TemporaryBasal> preparedQuery = queryBuilder.prepare();
            tempbasal = daoTemporaryBasal.query(preparedQuery);
            return tempbasal;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public  Dao<TemporaryBasal, Long> getTBDao() {
        try {
            return DaoManager.createDao(this.getConnectionSource(), TemporaryBasal.class);
        } catch (SQLException e) {
            log.error("Cannot create Dao for Treatment.class");
        }

        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
