package app.aaps.pump.carelevo.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android foreground state behind [CarelevoAppForeground]: the whole app's process lifecycle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCarelevoAppForegroundTest {

    private val sut = AndroidCarelevoAppForeground()
    private val registry = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry

    @Before
    fun setUp() {
        // The process lifecycle is one object for the whole process. Start each test in the background.
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    @Test
    fun `AAPS is in front while the process is started`() {
        assertThat(sut.isInForeground()).isFalse()

        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        assertThat(sut.isInForeground()).isTrue()

        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertThat(sut.isInForeground()).isTrue()

        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertThat(sut.isInForeground()).isFalse()
    }

    @Test
    fun `the stream gives the state now and then each change once`() {
        val seen = mutableListOf<Boolean>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { sut.inForeground().collect { seen += it } }

        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        // Resumed is still in front: no second true.
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        scope.cancel()

        assertThat(seen).containsExactly(false, true, false).inOrder()
    }
}
