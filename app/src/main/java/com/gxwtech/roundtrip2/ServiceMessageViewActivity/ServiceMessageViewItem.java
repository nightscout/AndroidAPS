package com.gxwtech.roundtrip2.ServiceMessageViewActivity;

import com.gxwtech.roundtrip2.ServiceData.ServiceMessage;

/**
 * Created by geoff on 7/4/16.
 */

public class ServiceMessageViewItem {
    public final String id;
    public final String content;
    public final String details;

    public ServiceMessageViewItem(String id, String content, String details) {
        this.id = id;
        this.content = content;
        this.details = details;
    }

    @Override
    public String toString() {
        return content;
    }
}

