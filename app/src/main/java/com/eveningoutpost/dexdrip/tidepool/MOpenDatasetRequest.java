package com.eveningoutpost.dexdrip.tidepool;

import com.google.gson.annotations.Expose;

import java.util.TimeZone;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.utils.T;

import static com.eveningoutpost.dexdrip.Models.JoH.getTimeZoneOffsetMs;

public class MOpenDatasetRequest extends BaseMessage {

    static final String UPLOAD_TYPE = "continuous";

    @Expose
    public String deviceId;
    @Expose
    public String time = DateUtil.toFormatAsUTC(info.nightscout.androidaps.utils.DateUtil.now());
    @Expose
    public int timezoneOffset = (int) (getTimeZoneOffsetMs() / T.mins(1).msecs());
    @Expose
    public String type = "upload";
    //public String byUser;
    @Expose
    public ClientInfo client = new ClientInfo();
    @Expose
    public String computerTime = DateUtil.toFormatNoZone(info.nightscout.androidaps.utils.DateUtil.now());
    @Expose
    public String dataSetType = UPLOAD_TYPE;  // omit for "normal"
    @Expose
    public String[] deviceManufacturers = {((PluginBase) (ConfigBuilderPlugin.getPlugin().getActiveBgSource())).getName()};
    @Expose
    public String deviceModel = ((PluginBase) (ConfigBuilderPlugin.getPlugin().getActiveBgSource())).getName();
    @Expose
    public String[] deviceTags = {"bgm", "cgm", "insulin-pump"};
    @Expose
    public Deduplicator deduplicator = new Deduplicator();
    @Expose
    public String timeProcessing = "none";
    @Expose
    public String timezone = TimeZone.getDefault().getID();
    @Expose
    public String version = BuildConfig.VERSION_NAME;

    class ClientInfo {
        @Expose
        final String name = BuildConfig.APPLICATION_ID;
        @Expose
        final String version = "0.1.0"; // TODO: const it
    }

    class Deduplicator {
        @Expose
        final String name = "org.tidepool.deduplicator.dataset.delete.origin";
    }

    static boolean isNormal() {
        return UPLOAD_TYPE.equals("normal");
    }
}
