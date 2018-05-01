package com.gxwtech.roundtrip2.CommunicationService.Objects;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;


/**
 * Created by Tim on 05/08/2016.
 */
public class Bolus extends RealmObject {

    public Double getValue() {
        return value;
    }
    public void setValue(Double value) {
        this.value = value;
    }
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }
    public Date getTimestamp() {
        return timestamp;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getId() {
        return id;
    }

    private String id = UUID.randomUUID().toString();
    private Date timestamp  = new Date();
    private String type;
    private Double value;

    public static Bolus getBolus(String uuid, Realm realm) {
        RealmResults<Bolus> results = realm.where(Bolus.class)
                .equalTo("id", uuid)
                .findAllSorted("timestamp", Sort.DESCENDING);

        if (results.isEmpty()) {
            return null;
        } else {
            return results.first();
        }
    }

    public static List<Bolus> getBolusList(Realm realm){
        RealmResults<Bolus> results = realm.where(Bolus.class)
                .findAllSorted("timestamp", Sort.DESCENDING);
        if (results.isEmpty()) {
            return null;
        } else {
            return results;
        }
    }

    public static List<Bolus> getBolusesBetween(Date dateFrom, Date dateTo, Realm realm) {
        RealmResults<Bolus> results = realm.where(Bolus.class)
                .greaterThanOrEqualTo("timestamp", dateFrom)
                .lessThanOrEqualTo("timestamp", dateTo)
                .findAllSorted("timestamp", Sort.DESCENDING);
        return results;
    }

    public static Double getBolusCountBetween(Date dateFrom, Date dateTo, Realm realm) {
        Number result = realm.where(Bolus.class)
                .greaterThanOrEqualTo("timestamp", dateFrom)
                .lessThanOrEqualTo("timestamp", dateTo)
                .sum("value");
        return result.doubleValue();
    }


    public static class sortByDateTimeOld2YoungOLD implements Comparator<Bolus> {
        @Override
        public int compare(Bolus o1, Bolus o2) {
            return o2.getTimestamp().compareTo(o1.getTimestamp());
        }
    }
}