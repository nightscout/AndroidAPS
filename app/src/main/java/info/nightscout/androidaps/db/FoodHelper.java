package info.nightscout.androidaps.db;

import com.j256.ormlite.android.AndroidConnectionSource;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.table.TableUtils;

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

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.events.EventFoodDatabaseChanged;

/**
 * Created by mike on 24.09.2017.
 */

public class FoodHelper {
    private static Logger log = LoggerFactory.getLogger(FoodHelper.class);

    DatabaseHelper databaseHelper;

    private static final ScheduledExecutorService foodEventWorker = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduledFoodEventPost = null;

    public FoodHelper(DatabaseHelper databaseHelper) {
        this.databaseHelper = databaseHelper;
    }

    private Dao<Food, Long> getDaoFood() throws SQLException {
        return databaseHelper.getDao(Food.class);
    }

    public void resetFood() {
        try {
            TableUtils.dropTable(databaseHelper.getConnectionSource(), Food.class, true);
            TableUtils.createTableIfNotExists(databaseHelper.getConnectionSource(), Food.class);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        scheduleFoodChange();
    }

    public List<Food> getFoodData() {
        try {
            Dao<Food, Long> daoFood = getDaoFood();
            List<Food> foods;
            QueryBuilder<Food, Long> queryBuilder = daoFood.queryBuilder();
            PreparedQuery<Food> preparedQuery = queryBuilder.prepare();
            foods = daoFood.query(preparedQuery);
            return foods;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return new ArrayList<>();
    }

    public boolean createOrUpdate(Food food) {
        try {
            // find by NS _id
            if (food._id != null && !food._id.equals("")) {
                Food old;

                QueryBuilder<Food, Long> queryBuilder = getDaoFood().queryBuilder();
                Where where = queryBuilder.where();
                where.eq("_id", food._id);
                PreparedQuery<Food> preparedQuery = queryBuilder.prepare();
                List<Food> found = getDaoFood().query(preparedQuery);
                if (found.size() > 0) {
                    old = found.get(0);
                    if (!old.isEqual(food)) {
                        getDaoFood().delete(old); // need to delete/create because date may change too
                        old.copyFrom(food);
                        getDaoFood().create(old);
                        log.debug("FOOD: Updating record by _id: " + old.toString());
                        scheduleFoodChange();
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    getDaoFood().createOrUpdate(food);
                    log.debug("FOOD: New record: " + food.toString());
                    scheduleFoodChange();
                    return true;
                }
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    public void delete(Food food) {
        try {
            getDaoFood().delete(food);
            scheduleFoodChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    public static void scheduleFoodChange() {
        class PostRunnable implements Runnable {
            public void run() {
                log.debug("Firing EventFoodChange");
                MainApp.bus().post(new EventFoodDatabaseChanged());
                scheduledFoodEventPost = null;
            }
        }
        // prepare task for execution in 1 sec
        // cancel waiting task to prevent sending multiple posts
        if (scheduledFoodEventPost != null)
            scheduledFoodEventPost.cancel(false);
        Runnable task = new PostRunnable();
        final int sec = 1;
        scheduledFoodEventPost = foodEventWorker.schedule(task, sec, TimeUnit.SECONDS);

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
            Food food = new Food();
            if (trJson.has("type") && trJson.getString("type").equals("food")) {
                if (trJson.has("_id"))
                    food._id = trJson.getString("_id");
                if (trJson.has("category"))
                    food.category = trJson.getString("category");
                if (trJson.has("subcategory"))
                    food.subcategory = trJson.getString("subcategory");
                if (trJson.has("name"))
                    food.name = trJson.getString("name");
                if (trJson.has("unit"))
                    food.units = trJson.getString("unit");
                if (trJson.has("portion"))
                    food.portion = trJson.getDouble("portion");
                if (trJson.has("carbs"))
                    food.carbs = trJson.getInt("carbs");
                if (trJson.has("gi"))
                    food.gi = trJson.getInt("gi");
                if (trJson.has("energy"))
                    food.energy = trJson.getInt("energy");
                if (trJson.has("protein"))
                    food.protein = trJson.getInt("protein");
                if (trJson.has("fat"))
                    food.fat = trJson.getInt("fat");
            }
            createOrUpdate(food);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    public void deleteFoodById(String _id) {
        Food stored = findFoodById(_id);
        if (stored != null) {
            log.debug("FOOD: Removing Food record from database: " + stored.toString());
            delete(stored);
            scheduleFoodChange();
        }
    }

    public Food findFoodById(String _id) {
        try {
            QueryBuilder<Food, Long> queryBuilder = getDaoFood().queryBuilder();
            Where where = queryBuilder.where();
            where.eq("_id", _id);
            PreparedQuery<Food> preparedQuery = queryBuilder.prepare();
            List<Food> list = getDaoFood().query(preparedQuery);

            if (list.size() == 1) {
                return list.get(0);
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }

}
