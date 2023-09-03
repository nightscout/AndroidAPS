package info.nightscout.automation

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.automation.triggers.Trigger
import info.nightscout.automation.triggers.TriggerConnector
import info.nightscout.automation.triggers.TriggerDummy
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(3, root.size())
        Assertions.assertEquals(t0, root.list[0])
        Assertions.assertEquals(t1, root.list[1])
        Assertions.assertEquals(t2, root.list[2])

        // remove a trigger
        root.list.remove(t1)
        Assertions.assertEquals(2, root.size())
        Assertions.assertEquals(t0, root.list[0])
        Assertions.assertEquals(t2, root.list[1])
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
        Assertions.assertEquals(4, root.size())
    }
}