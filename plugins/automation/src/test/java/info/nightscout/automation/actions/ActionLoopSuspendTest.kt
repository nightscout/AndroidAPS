package info.nightscout.automation.actions

import info.nightscout.automation.R
import info.nightscout.automation.elements.InputDuration
import info.nightscout.interfaces.queue.Callback
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopSuspendTest : ActionsTestBase() {

    lateinit var sut: ActionLoopSuspend

    @BeforeEach
    fun setup() {

        `when`(context.getString(info.nightscout.core.ui.R.string.suspendloop)).thenReturn("Suspend loop")
        `when`(rh.gs(R.string.suspendloopforXmin)).thenReturn("Suspend loop for %d min")
        `when`(context.getString(R.string.alreadysuspended)).thenReturn("Already suspended")

        sut = ActionLoopSuspend(injector)
    }

    @Test fun friendlyNameTest() {
        Assertions.assertEquals(info.nightscout.core.ui.R.string.suspendloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        sut.minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        Assertions.assertEquals("Suspend loop for 30 min", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assertions.assertEquals(R.drawable.ic_pause_circle_outline_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(loopPlugin.isSuspended).thenReturn(false)
        sut.minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendLoop(30)

        // another call should keep it suspended, no more invocations
        `when`(loopPlugin.isSuspended).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendLoop(30)
    }

    @Test fun applyTest() {
        val a = ActionLoopSuspend(injector)
        a.minutes = InputDuration(20, InputDuration.TimeUnit.MINUTES)
        val b = ActionLoopSuspend(injector)
        b.apply(a)
        Assertions.assertEquals(20, b.minutes.getMinutes().toLong())
    }

    @Test fun hasDialogTest() {
        val a = ActionLoopSuspend(injector)
        Assertions.assertTrue(a.hasDialog())
    }
}