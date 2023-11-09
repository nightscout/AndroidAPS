package app.aaps.plugins.automation

import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.plugins.automation.triggers.TriggerConnector
import app.aaps.plugins.automation.triggers.TriggerDummy
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.Test

class ComposeTriggerTest : TestBase() {

    val injector: HasAndroidInjector = HasAndroidInjector { AndroidInjector { } }

    @Test fun testTriggerList() {
        val root = TriggerConnector(injector)

        // add some triggers
        val t0: Trigger = TriggerDummy(injector)
        root.list.add(t0)
        val t1: Trigger = TriggerDummy(injector)
        root.list.add(t1)
        val t2: Trigger = TriggerDummy(injector)
        root.list.add(t2)
        assertThat(root.list).containsExactly(t0, t1, t2).inOrder()

        // remove a trigger
        root.list.remove(t1)
        assertThat(root.list).containsExactly(t0, t2).inOrder()
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
        assertThat(root.size()).isEqualTo(4)
    }
}
