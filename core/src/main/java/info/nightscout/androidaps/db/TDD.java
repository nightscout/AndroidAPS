package info.nightscout.androidaps.db;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Locale;

import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.resources.ResourceHelper;

/**
 * Created by mike on 20.09.2017.
 */


@DatabaseTable(tableName = "TDDs")
public class TDD {

    @DatabaseField(id = true)
    public long date;

    @DatabaseField
    public double bolus;

    @DatabaseField
    public double basal;

    @DatabaseField
    public double total;


    public double getTotal() {
        return (total > 0d) ? total : (bolus + basal);
    }


    public TDD() {
    }

    public TDD(long date, double bolus, double basal, double total) {
        this.date = date;
        this.bolus = bolus;
        this.basal = basal;
        this.total = total;
    }


    public String toString(DateUtil dateUtil) {
        return "TDD [" +
                "date=" + date +
                "date(str)=" + dateUtil.dateAndTimeString(date) +
                ", bolus=" + bolus +
                ", basal=" + basal +
                ", total=" + total +
                ']';
    }

    public String toText(ResourceHelper resourceHelper, DateUtil dateUtil) {
        return resourceHelper.gs(R.string.tddformat, dateUtil.dateStringShort(date), total, bolus, basal);
    }

    public String toText(ResourceHelper resourceHelper, int days) {
        return resourceHelper.gs(R.string.tddformat, String.format(Locale.getDefault(), "%d ", days) + resourceHelper.gs(R.string.days), total, bolus, basal);
    }
}
