package app.aaps.core.interfaces.configuration

import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The part of the reconfiguration window that lives on [Config]: the derived [Config.appInitialized],
 * the [awaitInitialized] predicate, and [whileReconfiguring]'s `finally`.
 *
 * Driven through a real [Config] rather than a mock, because the point of most of these is the
 * default implementation on the interface - a mock would answer for it and prove nothing. The
 * counting itself is tested without any of this in `InitProgressReconfigureTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigReconfiguringTest {

    /**
     * A real [Config], wired exactly as the four production implementations are: the window is held in
     * [InitProgress] and moved with its own helpers. Everything unrelated answers with a constant.
     */
    private class TestConfig : Config {

        private val _initProgressFlow = MutableStateFlow(InitProgress(done = true))
        override val initProgressFlow: StateFlow<InitProgress> = _initProgressFlow.asStateFlow()

        private val _initSnackbarFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
        override val initSnackbarFlow: SharedFlow<String> = _initSnackbarFlow.asSharedFlow()

        override fun beginReconfiguring() = _initProgressFlow.update { it.enteringReconfigure() }
        override fun endReconfiguring() = _initProgressFlow.update { it.leavingReconfigure() }

        override fun updateInitProgress(step: String, current: Int, total: Int) = Unit
        override fun initCompleted() = Unit
        override fun initFailed(error: String) = Unit
        override fun showInitSnackbar(message: String) = Unit

        override val SUPPORTED_NS_VERSION: Int = 0
        override val APS: Boolean = true
        override val AAPSCLIENT: Boolean = false
        override val AAPSCLIENT1: Boolean = false
        override val AAPSCLIENT2: Boolean = false
        override val AAPSCLIENT3: Boolean = false
        override val PUMPCONTROL: Boolean = false
        override val PUMPDRIVERS: Boolean = true
        override val FLAVOR: String = "full"
        override val VERSION_NAME: String = "test"
        override val HEAD: String = "test"
        override val COMMITTED: Boolean = true
        override val BUILD_VERSION: String = "test"
        override val REMOTE: String = "test"
        override val BUILD_TYPE: String = "debug"
        override val VERSION: String = "test"
        override val platform: AppPlatform = AppPlatform.Android
        override val APPLICATION_ID: String = "test"
        override val DEBUG: Boolean = true
        override val currentDeviceModelString: String = "test"
        override val deviceModelForUpload: String = "test"
        override val deviceManufacturer: String = "test"
        override val appName: TextRef = TextRef.Literal("AAPS")
        override fun isDev(): Boolean = true
        override fun isEngineeringModeOrRelease(): Boolean = true
        override fun isEngineeringMode(): Boolean = false
        override fun isEnabled(option: ExternalOptions): Boolean = false
    }

    @Test
    fun `appInitialized is false while reconfiguring, even though init finished`() {
        val config = TestConfig()
        assertThat(config.appInitialized).isTrue()

        config.beginReconfiguring()

        assertThat(config.appInitialized).isFalse()
        // The splash gate is untouched - that is the whole reason `done` is not cleared to say this.
        assertThat(config.initProgressFlow.value.done).isTrue()

        config.endReconfiguring()
        assertThat(config.appInitialized).isTrue()
    }

    /**
     * The hole this was written for. [awaitInitialized] used to wait on `it.done`, which is ALREADY
     * true during an import - so it returned instantly and handed the caller the exact window it was
     * meant to skip. `KeepAliveWorker` fires every five minutes and then calls `checkPump()`.
     */
    @Test
    fun `awaitInitialized does not return while reconfiguring`() = runTest {
        val config = TestConfig()
        config.beginReconfiguring()

        var answer: Boolean? = null
        backgroundScope.launch { answer = config.awaitInitialized(timeoutMs = 10_000L) }

        // No virtual time is advanced anywhere here on purpose. The window closing is what must
        // release the waiter; if the test let the clock run, a pass could just mean the timeout fired.
        runCurrent()
        assertThat(answer).isNull()

        config.endReconfiguring()
        runCurrent()

        assertThat(answer).isTrue()
    }

    @Test
    fun `awaitInitialized gives up when the window never closes`() = runTest {
        val config = TestConfig()
        config.beginReconfiguring()

        assertThat(config.awaitInitialized(timeoutMs = 1_000L)).isFalse()
    }

    @Test
    fun `whileReconfiguring opens the window for the block and closes it after`() = runTest {
        val config = TestConfig()
        var insideBlock: Boolean? = null

        config.whileReconfiguring { insideBlock = config.appInitialized }

        assertThat(insideBlock).isFalse()
        assertThat(config.appInitialized).isTrue()
    }

    /**
     * The one that matters most. Neither `executeImport` implementation has a `try/finally` of its
     * own, so a throw part-way through rewriting the preferences must still close the window here. A
     * window left open means `appInitialized` stays false for the rest of the process, and
     * `WizardBolusExecutorImpl` refuses on it in two places - the user could not bolus. That is a
     * worse outcome than the crash this whole change exists to prevent.
     */
    @Test
    fun `whileReconfiguring closes the window when the block throws`() = runTest {
        val config = TestConfig()

        val thrown = runCatching { config.whileReconfiguring { error("apply blew up") } }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IllegalStateException::class.java)
        assertThat(config.appInitialized).isTrue()
    }

    /** Nested windows are real: an import opens one for the rewrite and another for the apply. */
    @Test
    fun `a nested window does not reopen the app early`() = runTest {
        val config = TestConfig()

        config.whileReconfiguring {
            config.whileReconfiguring { }
            // The inner block finished, but the outer one has not - the app is still reconfiguring.
            assertThat(config.appInitialized).isFalse()
        }

        assertThat(config.appInitialized).isTrue()
    }
}
