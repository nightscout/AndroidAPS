package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import info.nightscout.androidaps.db.DbObjectBase;

/**
 * Created by andy on 24.11.2019
 */
public class PodDbEntry implements DbObjectBase {

    private long dateTime;
    private PodDbEntryType podDbEntryType;
    private String data;
    private boolean success;
    private long pumpId;
    private Boolean successConfirmed;

    //private String request;
    //private long dateTimeResponse;
    //private String response;


    public PodDbEntry(PodDbEntryType podDbEntryType) {
        this.dateTime = System.currentTimeMillis();
        this.podDbEntryType = podDbEntryType;
        generatePumpId();
    }


    public PodDbEntry(long requestTime, PodDbEntryType podDbEntryType) {
        this.dateTime = requestTime;
        this.podDbEntryType = podDbEntryType;
        generatePumpId();
    }



    public long getDateTime() {
        return dateTime;
    }

    public void setDateTime(long dateTime) {
        this.dateTime = dateTime;
    }

    public PodDbEntryType getPodDbEntryType() {
        return podDbEntryType;
    }

    public void setPodDbEntryType(PodDbEntryType podDbEntryType) {
        this.podDbEntryType = podDbEntryType;
    }


    @Override
    public long getDate() {
        return this.dateTime;
    }

    @Override
    public long getPumpId() {
        return pumpId;
    }

    private void generatePumpId() {
        // TODO
        // yyyymmddhhMMssxx (time and xx is code of podDbEntryType)
    }


}
