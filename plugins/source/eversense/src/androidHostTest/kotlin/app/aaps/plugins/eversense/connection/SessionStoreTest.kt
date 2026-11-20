package app.aaps.plugins.eversense.connection

import app.aaps.plugins.eversense.storage.EversenseStore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.CancellationException

class SessionStoreTest {
    private val scheduled = mutableListOf<() -> Unit>()
    private var token = 0L
    private val transport = object : EversenseConnectionController.Transport {
        override fun open(session: Long): Boolean { token = session; return true }
        override fun reuse(session: Long) = true
        override fun close() {}
    }
    private val controller = EversenseConnectionController(
        object : EversenseConnectionController.Scheduler {
            override fun after(delayMs: Long, action: () -> Unit): EversenseConnectionController.Task {
                scheduled.add(action)
                return EversenseConnectionController.Task { }
            }
        }, { 0L }, transport
    )
    private val delegate: EversenseStore = mock()
    private val editor: EversenseStore.Editor = mock()
    private fun store(): SessionStore {
        whenever(delegate.edit()).thenReturn(editor)
        controller.start()
        return SessionStore(controller, token, delegate)
    }
    @Test fun queuedEditorCannotRestoreCredentialsAfterStopEvenWithChainedCalls() {
        val store = store()
        val pending = store.edit().putString("username", "old").putString("access_token", "stale")
        controller.stop()
        assertThrows(CancellationException::class.java) { pending.apply() }
        assertThrows(CancellationException::class.java) { pending.commit() }
        verify(editor, never()).apply()
        verify(editor, never()).commit()
    }
    @Test fun oldStoreCannotReadOrWriteAfterReconnect() {
        val old = store()
        controller.connect(force = true)
        assertThrows(CancellationException::class.java) { old.getString("token", null) }
        assertThrows(CancellationException::class.java) { old.edit() }
    }
    @Test fun currentSessionCanCommit() {
        val current = store()
        whenever(editor.commit()).thenReturn(true)
        assertTrue(current.edit().putLong("counter", 2).commit())
        verify(editor).putLong("counter", 2)
        verify(editor).commit()
    }
    @Test fun interrupted365LinkCannotPublishAfterSameGattIsReused() {
        val current = store()
        var generation = 1
        val httpStore = current.restrict { generation == 1 }
        val pending = httpStore.edit().putString("access_token", "old")
        controller.disconnected(token, 19, true)
        generation++
        scheduled.last().invoke()
        assertTrue(controller.active(token))
        assertThrows(CancellationException::class.java) { pending.apply() }
        verify(editor, never()).apply()
    }
    @Test fun cloudStoreSurvivesBleDisconnectAndFreshGattButNotSignOutOrStop() {
        store()
        val cloud = controller.cloudStore(delegate)!!
        controller.disconnected(token, 133)
        cloud.edit().putString("token", "valid").apply()
        controller.connect(force = true)
        cloud.edit().putString("token", "still valid").apply()
        controller.disconnect()
        assertThrows(CancellationException::class.java) { cloud.edit() }
        controller.connect()
        val next = controller.cloudStore(delegate)!!
        controller.stop()
        assertThrows(CancellationException::class.java) { next.edit() }
    }

}
