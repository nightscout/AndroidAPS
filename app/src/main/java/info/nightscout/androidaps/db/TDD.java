package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.utils.DateUtil;

/**
 * Created by mike on 20.09.2017.
 */


@DatabaseTable(tableName = DatabaseHelper.DATABASE_TDDS)
public class TDD {
    private static Logger log = LoggerFactory.getLogger(L.DATABASE);

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


    @Override
    public String toString() {
        return "TDD [" +
                "date=" + date +
                "date(str)=" + DateTimeUtil.toStringFromTimeInMillis(date) +
                ", bolus=" + bolus +
                ", basal=" + basal +
                ", total=" + total +
                ']';
    }

    public String toText() {
        return MainApp.gs(R.string.tddformat, DateUtil.dateStringShort(date), total, bolus, basal);
    }

    public String toText(int days) {
        return MainApp.gs(R.string.tddformat, String.format("%d ", days) + MainApp.gs(R.string.days), total, bolus, basal);
    }
}
