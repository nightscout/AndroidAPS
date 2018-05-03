package info.nightscout.androidaps.startupwizard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainActivity;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.events.EventRefreshGui;
import info.nightscout.utils.LocaleHelper;
import info.nightscout.utils.SP;

public class SWScreen {

    int header;
    List<SWItem> items = new ArrayList<>();
    SWValidator validator;
    boolean skippable = false;

    public SWScreen(int header) {
        this.header = header;
    }

    public String getHeader() {
        return MainApp.gs(header);
    }

    public SWScreen skippable(boolean skippable) {
        this.skippable = skippable;
        return this;
    }

    public SWScreen add(SWItem newItem) {
        items.add(newItem);
        return this;
    }

    public SWScreen validator(SWValidator validator) {
        this.validator = validator;
        return this;
    }

    boolean isValid() {
        if (validator != null)
            return validator.isValid();
        return true;
    }

}
