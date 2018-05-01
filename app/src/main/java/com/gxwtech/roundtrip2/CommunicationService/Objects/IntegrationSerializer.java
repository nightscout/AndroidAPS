package com.gxwtech.roundtrip2.CommunicationService.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * Created by Tim on 12/08/2016.
 * Required by Realm for converting to gson https://realm.io/docs/java/latest/#gson
 */
public class IntegrationSerializer implements JsonSerializer<Integration> {

    @Override
    public JsonElement serialize(Integration src, Type typeOfSrc, JsonSerializationContext context) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", src.getId());
        jsonObject.addProperty("type", src.getType());
        jsonObject.addProperty("state", src.getState());
        jsonObject.addProperty("action", src.getAction());
        jsonObject.addProperty("timestamp", src.getTimestamp().getTime());
        jsonObject.addProperty("date_updated", src.getDate_updated().getTime());
        jsonObject.addProperty("local_object", src.getLocal_object());
        jsonObject.addProperty("local_object_id", src.getLocal_object_id());
        jsonObject.addProperty("remote_id", src.getRemote_id());
        jsonObject.addProperty("details", src.getDetails());
        jsonObject.addProperty("remote_var1", src.getRemote_var1());
        jsonObject.addProperty("auth_code", src.getAuth_code());
        jsonObject.addProperty("toSync", src.getToSync());

        return jsonObject;
    }
}