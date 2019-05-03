package info.nightscout.androidaps.plugins.general.automation.elements;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.MainApp;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MainApp.class})
public class LayoutBuilderTest {

    @Test
    public void addTest() {
        LayoutBuilder layoutBuilder = new LayoutBuilder();

        InputInsulin inputInsulin = new InputInsulin();
        layoutBuilder.add(inputInsulin);
        Assert.assertEquals(1, layoutBuilder.mElements.size());
    }

    @Test
    public void addConditionalTest() {
        LayoutBuilder layoutBuilder = new LayoutBuilder();

        InputInsulin inputInsulin = new InputInsulin();
        layoutBuilder.add(inputInsulin, true);
        Assert.assertEquals(1, layoutBuilder.mElements.size());
        layoutBuilder.add(inputInsulin, false);
        Assert.assertEquals(1, layoutBuilder.mElements.size());
    }
}