package com.eveningoutpost.dexdrip.tidepool;

import android.util.Log;

import com.eveningoutpost.dexdrip.store.FastStore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

// jamorham

// Callback template to reduce boiler plate

class TidepoolCallback<T> implements Callback<T> {

    final Session session;
    final String name;
    final Runnable onSuccess;

    Runnable onFailure;

    public TidepoolCallback(Session session, String name, Runnable onSuccess) {
        this.session = session;
        this.name = name;
        this.onSuccess = onSuccess;
    }

    TidepoolCallback<T> setOnFailure(final Runnable runnable) {
        this.onFailure = runnable;
        return this;
    }

    @Override
    public void onResponse(Call<T> call, Response<T> response) {
        if (response.isSuccessful() && response.body() != null) {
            Log.d(TidepoolUploader.TAG, name + " success");
            session.populateBody(response.body());
            session.populateHeaders(response.headers());
            if (onSuccess != null) {
                onSuccess.run();
            }
        } else {
            final String msg = name + " was not successful: " + response.code() + " " + response.message();
            Log.e(TidepoolUploader.TAG, msg);
            status(msg);
            if (onFailure != null) {
                onFailure.run();
            }
        }
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        final String msg = name + " Failed: " + t;
        Log.e(TidepoolUploader.TAG, msg);
        status(msg);
        if (onFailure != null) {
            onFailure.run();
        }
    }


    private static void status(final String status) {
        FastStore.getInstance().putS(TidepoolUploader.STATUS_KEY, status);
    }
}
