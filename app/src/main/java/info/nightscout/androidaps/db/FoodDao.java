package info.nightscout.androidaps.db;

import android.content.Context;

import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.support.ConnectionSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by triplem on 04.01.18.
 */

public class FoodDao extends BaseDaoImpl<Food, Long> {

    private static final Logger log = LoggerFactory.getLogger(FoodDao.class);

    public FoodDao(ConnectionSource source) throws SQLException {
        super(source, Food.class);
    }

    /**
     * Static instantiation methods. The database connection is accessed via
     * the OpenHelperManager which keeps a count of the number of objects
     * using the connection. Thus every call to connect() must be matched by
     * a call to release() once the session is done.
     */
    public static FoodDao connect(Context context) {
        return with(OpenHelperManager.getHelper(context, DatabaseHelper.class)
                .getConnectionSource());
    }

    public static FoodDao with(ConnectionSource connection) {
        try {
            return (FoodDao) DaoManager.createDao(connection, Food.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Releasing the DAO flags the connection manager that the DAO is no
     * longer using the connection. When the connection count is zero, the
     * connection manager will close the database.
     */
    public void release() {
        OpenHelperManager.releaseHelper();
    }

    /**
     *
     * @return
     *
     * @deprecated should use queryForAll instead, which is a standard method of the ORMLite DAO
     */
    public List<Food> getFoodData() {
        try {
            QueryBuilder<Food, Long> queryBuilder = this.queryBuilder();
            PreparedQuery<Food> preparedQuery = queryBuilder.prepare();
            return this.query(preparedQuery);
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }

        return new ArrayList<>();
    }

    public boolean createOrUpdateByNS(Food food) {
        try {
            // find by NS _id
            if (food._id != null) {
                Food old = this.findByNSId(food._id);

                if (old != null) {
                    if (!old.isEqual(food)) {
                        this.delete(old); // need to delete/create because date may change too
                        old.copyFrom(food);
                        this.create(old);
                        log.debug("FOOD: Updating record by _id: " + old.toString());
                        FoodHelper.scheduleFoodChange();
                        return true;
                    } else {
                        return false;
                    }
                }
            }
            this.createOrUpdate(food);
            log.debug("FOOD: New record: " + food.toString());
            FoodHelper.scheduleFoodChange();
            return true;
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return false;
    }

    /**
     * deletes an entry by its NS Id.
     *
     * Basically a convenience method for findByNSId and deleteFood.
     *
     * should be moved to a Service
     *
     * @param _id
     */
    public void deleteByNSId(String _id) throws SQLException {
        Food stored = findByNSId(_id);
        if (stored != null) {
            log.debug("FOOD: Removing Food record from database: " + stored.toString());
            this.deleteFood(stored);
        }
    }

    /**
     * deletes the food and sends the foodChange Event
     *
     * should be moved ot a Service
     *
     * @param food
     */
    public void deleteFood(Food food) {
        try {
            this.delete(food);
            FoodHelper.scheduleFoodChange();
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
    }

    /**
     * finds food by its NS Id.
     *
     * @param _id
     * @return
     */
    public Food findByNSId(String _id) {
        try {
            List<Food> list = this.queryForEq("_id", _id);

            if (list.size() == 1) { // really? if there are more then one result, then we do not return anything...
                return list.get(0);
            }
        } catch (SQLException e) {
            log.error("Unhandled exception", e);
        }
        return null;
    }



}

