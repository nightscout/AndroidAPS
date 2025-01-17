package app.aaps.plugins.automation.actions

import app.aaps.core.interfaces.queue.Callback
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ActionLoopSuspendTest : ActionsTestBase() {

    lateinit var sut: ActionLoopSuspend

    @BeforeEach
    fun setup() {

        `when`(rh.gs(app.aaps.core.ui.R.string.suspendloop)).thenReturn("Suspend loop")
        `when`(rh.gs(R.string.suspendloopforXmin)).thenReturn("Suspend loop for %d min")
        `when`(rh.gs(R.string.alreadysuspended)).thenReturn("Already suspended")

        sut = ActionLoopSuspend(injector)
    }

    @Test fun friendlyNameTest() {
        assertThat(sut.friendlyName()).isEqualTo(app.aaps.core.ui.R.string.suspendloop)
    }

    @Test fun shortDescriptionTest() {
        sut.minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        assertThat(sut.shortDescription()).isEqualTo("Suspend loop for 30 min")
    }

    @Test fun iconTest() {
        assertThat(sut.icon()).isEqualTo(R.drawable.ic_pause_circle_outline_24dp)
    }

    @Test fun doActionTest() {
        `when`(loopPlugin.isSuspended).thenReturn(false)
        sut.minutes = InputDuration(30, InputDuration.TimeUnit.MINUTES)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendLoop(anyInt(), anyObject(), anyObject(), anyObject(), anyObject())

        // another call should keep it suspended, no more invocations
        `when`(loopPlugin.isSuspended).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendLoop(anyInt(), anyObject(), anyObject(), anyObject(), anyObject())
    }

    @Test fun applyTest() {
        val a = ActionLoopSuspend(injector)
        a.minutes = InputDuration(20, InputDuration.TimeUnit.MINUTES)
        val b = ActionLoopSuspend(injector)
        b.apply(a)
        assertThat(b.minutes.getMinutes().toLong()).isEqualTo(20)
    }

    @Test fun hasDialogTest() {
        val a = ActionLoopSuspend(injector)
        assertThat(a.hasDialog()).isTrue()
    }
}
