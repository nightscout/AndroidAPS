package com.gxwtech.roundtrip2.CommunicationService.Objects;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;


/**
 * Created by Tim on 16/01/2016.
 * This table holds Integration details of an object
 * one object may have multiple Integrations
 */

public class Integration extends RealmObject {

    public String getId() {
        return id;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getState() {
        return state;
    }
    public void setState(String state) {
        this.state = state;
    }
    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }
    public Date getTimestamp() {
        return timestamp;
    }
    public Date getDate_updated() {
        return date_updated;
    }
    public void setDate_updated(Date date_updated) {
        this.date_updated = date_updated;
    }
    public String getLocal_object() {
        return local_object;
    }
    public void setLocal_object(String local_object) {
        this.local_object = local_object;
    }
    public String getLocal_object_id() {
        return local_object_id;
    }
    public void setLocal_object_id(String local_object_id) {
        this.local_object_id = local_object_id;
    }
    public String getRemote_id() {
        return remote_id;
    }
    public void setRemote_id(String remote_id) {
        this.remote_id = remote_id;
    }
    public String getDetails() {
        return details;
    }
    public void setDetails(String details) {
        this.details = details;
    }
    public String getRemote_var1() {
        return remote_var1;
    }
    public void setRemote_var1(String remote_var1) {
        this.remote_var1 = remote_var1;
    }
    public String getAuth_code() {
        return auth_code;
    }
    public void setAuth_code(String auth_code) {
        this.auth_code = auth_code;
    }
    public Boolean getToSync() {
        return toSync;
    }
    public void setToSync(Boolean toSync) {
        this.toSync = toSync;
    }


    private String id;
    private String type;                        //What Integration is this?
    private String state;                       //Current state this Integration is in
    private String action;                      //Requested action for this object
    private Date timestamp;                     //Date created
    private Date date_updated;                  //Last time the Integration for this object was updated
    private String local_object;                //What rt2 object is this? Bolus, TempBasal etc
    private String local_object_id;             //HAPP ID for this object
    private String remote_id;                   //ID provided by the remote system
    private String details;                     //The details of this Integration attempt
    private String remote_var1;                 //Misc information about this Integration
    private String auth_code;                   //auth_code if required
    private Boolean toSync;                     //Do we need to sync this object?

    public Integration(){
        id                  = UUID.randomUUID().toString();
        timestamp           = new Date();
        date_updated        = new Date();
        remote_var1         =   "";
        state               =   "";
        toSync              = true;
    }

    public Integration(String type, String local_object, String local_object_id){
        id                  = UUID.randomUUID().toString();
        timestamp           = new Date();
        date_updated        = new Date();
        remote_var1         =   "";
        state               =   "";
        this.type           =   type;
        this.local_object     =   local_object;
        this.local_object_id  =   local_object_id;
        toSync              = true;
    }

    public static Integration getIntegration(String type, String local_object, String rt2_id, Realm realm){
        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("type", type)
                .equalTo("local_object", local_object)
                .equalTo("local_object_id", rt2_id)
                .findAllSorted("date_updated", Sort.DESCENDING);

        if (results.isEmpty()) {                                                                    //We dont have an Integration for this item
            return null;

        } else {                                                                                    //Found an Integration, return it
            return results.first();
        }
    }

    public static List<Integration> getIntegrationsFor(String local_object, String local_object_id, Realm realm) {
        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("local_object", local_object)
                .equalTo("local_object_id", local_object_id)
                .findAllSorted("date_updated", Sort.DESCENDING);
        return results;
    }

    public static List<Integration> getIntegrationsHoursOld(String type, String local_object,  int inLastHours, Realm realm) {
        Date now        = new Date();
        Date hoursAgo   = new Date(now.getTime() - (inLastHours * 60 * 60 * 1000));

        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("local_object", local_object)
                .equalTo("type", type)
                .greaterThanOrEqualTo("date_updated", hoursAgo)
                .lessThanOrEqualTo("date_updated", now)
                .findAllSorted("date_updated", Sort.DESCENDING);
        return results;
    }

    public static List<Integration> getIntegrationsToSync(String type, String local_object, Realm realm) {
        if (local_object != null) {
            RealmResults<Integration> results = realm.where(Integration.class)
                    .equalTo("local_object", local_object)
                    .equalTo("type", type)
                    .equalTo("toSync", Boolean.TRUE)
                    .findAllSorted("date_updated", Sort.DESCENDING);
            return results;
        } else {
            RealmResults<Integration> results = realm.where(Integration.class)
                    .equalTo("type", type)
                    .equalTo("toSync", Boolean.TRUE)
                    .findAllSorted("date_updated", Sort.DESCENDING);
            return results;
        }
    }

    public static Integration getIntegrationByID(String uuid, Realm realm) {
        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("id", uuid)
                .findAllSorted("timestamp", Sort.DESCENDING);
        if (results.isEmpty()) {
            return null;
        } else {
            return results.first();
        }
    }

    public static List<Integration> getUpdatedInLastMins(Integer inLastMins, String type, Realm realm) {
        Date now = new Date();
        Date minsAgo = new Date(now.getTime() - (inLastMins * 60 * 1000));

        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("type", type)
                .greaterThanOrEqualTo("date_updated", minsAgo)
                .lessThanOrEqualTo("date_updated", now)
                .findAllSorted("date_updated", Sort.DESCENDING);
        return results;
    }

    public static List<Integration> getIntegrationsWithErrors(String type, Realm realm) {
        RealmResults<Integration> results = realm.where(Integration.class)
                .equalTo("type", type)
                .equalTo("state", "error")
                .findAllSorted("date_updated", Sort.DESCENDING);
        return results;
    }

   

}
