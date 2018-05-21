package info.nightscout.androidaps.setupwizard;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.setupwizard.elements.SWItem;

public class SWScreen {

    int header;
    List<SWItem> items = new ArrayList<>();
    SWValidator validator;
    SWValidator visibility;
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

    public SWScreen visibility(SWValidator visibility) {
        this.visibility = visibility;
        return this;
    }

    public void processVisibility() {
        for (SWItem i : items)
            i.processVisibility();
    }
}
