package info.nightscout.androidaps.plugins.general.automation.elements;

import android.widget.LinearLayout;

import java.util.ArrayList;

public class LayoutBuilder {
    ArrayList<Element> mElements = new ArrayList<>();

    public LayoutBuilder add(Element element) {
        mElements.add(element);
        return this;
    }

    public LayoutBuilder add(Element element, boolean condition) {
        if (condition) mElements.add(element);
        return this;
    }

    public void build(LinearLayout layout) {
        for (Element e : mElements) {
            e.addToLayout(layout);
        }
    }

}
