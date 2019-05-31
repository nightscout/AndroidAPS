package com.eveningoutpost.dexdrip.tidepool;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

// jamorham

public class InfoInterceptor implements Interceptor {

    private String tag = "interceptor";

    public InfoInterceptor(String tag) {
        this.tag = tag;
    }

    @Override
    public Response intercept(@NonNull final Chain chain) throws IOException {
        final Request request = chain.request();
        if (request != null && request.body() != null) {
            Log.d(tag, "Interceptor Body size: " + request.body().contentLength());
            //} else {
            //  UserError.Log.d(tag,"Null request body in InfoInterceptor");
        }
        return chain.proceed(request);
    }
}
