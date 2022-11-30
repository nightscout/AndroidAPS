package info.nightscout.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.automation.triggers.Trigger
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerDummy
import org.junit.Assert
import org.junit.jupiter.api.Test

class ComposeTriggerTest : TestBase() {

    var injector: HasAndroidInjector = HasAndroidInjector { AndroidInjector { } }

    @Test fun testTriggerList() {
        val root = TriggerConnector(injector)

        // add some triggers
        val t0: Trigger = TriggerDummy(injector)
        root.list.add(t0)
        val t1: Trigger = TriggerDummy(injector)
        root.list.add(t1)
        val t2: Trigger = TriggerDummy(injector)
        root.list.add(t2)
        Assert.assertEquals(3, root.size())
        Assert.assertEquals(t0, root.list[0])
        Assert.assertEquals(t1, root.list[1])
        Assert.assertEquals(t2, root.list[2])

        // remove a trigger
        root.list.remove(t1)
        Assert.assertEquals(2, root.size())
        Assert.assertEquals(t0, root.list[0])
        Assert.assertEquals(t2, root.list[1])
    }

    @Test
    fun testChangeConnector() {
        // initialize scenario
        val root = TriggerConnector(injector, TriggerConnector.Type.AND)
        val t = arrayOfNulls<Trigger>(4)
        for (i in t.indices) {
            t[i] = TriggerDummy(injector)
            root.list.add(t[i]!!)
        }
        Assert.assertEquals(4, root.size())
    }
}