package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Created by mike on 20.09.2017.
 */


@DatabaseTable(tableName = DatabaseHelper.DATABASE_TDDS)
public class TDD {
    private static Logger log = LoggerFactory.getLogger(TDD.class);

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public double bolus;

    @DatabaseField
    public double basal;

    @DatabaseField
    public double total;


    public double getTotal(){
        return (total > 0d) ? total:(bolus+basal);
    }


    public TDD() { }

    public TDD(long date, double bolus, double basal, double total){
        this.date = date;
        this.bolus = bolus;
        this.basal = basal;
        this.total = total;
    }
}
