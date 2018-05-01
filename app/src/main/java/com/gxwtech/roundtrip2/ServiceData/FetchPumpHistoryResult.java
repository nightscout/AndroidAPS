package com.gxwtech.roundtrip2.ServiceData;

import android.os.Bundle;

import com.gxwtech.roundtrip2.RoundtripService.medtronic.PumpData.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by geoff on 7/16/16.
 */
public class FetchPumpHistoryResult extends ServiceResult {
    public FetchPumpHistoryResult() {}

    public ArrayList<Page> getPageArray() {
        ArrayList<Bundle> pagebundles = map.getParcelableArrayList("pageArrayList");
        ArrayList<Page> pages = new ArrayList<>();
        for (Bundle b : pagebundles) {
            Page p = new Page();
            p.unpack(b);
            pages.add(p);
        }
        return pages;
    }

    public void setPageArray(List<Page> pageList) {
        ArrayList<Bundle> pageBundles = new ArrayList<>();
        for (Page p : pageList) {
            pageBundles.add(p.pack());
        }
        map.putParcelableArrayList("pageArrayList",pageBundles);
    }

}
