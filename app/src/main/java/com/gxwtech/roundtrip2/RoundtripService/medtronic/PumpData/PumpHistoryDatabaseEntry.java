package com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData;

import android.content.ContentValues;
import android.os.Bundle;

/**
 * Created by geoff on 6/17/16.
 */
public class PumpHistoryDatabaseEntry {
    public static final String key_timestamp = "timestamp";
    private String timestampString;
    public static final String key_pageNum = "pagenum";
    private int pageNum;
    public static final String key_pageOffset = "offset";
    private int pageOffset;
    public static final String key_recordType = "type";
    private String recordType;
    public static final String key_length = "length";
    private int length;
    public static String getTableInitString() {
        String rval = "(";
        rval += "id INTEGER PRIMARY KEY" + ", ";
        rval += key_timestamp + " TEXT" + ",";
        rval += key_pageNum + " INTEGER" + ",";
        rval += key_pageOffset + " INTEGER" + ",";
        rval += key_recordType + " TEXT" + ",";
        rval += key_length + " INTEGER" +")";
        return rval;
    }
    public PumpHistoryDatabaseEntry() {
    }
    public boolean initFromRecordBundle(int pageNum, Bundle bundle) {
        timestampString = bundle.getString("timestamp","00-00-00T00:00:00");
        this.pageNum = pageNum;
        pageOffset = bundle.getInt("foundAtOffset",-1);
        recordType = bundle.getString("_type","(unknown)");
        this.length = bundle.getInt("length",0);
        return true;
    }
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(key_length,length);
        values.put(key_pageNum,pageNum);
        values.put(key_pageOffset,pageOffset);
        values.put(key_recordType,recordType);
        values.put(key_timestamp,timestampString);
        return values;
    }


}
