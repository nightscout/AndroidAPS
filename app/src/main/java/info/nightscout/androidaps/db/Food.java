package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Created by mike on 20.09.2017.
 */


@DatabaseTable(tableName = DatabaseHelper.DATABASE_FOODS)
public class Food {
    private static Logger log = LoggerFactory.getLogger(Food.class);

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

    public Food() {
        key = System.currentTimeMillis();
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
}
