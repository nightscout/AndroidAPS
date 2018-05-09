package info.nightscout.androidaps.startupwizard;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;

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

    public void processVisibility() {
        for (SWItem i : items)
            i.processVisibility();
    }
}
