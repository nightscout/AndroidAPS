package info.nightscout.androidaps.plugins.general.automation;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import info.nightscout.androidaps.plugins.general.automation.dialogs.TriggerListAdapter;
import info.nightscout.androidaps.plugins.general.automation.triggers.DummyTrigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.Trigger;
import info.nightscout.androidaps.plugins.general.automation.triggers.TriggerConnector;

@RunWith(PowerMockRunner.class)
@PrepareForTest({})
public class ComposeTriggerTest {
    @Test
    public void testTriggerList() {
        TriggerConnector root = new TriggerConnector();

        // add some triggers
        Trigger t0 = new DummyTrigger();
        root.add(t0);
        Trigger t1 = new DummyTrigger();
        root.add(t1);
        Trigger t2 = new DummyTrigger();
        root.add(t2);

        Assert.assertEquals(3, root.size());
        Assert.assertEquals(t0, root.get(0));
        Assert.assertEquals(t1, root.get(1));
        Assert.assertEquals(t2, root.get(2));

        // remove a trigger
        root.remove(t1);

        Assert.assertEquals(2, root.size());
        Assert.assertEquals(t0, root.get(0));
        Assert.assertEquals(t2, root.get(1));
    }

    @Test
    public void testChangeConnector() {
        // initialize scenario
        TriggerConnector root = new TriggerConnector(TriggerConnector.Type.AND);
        Trigger[] t = new Trigger[4];
        for (int i = 0; i < t.length; ++i) {
            t[i] = new DummyTrigger();
            root.add(t[i]);
        }
        Assert.assertEquals(4, root.size());

        // change connector of t1,t2 from "and" to "or"
        Assert.assertEquals(root, t[2].getConnector());
        TriggerListAdapter.changeConnector(null, t[2], t[2].getConnector(), TriggerConnector.Type.OR);

        Assert.assertEquals(3, root.size());
        Assert.assertEquals(t[0], root.get(0));
        Assert.assertEquals(t[3], root.get(2));
        Assert.assertTrue(root.get(1) instanceof TriggerConnector);

        TriggerConnector newConnector = (TriggerConnector) root.get(1);
        Assert.assertEquals(2, newConnector.size());
        Assert.assertEquals(t[1], newConnector.get(0));
        Assert.assertEquals(t[2], newConnector.get(1));

        // undo
        Assert.assertEquals(newConnector, t[2].getConnector());
        TriggerListAdapter.changeConnector(null, t[2], t[2].getConnector(), TriggerConnector.Type.AND);
        Assert.assertEquals(4, root.size());
        for (int i = 0; i < 4; ++i) {
            Assert.assertEquals(t[i], root.get(i));
        }
    }

    @Test
    public void testConnectorSimplify() {
        // initialize scenario
        /*
         *   parent
         *   -> child
         *       -> t0
         *       -> t1
         */
        TriggerConnector parent = new TriggerConnector(TriggerConnector.Type.AND);
        TriggerConnector child = new TriggerConnector(TriggerConnector.Type.AND);
        Trigger t0 = new DummyTrigger();
        Trigger t1 = new DummyTrigger();
        child.add(t0);
        child.add(t1);
        parent.add(child);
        Assert.assertEquals(1, parent.size());
        Assert.assertEquals(child, parent.get(0));
        Assert.assertEquals(2, child.size());
        Assert.assertEquals(t0, child.get(0));
        Assert.assertEquals(t1, child.get(1));

        // simplify
        parent.simplify();

        /*
         *   parent
         *   -> t0
         *   -> t1
         */
        Assert.assertEquals(2, parent.size());
        Assert.assertEquals(t0, parent.get(0));
        Assert.assertEquals(t1, parent.get(1));

        // add a new child at position 1
        /*
         *   parent
         *   -> t0
         *   -> newChild
         *      -> t2
         *   -> t1
         */
        TriggerConnector newChild = new TriggerConnector(TriggerConnector.Type.AND);
        Trigger t2 = new DummyTrigger();
        newChild.add(t2);
        parent.add(1, newChild);

        // simplify
        parent.simplify();

        /*
         *   parent
         *   -> t0
         *   -> t2
         *   -> t1
         */
        Assert.assertEquals(3, parent.size());
        Assert.assertEquals(t0, parent.get(0));
        Assert.assertEquals(t2, parent.get(1));
        Assert.assertEquals(t1, parent.get(2));
    }
}