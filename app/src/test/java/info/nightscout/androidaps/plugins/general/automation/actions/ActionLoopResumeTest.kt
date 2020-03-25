package info.nightscout.androidaps.plugins.general.automation.actions

import info.nightscout.androidaps.R
import info.nightscout.androidaps.queue.Callback
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class ActionLoopResumeTest : ActionsTestBase() {

    lateinit var sut: ActionLoopResume

    @Before
    fun setup() {

        `when`(virtualPumpPlugin.specialEnableCondition()).thenReturn(true)
        `when`(resourceHelper.gs(R.string.resumeloop)).thenReturn("Resume loop")
        `when`(resourceHelper.gs(R.string.notsuspended)).thenReturn("Not suspended")

        sut = ActionLoopResume(injector)
    }

    @Test fun friendlyNameTest() {
        Assert.assertEquals(R.string.resumeloop, sut.friendlyName())
    }

    @Test fun shortDescriptionTest() {
        Assert.assertEquals("Resume loop", sut.shortDescription())
    }

    @Test fun iconTest() {
        Assert.assertEquals(R.drawable.ic_replay_24dp, sut.icon())
    }

    @Test fun doActionTest() {
        `when`(loopPlugin.isSuspended).thenReturn(true)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendTo(0)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopResume")
        Mockito.verify(loopPlugin, Mockito.times(1)).createOfflineEvent(0)

        // another call should keep it resumed, , no new invocation
        `when`(loopPlugin.isSuspended).thenReturn(false)
        sut.doAction(object : Callback() {
            override fun run() {}
        })
        Mockito.verify(loopPlugin, Mockito.times(1)).suspendTo(0)
        Mockito.verify(configBuilderPlugin, Mockito.times(1)).storeSettings("ActionLoopResume")
        Mockito.verify(loopPlugin, Mockito.times(1)).createOfflineEvent(0)
    }
}