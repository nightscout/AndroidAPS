package com.gxwtech.roundtrip2.ServiceMessageViewActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * static class to hold data for ServiceMessageViewListActivity
 */
public class ServiceMessageView {

    public static List<ServiceMessageViewItem> ITEMS = new ArrayList<>();

    public static Map<String, ServiceMessageViewItem> ITEM_MAP = new HashMap<>();

    public static void addItem(ServiceMessageViewItem item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.id, item);
    }

    public static ServiceMessageViewItem createItem(int position) {
        return new ServiceMessageViewItem(String.valueOf(position), "Item " + position, makeDetails(position));
    }

    public static String makeDetails(int position) {
        StringBuilder builder = new StringBuilder();
        builder.append("Details about Item: ").append(position);
        for (int i = 0; i < position; i++) {
            builder.append("\nMore details information here.");
        }
        return builder.toString();
    }

}
