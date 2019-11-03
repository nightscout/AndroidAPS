package info.nightscout.androidaps.plugins.general.food;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import info.nightscout.androidaps.utils.JsonHelper;

/**
 * Created by mike on 20.09.2017.
 */

@DatabaseTable(tableName = Food.TABLE_FOODS)
public class Food {
    static final String TABLE_FOODS = "Foods";

    @DatabaseField(id = true)
    public long key;

    @DatabaseField
    public boolean isValid = true;

    @DatabaseField
    public String _id; // NS _id

    @DatabaseField
    public String name;

    @DatabaseField
    public String category;

    @DatabaseField
    public String subcategory;

    // Example:
    // name="juice" portion=250 units="ml" carbs=12
    // means 250ml of juice has 12g of carbs

    @DatabaseField
    public double portion; // common portion in "units"

    @DatabaseField
    public int carbs; // in grams

    @DatabaseField
    public int fat = 0; // in grams

    @DatabaseField
    public int protein = 0; // in grams

    @DatabaseField
    public int energy = 0; // in kJ

    @DatabaseField
    public String units = "g";

    @DatabaseField
    public int gi; // not used yet

    private Food() {
        key = System.currentTimeMillis();
    }

    public static Food createFromJson(JSONObject json) throws JSONException {
        Food food = new Food();
        if ("food".equals(JsonHelper.safeGetString(json, "type"))) {
            food._id = JsonHelper.safeGetString(json, "_id");
            food.category = JsonHelper.safeGetString(json, "category");
            food.subcategory = JsonHelper.safeGetString(json, "subcategory");
            food.name = JsonHelper.safeGetString(json, "name");
            food.units = JsonHelper.safeGetString(json, "unit");
            food.portion = JsonHelper.safeGetDouble(json, "portion");
            food.carbs = JsonHelper.safeGetInt(json, "carbs");
            food.gi = JsonHelper.safeGetInt(json, "gi");
            food.energy = JsonHelper.safeGetInt(json, "energy");
            food.protein = JsonHelper.safeGetInt(json, "protein");
            food.fat = JsonHelper.safeGetInt(json, "fat");
        }

        return food;
    }

    public boolean isEqual(Food other) {
        if (portion != other.portion)
            return false;
        if (carbs != other.carbs)
            return false;
        if (fat != other.fat)
            return false;
        if (protein != other.protein)
            return false;
        if (energy != other.energy)
            return false;
        if (gi != other.gi)
            return false;
        if (!Objects.equals(_id, other._id))
            return false;
        if (!Objects.equals(name, other.name))
            return false;
        if (!Objects.equals(category, other.category))
            return false;
        if (!Objects.equals(subcategory, other.subcategory))
            return false;
       if (!Objects.equals(units, other.units))
            return false;
        return true;
    }

    public void copyFrom(Food other) {
        isValid = other.isValid;
        _id = other._id;
        name = other.name;
        category = other.category;
        subcategory = other.subcategory;
        portion = other.portion;
        carbs = other.carbs;
        fat = other.fat;
        protein = other.protein;
        energy = other.energy;
        units = other.units;
        gi = other.gi;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("_id=" + _id + ";");
        sb.append("isValid=" + isValid + ";");
        sb.append("name=" + name + ";");
        sb.append("category=" + category + ";");
        sb.append("subcategory=" + subcategory + ";");
        sb.append("portion=" + portion + ";");
        sb.append("carbs=" + carbs + ";");
        sb.append("protein=" + protein + ";");
        sb.append("energy=" + energy + ";");
        sb.append("units=" + units + ";");
        sb.append("gi=" + gi + ";");

        return sb.toString();
    }
}
