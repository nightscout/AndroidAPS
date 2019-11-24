package info.nightscout.androidaps.plugins.pump.omnipod.driver.db;

import info.nightscout.androidaps.db.DbObjectBase;

/**
 * Created by andy on 24.11.2019
 */
public class PodDbEntry implements DbObjectBase {

    private long dateTime;
    private PodDbEntryType podDbEntryType;
    private String shortDescription;
    private String request;
    private long dateTimeResponse;
    private String response;
    private boolean success;
    private long pumpId;

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

    public PodDbEntry(long requestTime, PodDbEntryType podDbEntryType, String shortDescription, String request, Long dateTimeResponse, String response) {
        this.dateTime = requestTime;
        this.podDbEntryType = podDbEntryType;
        this.shortDescription = shortDescription;
        this.request = request;
        this.dateTimeResponse = dateTimeResponse;
        this.response = response;
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

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public long getDateTimeResponse() {
        return dateTimeResponse;
    }

    public void setDateTimeResponse(long dateTimeResponse) {
        this.dateTimeResponse = dateTimeResponse;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
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
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }
}
