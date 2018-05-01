package com.gxwtech.roundtrip2.CommunicationService.Objects;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;
import io.realm.annotations.Ignore;

/**
 * Created by Tim on 03/09/2015.
 */
public class TempBasal extends RealmObject {


    public Double getRate() {
        return rate;
    }
    public void setRate(Double rate) {
        this.rate = rate;
    }
    public Integer getDuration() {
        return duration;
    }
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    public Date getStart_time() {
        return start_time;
    }
    public void setStart_time(Date start_time) {
        this.start_time = start_time;
    }
    public String getBasal_adjustemnt() {
        return basal_adjustemnt;
    }
    public void setBasal_adjustemnt(String basal_adjustemnt) {
        this.basal_adjustemnt = basal_adjustemnt;
    }
    public String getAps_mode() {
        return aps_mode;
    }
    public void setAps_mode(String aps_mode) {
        this.aps_mode = aps_mode;
    }
    public String getId() {
        return id;
    }
    public Date getTimestamp() {
        return timestamp;
    }

    private String   id                 = UUID.randomUUID().toString();
    private Double   rate               = 0D;    //Temp Basal Rate for (U/hr) mode
    private Integer  duration           = 0;     //Duration of Temp
    private Date     start_time;                 //When the Temp Basal started
    private String   basal_adjustemnt   = "";    //High or Low temp
    private String   aps_mode;

    @Ignore
    public Date     timestamp        = new Date();

    public static TempBasal getTempBasalByID(String uuid, Realm realm) {
        RealmResults<TempBasal> results = realm.where(TempBasal.class)
                .equalTo("id", uuid)
                .findAllSorted("start_time", Sort.DESCENDING);

        if (results.isEmpty()) {
            return null;
        } else {
            return results.first();
        }
    }

    public static TempBasal last(Realm realm) {
        RealmResults<TempBasal> results = realm.where(TempBasal.class)
                .findAllSorted("start_time", Sort.DESCENDING);

        if (results.isEmpty()) {
            return new TempBasal();     //returns an empty TempBasal, other than null
        } else {
            return results.first();
        }
    }

    public static TempBasal lastActive(Realm realm) {
        RealmResults<TempBasal> results = realm.where(TempBasal.class)
                .findAllSorted("start_time", Sort.DESCENDING);

        if (results.isEmpty()) {
            return null;
        } else {
            TempBasal tempBasal = results.first();
            Integration integration = Integration.getIntegration("pump","temp_Basal",tempBasal.getId(),realm);

            if (integration.getState().equals("set")){
                return tempBasal;
            } else {
                return null;
            }
        }
    }

    public static TempBasal getCurrentActive(Date atThisDate, Realm realm) {
        RealmResults<TempBasal> results = realm.where(TempBasal.class)
                .findAllSorted("start_time", Sort.DESCENDING);

        TempBasal last = null;
        if (!results.isEmpty()) last = results.first();
        if (last != null && last.isactive(atThisDate)){
            return last;
        } else {
            return new TempBasal();     //returns an empty TempBasal, other than null or inactive basal
        }
    }

    public boolean isactive(Date atThisDate){
        if (atThisDate == null) atThisDate = new Date();

        if (start_time == null){ return false;}

        Date fur = new Date(start_time.getTime() + duration * 60000);
        if (fur.after(atThisDate)){
            return true;
        } else {
            return false;
        }
    }

    public String ageFormattted(){
        Integer minsOld = age();
        if (minsOld > 1){
            return minsOld + " mins ago";
        } else {
            return minsOld + " min ago";
        }
    }

    public int age(){
        Date timeNow = new Date();
        return (int)(timeNow.getTime() - timestamp.getTime()) /1000/60;                             //Age in Mins the Temp Basal was suggested
    }

    public Date endDate(){
        Date endedAt = new Date(start_time.getTime() + (duration * 1000 * 60));                     //The date this Temp Basal ended
        return endedAt;
    }

    public Long durationLeft(){
        if (start_time != null) {
            Date timeNow = new Date();
            Long min_left = ((start_time.getTime() + duration * 60000) - timeNow.getTime()) / 60000;   //Time left to run in Mins
            return min_left;
        } else {
            return duration.longValue();
        }
    }

    public static List<TempBasal> getTempBasalsDated(Date dateFrom, Date dateTo, Realm realm) {
        RealmResults<TempBasal> results = realm.where(TempBasal.class)
                .greaterThanOrEqualTo("start_time", dateFrom)
                .lessThanOrEqualTo("start_time", dateTo)
                .findAllSorted("start_time", Sort.DESCENDING);
        return results;
    }

    public boolean checkIsCancelRequest() {
        if (rate.equals(0D) && duration.equals(0)){
            return true;
        } else {
            return false;
        }
    }

}

