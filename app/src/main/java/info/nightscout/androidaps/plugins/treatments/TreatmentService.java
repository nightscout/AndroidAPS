package info.nightscout.androidaps.plugins.treatments;

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
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventNsTreatment;
import info.nightscout.androidaps.events.EventReloadTreatmentData;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.JsonHelper;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


/**
 * Created by mike on 24.09.2017.
 */

public class TreatmentService extends OrmLiteBaseService<DatabaseHelper> {
    private static Logger log = LoggerFactory.getLogger(L.DATATREATMENTS);
    private CompositeDisposable disposable = new CompositeDisposable();

    private static final ScheduledExecutorService treatmentEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledTreatmentEventPost = null;

    public TreatmentService() {
        onCreate();
        dbInitialize();
        disposable.add(RxBus.INSTANCE
                .toObservable(EventNsTreatment.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    int mode = event.getMode();
                    JSONObject payload = event.getPayload();

                    if (mode == EventNsTreatment.Companion.getADD() || mode == EventNsTreatment.Companion.getUPDATE()) {
                        this.createTreatmentFromJsonIfNotExists(payload);
                    } else { // EventNsTreatment.REMOVE
                        this.deleteNS(payload);
                    }
                }, FabricPrivacy::logException)
        );
    }

    /**
     * This method is a simple re-implementation of the database create and up/downgrade functionality
     * in SQLiteOpenHelper#getDatabaseLocked method.
     * <p>
     * It is implemented to be able to late initialize separate plugins of the application.
     */
    protected void dbInitialize() {
        DatabaseHelper helper = OpenHelperManager.getHelper(this, DatabaseHelper.class);
        int newVersion = helper.getNewVersion();
        int oldVersion = helper.getOldVersion();

        if (oldVersion > newVersion) {
            onDowngrade(this.getConnectionSource(), oldVersion, newVersion);
        } else {
            onUpgrade(this.getConnectionSource(), oldVersion, newVersion);
        }
    }

    public Dao<Treatment, Long> getDao() {
        try {
            return DaoManager.createDao(this.getConnectionSource(), Treatment.class);
        } catch (SQLException e) {
            log.error("Cannot create Dao for Treatment.class");
        }

        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (L.isEnabled(L.DATATREATMENTS))
                log.info("onCreate");
            TableUtils.createTableIfNotExists(this.getConnectionSource(), Treatment.class);
        } catch (SQLException e) {
            log.error("Can't create database", e);
            throw new RuntimeException(e);
        }
    }

    public void onUpgrade(ConnectionSource connectionSource, int oldVersion, int newVersion) {
        if (oldVersion == 7 && newVersion == 8) {
            log.debug("Upgrading database from v7 to v8");
            try {
                TableUtils.dropTable(connectionSource, Treatment.class, true);
                TableUtils.createTableIfNotExists(connectionSource, Treatment.class);
            } catch (SQLException e) {
                log.error("Can't create database", e);
                throw new RuntimeException(e);
            }
        } else if (oldVersion == 8 && newVersion == 9) {
            log.debug("Upgrading database from v8 to v9");
            try {
                getDao().executeRaw("ALTER TABLE `" + Treatment.TABLE_TREATMENTS + "` ADD COLUMN boluscalc STRING;");
            } catch (SQLException e) {
                log.error("Unhandled exception", e);
            }
        } else {
            if (L.isEnabled(L.DATATREATMENTS))
                log.info("onUpgrade");
//            this.resetFood();
        }
    }

    public void onDowngrade(ConnectionSource connectionSource, int oldVersion, int newVersion) {
        if (oldVersion == 9 && newVersion == 8) {
            try {
                getDao().executeRaw("ALTER TABLE `" + Treatment.TABLE_TREATMENTS + "` DROP COLUMN boluscalc STRING;");
            } catch (SQLException e) {
                log.error("Unhandled exception", e);
            }
        }
    }

    public void resetTreatments() {
        try {
            TableUtils.dropTable(this.getConnectionSource(), Treatment.class, true);
            TableUtils.createTableIfNotExists(this.getConnectionSource(), Treatment.class);
            DatabaseHelper.updateEarliestDataChange(0);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTreatmentChange(null, true);
    }


    /**
     * A place to centrally register events to be posted, if any data changed.
     * This should be implemented in an abstract service-class.
     * <p>
     * We do need to make sure, that ICallback is extended to be able to handle multiple
     * events, or handle a list of events.
     * <p>
     * on some methods the earliestDataChange event is handled separatly, in that it is checked if it is
     * set to null by another event already (eg. scheduleExtendedBolusChange).
     *
     * @param event
     * @param eventWorker
     * @param callback
     */
    private void scheduleEvent(final Event event, ScheduledExecutorService eventWorker,
                               final ICallback callback) {

        class PostRunnable implements Runnable {
            public void run() {
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("Firing EventReloadTreatmentData");
                RxBus.INSTANCE.send(event);
                if (DatabaseHelper.earliestDataChange != null) {
                    if (L.isEnabled(L.DATATREATMENTS))
                        log.debug("Firing EventNewHistoryData");
                    RxBus.INSTANCE.send(new EventNewHistoryData(DatabaseHelper.earliestDataChange));
                }
                DatabaseHelper.earliestDataChange = null;
                callback.setPost(null);
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        ScheduledFuture<?> scheduledFuture = callback.getPost();
        if (scheduledFuture != null)
            scheduledFuture.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        callback.setPost(eventWorker.schedule(task, sec, TimeUnit.SECONDS));
    }

    /**
     * Schedule a foodChange Event.
     */
    public void scheduleTreatmentChange(@Nullable final Treatment treatment, boolean runImmediately) {
        if (runImmediately) {
            if (L.isEnabled(L.DATATREATMENTS))
                log.debug("Firing EventReloadTreatmentData");
            RxBus.INSTANCE.send(new EventReloadTreatmentData(new EventTreatmentChange(treatment)));
            if (DatabaseHelper.earliestDataChange != null) {
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("Firing EventNewHistoryData");
                RxBus.INSTANCE.send(new EventNewHistoryData(DatabaseHelper.earliestDataChange));
            }
            DatabaseHelper.earliestDataChange = null;
        } else {
            this.scheduleEvent(new EventReloadTreatmentData(new EventTreatmentChange(treatment)), treatmentEventWorker, new ICallback() {
                @Override
                public void setPost(ScheduledFuture<?> post) {
                    scheduledTreatmentEventPost = post;
                }

                @Override
                public ScheduledFuture<?> getPost() {
                    return scheduledTreatmentEventPost;
                }
            });
        }
    }

    public List<Treatment> getTreatmentData() {
        try {
            return this.getDao().queryForAll();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }

        return new ArrayList<>();
    }

    /*
    {
        "_id": "551ee3ad368e06e80856e6a9",
        "type": "food",
        "category": "Zakladni",
        "subcategory": "Napoje",
        "name": "Mleko",
        "portion": 250,
        "carbs": 12,
        "gi": 1,
        "created_at": "2015-04-14T06:59:16.500Z",
        "unit": "ml"
    }
     */
    public void createTreatmentFromJsonIfNotExists(JSONObject json) {
        try {
            Treatment treatment = Treatment.createFromJson(json);
            if (treatment != null) {

                if (MedtronicHistoryData.doubleBolusDebug)
                    log.debug("DoubleBolusDebug: createTreatmentFromJsonIfNotExists:: medtronicPump={}", MedtronicUtil.isMedtronicPump());

                if (!MedtronicUtil.isMedtronicPump())
                    createOrUpdate(treatment);
                else
                    createOrUpdateMedtronic(treatment, true);
            } else
                log.error("Date is null: " + treatment.toString());
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }


    // return true if new record is created
    public UpdateReturn createOrUpdate(Treatment treatment) {
        try {
            Treatment old;
            treatment.date = DatabaseHelper.roundDateToSec(treatment.date);

            if (treatment.source == Source.PUMP) {
                // check for changed from pump change in NS
                Treatment existingTreatment = getPumpRecordById(treatment.pumpId);
                if (existingTreatment != null) {
                    boolean equalRePumpHistory = existingTreatment.equalsRePumpHistory(treatment);
                    boolean sameSource = existingTreatment.source == treatment.source;
                    if (!equalRePumpHistory) {
                        // another treatment exists. Update it with the treatment coming from the pump
                        if (L.isEnabled(L.DATATREATMENTS))
                            log.debug("Pump record already found in database: " + existingTreatment.toString() + " wanting to add " + treatment.toString());
                        long oldDate = existingTreatment.date;

                        //preserve carbs
                        if (existingTreatment.isValid && existingTreatment.carbs > 0 && treatment.carbs == 0) {
                            treatment.carbs = existingTreatment.carbs;
                        }

                        getDao().delete(existingTreatment); // need to delete/create because date may change too
                        existingTreatment.copyBasics(treatment);
                        getDao().create(existingTreatment);
                        DatabaseHelper.updateEarliestDataChange(oldDate);
                        DatabaseHelper.updateEarliestDataChange(existingTreatment.date);
                        scheduleTreatmentChange(treatment, true);
                        return new UpdateReturn(sameSource, false); //updating a pump treatment with another one from the pump is not counted as clash
                    }
                    return new UpdateReturn(equalRePumpHistory, false);
                }
                existingTreatment = getDao().queryForId(treatment.date);
                if (existingTreatment != null) {
                    // another treatment exists with different pumpID. Update it with the treatment coming from the pump
                    boolean equalRePumpHistory = existingTreatment.equalsRePumpHistory(treatment);
                    boolean sameSource = existingTreatment.source == treatment.source;
                    long oldDate = existingTreatment.date;
                    if (L.isEnabled(L.DATATREATMENTS))
                        log.debug("Pump record already found in database: " + existingTreatment.toString() + " wanting to add " + treatment.toString());

                    //preserve carbs
                    if (existingTreatment.isValid && existingTreatment.carbs > 0 && treatment.carbs == 0) {
                        treatment.carbs = existingTreatment.carbs;
                    }

                    getDao().delete(existingTreatment); // need to delete/create because date may change too
                    existingTreatment.copyFrom(treatment);
                    getDao().create(existingTreatment);
                    DatabaseHelper.updateEarliestDataChange(oldDate);
                    DatabaseHelper.updateEarliestDataChange(existingTreatment.date);
                    scheduleTreatmentChange(treatment, true);
                    return new UpdateReturn(equalRePumpHistory || sameSource, false);
                }
                getDao().create(treatment);
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                DatabaseHelper.updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange(treatment, true);
                return new UpdateReturn(true, true);
            }
            if (treatment.source == Source.NIGHTSCOUT) {
                old = getDao().queryForId(treatment.date);
                if (old != null) {
                    if (!old.isEqual(treatment)) {
                        boolean historyChange = old.isDataChanging(treatment);
                        long oldDate = old.date;
                        getDao().delete(old); // need to delete/create because date may change too
                        old.copyFrom(treatment);
                        getDao().create(old);
                        if (L.isEnabled(L.DATATREATMENTS))
                            log.debug("Updating record by date from: " + Source.getString(treatment.source) + " " + old.toString());
                        if (historyChange) {
                            DatabaseHelper.updateEarliestDataChange(oldDate);
                            DatabaseHelper.updateEarliestDataChange(old.date);
                        }
                        scheduleTreatmentChange(treatment, false);
                        return new UpdateReturn(true, true);
                    }
                    if (L.isEnabled(L.DATATREATMENTS))
                        log.debug("Equal record by date from: " + Source.getString(treatment.source) + " " + old.toString());
                    return new UpdateReturn(true, false);
                }
                // find by NS _id
                if (treatment._id != null) {
                    old = findByNSId(treatment._id);
                    if (old != null) {
                        if (!old.isEqual(treatment)) {
                            boolean historyChange = old.isDataChanging(treatment);
                            long oldDate = old.date;
                            getDao().delete(old); // need to delete/create because date may change too
                            old.copyFrom(treatment);
                            getDao().create(old);
                            if (L.isEnabled(L.DATATREATMENTS))
                                log.debug("Updating record by _id from: " + Source.getString(treatment.source) + " " + old.toString());
                            if (historyChange) {
                                DatabaseHelper.updateEarliestDataChange(oldDate);
                                DatabaseHelper.updateEarliestDataChange(old.date);
                            }
                            scheduleTreatmentChange(treatment, false);
                            return new UpdateReturn(true, true);
                        }
                        if (L.isEnabled(L.DATATREATMENTS))
                            log.debug("Equal record by _id from: " + Source.getString(treatment.source) + " " + old.toString());
                        return new UpdateReturn(true, false);
                    }
                }
                getDao().create(treatment);
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                DatabaseHelper.updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange(treatment, false);
                return new UpdateReturn(true, true);
            }
            if (treatment.source == Source.USER) {
                getDao().create(treatment);
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                DatabaseHelper.updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange(treatment, true);
                return new UpdateReturn(true, true);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new UpdateReturn(false, false);
    }


    public UpdateReturn createOrUpdateMedtronic(Treatment treatment, boolean fromNightScout) {

        if (MedtronicHistoryData.doubleBolusDebug)
            log.debug("DoubleBolusDebug: createOrUpdateMedtronic:: originalTreatment={}, fromNightScout={}", treatment, fromNightScout);

        try {
            treatment.date = DatabaseHelper.roundDateToSec(treatment.date);

            Treatment existingTreatment = getRecord(treatment.pumpId, treatment.date);

            if (MedtronicHistoryData.doubleBolusDebug)
                log.debug("DoubleBolusDebug: createOrUpdateMedtronic:: existingTreatment={}", treatment);

            if (existingTreatment == null) {
                getDao().create(treatment);
                if (L.isEnabled(L.DATATREATMENTS))
                    log.debug("New record from: " + Source.getString(treatment.source) + " " + treatment.toString());
                DatabaseHelper.updateEarliestDataChange(treatment.date);
                scheduleTreatmentChange(treatment, true);
                return new UpdateReturn(true, true);
            } else {

                if (existingTreatment.date == treatment.date) {
                    if (MedtronicHistoryData.doubleBolusDebug)
                        log.debug("DoubleBolusDebug: createOrUpdateMedtronic::(existingTreatment.date==treatment.date)");

                    // we will do update only, if entry changed
                    if (!optionalTreatmentCopy(existingTreatment, treatment, fromNightScout)) {
                        return new UpdateReturn(true, false);
                    }
                    getDao().update(existingTreatment);
                    DatabaseHelper.updateEarliestDataChange(existingTreatment.date);
                    scheduleTreatmentChange(treatment, true);
                    return new UpdateReturn(true, false);
                } else {
                    if (MedtronicHistoryData.doubleBolusDebug)
                        log.debug("DoubleBolusDebug: createOrUpdateMedtronic::(existingTreatment.date != treatment.date)");

                    // date is different, we need to remove entry
                    getDao().delete(existingTreatment);
                    optionalTreatmentCopy(existingTreatment, treatment, fromNightScout);
                    getDao().create(existingTreatment);
                    DatabaseHelper.updateEarliestDataChange(existingTreatment.date);
                    scheduleTreatmentChange(treatment, true);
                    return new UpdateReturn(true, false); //updating a pump treatment with another one from the pump is not counted as clash
                }
            }

        } catch (SQLException e) {
            log.error("Unhandled SQL exception: {}", e.getMessage(), e);
        }
        return new UpdateReturn(false, false);
    }


    private boolean optionalTreatmentCopy(Treatment oldTreatment, Treatment newTreatment, boolean fromNightScout) {

        log.debug("optionalTreatmentCopy [old={}, new={}]", oldTreatment.toString(), newTreatment.toString());

        boolean changed = false;

        if (oldTreatment.date != newTreatment.date) {
            oldTreatment.date = newTreatment.date;
            changed = true;
        }

        if (oldTreatment.isSMB != newTreatment.isSMB) {
            if (!oldTreatment.isSMB) {
                oldTreatment.isSMB = newTreatment.isSMB;
                changed = true;
            }
        }

        if (!isSame(oldTreatment.carbs, newTreatment.carbs)) {
            if (isSame(oldTreatment.carbs, 0.0d)) {
                oldTreatment.carbs = newTreatment.carbs;
                changed = true;
            }
        }

        if (oldTreatment.mealBolus != (oldTreatment.carbs > 0)) {
            oldTreatment.mealBolus = (oldTreatment.carbs > 0);
            changed = true;
        }

        if (!isSame(oldTreatment.insulin, newTreatment.insulin)) {
            if (!fromNightScout) {
                oldTreatment.insulin = newTreatment.insulin;
                changed = true;
            }
        }

        if (!StringUtils.equals(oldTreatment._id, newTreatment._id)) {
            if (StringUtils.isBlank(oldTreatment._id)) {
                oldTreatment._id = newTreatment._id;
                changed = true;
            }
        }

        int source = Source.NONE;

        if (oldTreatment.pumpId == 0) {
            if (newTreatment.pumpId > 0) {
                oldTreatment.pumpId = newTreatment.pumpId;
                source = Source.PUMP;
                changed = true;
            }
        }

        if (source == Source.NONE) {

            if (oldTreatment.source == newTreatment.source) {
                source = oldTreatment.source;
            } else {
                source = (oldTreatment.source == Source.NIGHTSCOUT || newTreatment.source == Source.NIGHTSCOUT) ? Source.NIGHTSCOUT : Source.USER;
            }
        }

        if (oldTreatment.source != source) {
            oldTreatment.source = source;
            changed = true;
        }

        log.debug("optionalTreatmentCopy [changed={}, newAfterChange={}]", changed, oldTreatment.toString());
        return changed;
    }


    public static boolean isSame(Double d1, Double d2) {
        double diff = d1 - d2;

        return (Math.abs(diff) <= 0.00001);
    }

    @Deprecated
    private void treatmentCopy(Treatment oldTreatment, Treatment newTreatment, boolean fromNightScout) {

        log.debug("treatmentCopy [old={}, new={}]", oldTreatment.toString(), newTreatment.toString());


        if (fromNightScout) {
            long pumpId_old = oldTreatment.pumpId;
            boolean isSMB = (oldTreatment.isSMB || newTreatment.isSMB);

            oldTreatment.copyFrom(newTreatment);

            if (pumpId_old != 0) {
                oldTreatment.pumpId = pumpId_old;
            }

            if (oldTreatment.pumpId != 0 && oldTreatment.source != Source.PUMP) {
                oldTreatment.source = Source.PUMP;
            }

            oldTreatment.isSMB = isSMB;

        } else {
            oldTreatment.copyFrom(newTreatment);
        }

        log.debug("treatmentCopy [newAfterChange={}]", oldTreatment.toString());

    }


    public Treatment getRecord(long pumpId, long date) {

        Treatment record = null;

        if (pumpId > 0) {

            record = getPumpRecordById(pumpId);

            if (record != null) {
                return record;
            }
        }

        try {
            record = getDao().queryForId(date);
        } catch (SQLException ex) {
            log.error("Error getting entry by id ({}", date);
        }

        return record;

    }


    /**
     * Returns the record for the given id, null if none, throws RuntimeException
     * if multiple records with the same pump id exist.
     */
    @Nullable
    public Treatment getPumpRecordById(long pumpId) {
        try {
            QueryBuilder<Treatment, Long> queryBuilder = getDao().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("pumpId", pumpId);
            queryBuilder.orderBy("date", true);

            List<Treatment> result = getDao().query(queryBuilder.prepare());
            if (result.isEmpty())
                return null;
            if (result.size() > 1)
                log.warn("Multiple records with the same pump id found (returning first one): " + result.toString());
            return result.get(0);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteNS(JSONObject json) {
        String _id = JsonHelper.safeGetString(json, "_id");
        if (_id != null && !_id.isEmpty())
            this.deleteByNSId(_id);
    }

    /**
     * deletes an entry by its NS Id.
     * <p>
     * Basically a convenience method for findByNSId and delete.
     *
     * @param _id
     */
    private void deleteByNSId(String _id) {
        Treatment stored = findByNSId(_id);
        if (stored != null) {
            if (L.isEnabled(L.DATATREATMENTS))
                log.debug("Removing Treatment record from database: " + stored.toString());
            try {
                getDao().delete(stored);
            } catch (SQLException e) {
                log.error("Unhandled exception", e);
            }
            DatabaseHelper.updateEarliestDataChange(stored.date);
            this.scheduleTreatmentChange(stored, false);
        }
    }

    /**
     * deletes the treatment and sends the treatmentChange Event
     * <p>
     * should be moved ot a Service
     *
     * @param treatment
     */
    public void delete(Treatment treatment) {
        try {
            getDao().delete(treatment);
            DatabaseHelper.updateEarliestDataChange(treatment.date);
            this.scheduleTreatmentChange(treatment, true);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void update(Treatment treatment) {
        try {
            getDao().update(treatment);
            DatabaseHelper.updateEarliestDataChange(treatment.date);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleTreatmentChange(treatment, true);
    }

    /**
     * finds treatment by its NS Id.
     *
     * @param _id
     * @return
     */
    @Nullable
    public Treatment findByNSId(String _id) {
        try {
            Dao<Treatment, Long> daoTreatments = getDao();
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            queryBuilder.limit(10L);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            List<Treatment> trList = daoTreatments.query(preparedQuery);
            if (trList.size() != 1) {
                //log.debug("Treatment findTreatmentById query size: " + trList.size());
                return null;
            } else {
                //log.debug("Treatment findTreatmentById found: " + trList.get(0).log());
                return trList.get(0);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

    public List<Treatment> getTreatmentDataFromTime(long mills, boolean ascending) {
        try {
            Dao<Treatment, Long> daoTreatments = getDao();
            List<Treatment> treatments;
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.ge("date", mills);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = daoTreatments.query(preparedQuery);
            return treatments;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public List<Treatment> getTreatmentDataFromTime(long from, long to, boolean ascending) {
        try {
            Dao<Treatment, Long> daoTreatments = getDao();
            List<Treatment> treatments;
            QueryBuilder<Treatment, Long> queryBuilder = daoTreatments.queryBuilder();
            queryBuilder.orderBy("date", ascending);
            Where where = queryBuilder.where();
            where.between("date", from, to);
            PreparedQuery<Treatment> preparedQuery = queryBuilder.prepare();
            treatments = daoTreatments.query(preparedQuery);
            return treatments;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class UpdateReturn {
        public UpdateReturn(boolean success, boolean newRecord) {
            this.success = success;
            this.newRecord = newRecord;
        }

        boolean newRecord;
        boolean success;

        @Override
        public String toString() {
            return "UpdateReturn [" +
                    "newRecord=" + newRecord +
                    ", success=" + success +
                    ']';
        }
    }

}
