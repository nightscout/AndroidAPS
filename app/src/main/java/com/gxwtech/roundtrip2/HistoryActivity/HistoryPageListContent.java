package com.gxwtech.roundtrip2.HistoryActivity;

import android.os.Bundle;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by geoff on 6/12/16.
 */
public class HistoryPageListContent {
    public static final List<RecordHolder> ITEMS = new ArrayList<>();

    /**
     * A map of items, by ID.
     */
    public static final Map<String, RecordHolder> ITEM_MAP = new HashMap<>();

    static void addItem(Bundle recordBundle) {
        addItem(new RecordHolder(recordBundle));
    }

    private static void addItem(RecordHolder item) {
        ITEMS.add(item);
        ITEM_MAP.put(item.id, item);
    }

    private static String makeDetails(int position) {
        RecordHolder rh = ITEMS.get(position);
        if (rh == null) {
            return "(null)";
        }

        return makeDetails(rh.content);
    }

    private static String makeDetails(Bundle historyEntry) {
        Set<String> ignoredSet = new HashSet<>();
        ignoredSet.add("_type");
        ignoredSet.add("_stype");
        ignoredSet.add("_opcode");
        ignoredSet.add("timestamp");
        StringBuilder builder = new StringBuilder();
        int n = 0;
        for (String key : historyEntry.keySet()) {
            if (!ignoredSet.contains(key)) {
                builder.append(key);
                n++;
                if (n<historyEntry.keySet().size()-1) {
                    builder.append("\n");
                }
            }
        }
        return builder.toString();
    }

    public static class RecordHolder {
        public final String id;
        public final String dateAndName;
        public final Bundle content;
        public final String details;

        public RecordHolder(Bundle content) {
            id = String.format("%d",content.hashCode());
            String rawTimestamp = content.getString("timestamp","0000-00-00T00:00:00");
            int tspot = rawTimestamp.indexOf('T');
            StringBuilder dateAndNameBuilder = new StringBuilder();
            dateAndNameBuilder.append(rawTimestamp.substring(0,tspot-1));
            dateAndNameBuilder.append("\n");
            dateAndNameBuilder.append(rawTimestamp.substring(tspot+1,rawTimestamp.length()-1));
            dateAndNameBuilder.append("\n");
            String veryShortName = content.getString("_stype","(??????)");
            if (veryShortName.length() >= 12) {
                veryShortName = veryShortName.substring(0,12);
            }
            dateAndNameBuilder.append(veryShortName);
            this.dateAndName = dateAndNameBuilder.toString();
            this.content = content;
            details = makeDetails(content);
        }

        @Override
        public String toString() {
            return content.getString("_stype", "(unk)");
        }
    }

}

