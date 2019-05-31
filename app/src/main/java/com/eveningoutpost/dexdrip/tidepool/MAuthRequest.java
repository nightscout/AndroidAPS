package com.eveningoutpost.dexdrip.tidepool;

// jamorham

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.utils.SP;
import okhttp3.Credentials;

import static com.eveningoutpost.dexdrip.Models.JoH.emptyString;

public class MAuthRequest extends BaseMessage {

    public static String getAuthRequestHeader() {

        final String username = SP.getString(R.string.key_tidepool_username, null);
        final String password = SP.getString(R.string.key_tidepool_password, null);

        if (emptyString(username) || emptyString(password)) return null;
        return Credentials.basic(username.trim(), password);
    }
}



