package info.nightscout.androidaps.db;

/**
 * This class should get registered in the MainApp.
 */

public class DataServiceManager {

    private static final DataServiceManager INSTANCE = new DataServiceManager();

    private FoodService foodService;

    public static DataServiceManager getInstance() {
        return INSTANCE;
    }

    public FoodService getFoodService() {
        if (this.foodService == null) {
            this.foodService = new FoodService();
        }

        return foodService;
    }

}
