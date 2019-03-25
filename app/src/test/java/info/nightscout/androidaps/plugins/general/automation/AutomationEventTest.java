package info.nightscout.androidaps.plugins.general.automation;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.general.automation.actions.ActionLoopEnable;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnectorTest;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class AutomationEventTest {
    @Test
    public void testCloneEvent() throws CloneNotSupportedException {
        // create test object
        AutomationEvent event = new AutomationEvent();
        event.setTitle("Test");
        event.setTrigger(Trigger.instantiate(TriggerConnectorTest.oneItem));
        event.addAction(new ActionLoopEnable());

        // clone
        AutomationEvent clone = event.clone();

        // check title
        Assert.assertEquals(event.getTitle(), clone.getTitle());

        // check trigger
        Assert.assertNotNull(clone.getTrigger());
        Assert.assertFalse(event.getTrigger() == clone.getTrigger()); // not the same object reference
        Assert.assertEquals(event.getTrigger().getClass(), clone.getTrigger().getClass());
        // TODO: check trigger details

        // check action
        Assert.assertEquals(1, clone.getActions().size());
        Assert.assertFalse(event.getActions() == clone.getActions()); // not the same object reference
        // TODO: check action details
    }
}
