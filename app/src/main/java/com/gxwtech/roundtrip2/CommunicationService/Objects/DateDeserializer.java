package com.gxwtech.roundtrip2.CommunicationService.Objects;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;

import java.util.Date;

/**
 * Created by Tim on 16/08/2016.
 * Used by GSON to Deserializer Dates
 */
public class DateDeserializer implements JsonDeserializer<Date> {
    public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        Date date = null;
        date = new Date(json.getAsJsonPrimitive().getAsLong());
        return date;
    }
}
