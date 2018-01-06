package info.nightscout.androidaps.db;

import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.j256.ormlite.android.apptools.OrmLiteBaseService;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.Event;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;

/**
 * Created by mike on 24.09.2017.
 */

public class FoodService extends OrmLiteBaseService<DatabaseHelper> {
    private static Logger log = LoggerFactory.getLogger(FoodService.class);

    private static final ScheduledExecutorService foodEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledFoodEventPost = null;

    public FoodService() {
        this.onCreate();
    }

    public FoodDao getDao() {
        return FoodDao.with(this.getConnectionSource());
    }

    /**
     * This service method is just taking care about the Food-Table,
     * a central dataService should be use for throwing events for all
     * tables.
     */
    public void resetFood() {
        this.getHelper().resetFood();
        scheduleFoodChange();
    }

    /**
     * A place to centrally register events to be posted, if any data changed.
     * This should be implemented in an abstract service-class.
     *
     * We do need to make sure, that ICallback is extended to be able to handle multiple
     * events, or handle a list of events.
     *
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
                log.debug("Firing EventFoodChange");
                MainApp.bus().post(event);
                callback.setPost(null);
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (callback.getPost() != null)
            callback.getPost().cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        callback.setPost(eventWorker.schedule(task, sec, TimeUnit.SECONDS));
    }

    /**
     * Schedule a foodChange Event.
     */
    public void scheduleFoodChange() {
        this.scheduleEvent(new EventFoodDatabaseChanged(), foodEventWorker, new ICallback() {
            @Override
            public void setPost(ScheduledFuture<?> post) {
                scheduledFoodEventPost = post;
            }

            @Override
            public ScheduledFuture<?> getPost() {
                return scheduledFoodEventPost;
            }
        });
    }

    public List<Food> getFoodData() {
        return this.getDao().getFoodData();
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
    public void createFoodFromJsonIfNotExists(JSONObject trJson) {
        try {
            Food food = Food.createFromJson(trJson);
            this.getDao().createOrUpdate(food);
        } catch (JSONException | SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    /**
     * Create of update a food record by the NS (Nightscout) Id.
     *
     * @param food
     * @return
     */
    public boolean createOrUpdateByNS(Food food) {
        boolean result = this.getDao().createOrUpdateByNS(food);
        if (result) this.scheduleFoodChange();

        return result;
    }

    /**
     * deletes an entry by its NS Id.
     *
     * Basically a convenience method for findByNSId and delete.
     *
     * @param _id
     */
    public void deleteByNSId(String _id) throws SQLException {
        Food stored = this.getDao().findByNSId(_id);
        if (stored != null) {
            log.debug("FOOD: Removing Food record from database: " + stored.toString());
            this.delete(stored);
        }
    }

    /**
     * deletes the food and sends the foodChange Event
     *
     * should be moved ot a Service
     *
     * @param food
     */
    public void delete(Food food) {
        try {
            this.getDao().delete(food);
            this.scheduleFoodChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
