package app.aaps.implementation.bolus

import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/** Covers [RoleBranch] prepare/commit routing: client round-trip gating + master local-execution result mapping. */
class RoleBranchTest : TestBase() {

    @Mock lateinit var dispatcher: ClientControlActionDispatcher
    @Mock lateinit var nsClient: NsClient
    @Mock lateinit var config: Config

    private val command: ClientControlActionDispatcher.Command = ClientControlActionDispatcher.Command.BolusCommit(bolusId = 1L)

    private fun roleBranch(client: Boolean): RoleBranch {
        whenever(config.AAPSCLIENT).thenReturn(client)
        return RoleBranch(dispatcher, nsClient, config)
    }

    // ---- master (prepare) ----

    @Test fun prepare_masterPreview_returnsPrepared() = runTest {
        val result = roleBranch(client = false).prepare("l", command) {
            WizardBolusExecutor.PrepareResult.Preview(insulin = 1.0, carbs = 0, bolusId = 42L)
        }
        assertThat(result).isInstanceOf(ActionProgress.Prepared::class.java)
        assertThat((result as ActionProgress.Prepared).id).isEqualTo(42L)
    }

    @Test fun prepare_masterError_returnsRejectedExecutionFailed() = runTest {
        val result = roleBranch(client = false).prepare("l", command) {
            WizardBolusExecutor.PrepareResult.Error("boom")
        }
        assertThat(result).isEqualTo(ActionProgress.Rejected(FailureReason.ExecutionFailed, "boom"))
    }

    @Test fun prepare_masterNoAction_returnsRejectedNoAction() = runTest {
        val result = roleBranch(client = false).prepare("l", command) {
            WizardBolusExecutor.PrepareResult.NoAction
        }
        assertThat(result).isEqualTo(ActionProgress.Rejected(FailureReason.NoAction))
    }

    // ---- client (prepare) ----

    @Test fun prepare_clientReachable_delegatesToDispatcher() = runTest {
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(dispatcher.run(command, "l")).thenReturn(ActionProgress.Applied)
        val result = roleBranch(client = true).prepare("l", command) { error("master path must not run") }
        assertThat(result).isEqualTo(ActionProgress.Applied)
    }

    @Test fun prepare_clientOfflineControlDisabled_rejectedControlDisabled() = runTest {
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(false))
        whenever(nsClient.masterControlAllowed).thenReturn(MutableStateFlow(false))
        val result = roleBranch(client = true).prepare("l", command) { error("must not run") }
        assertThat((result as ActionProgress.Rejected).reason).isEqualTo(FailureReason.ControlDisabled)
    }

    @Test fun prepare_clientOfflineUnreachable_rejectedNotReachable() = runTest {
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(false))
        whenever(nsClient.masterControlAllowed).thenReturn(MutableStateFlow(true))
        val result = roleBranch(client = true).prepare("l", command) { error("must not run") }
        assertThat((result as ActionProgress.Rejected).reason).isEqualTo(FailureReason.NotReachable)
    }

    // ---- master (commit) ----

    @Test fun commit_masterDelivered_returnsApplied() = runTest {
        val result = roleBranch(client = false).commit("l", command) { WizardBolusExecutor.ConfirmResult.Delivered }
        assertThat(result).isEqualTo(ActionProgress.Applied)
    }

    @Test fun commit_masterNoPending_returnsRejected() = runTest {
        val result = roleBranch(client = false).commit("l", command) { WizardBolusExecutor.ConfirmResult.NoPending }
        assertThat((result as ActionProgress.Rejected).reason).isEqualTo(FailureReason.NoPendingBolus)
    }

    @Test fun commit_masterSyncError_returnsRejectedExecutionFailed() = runTest {
        val result = roleBranch(client = false).commit("l", command) { onError ->
            onError("fail"); WizardBolusExecutor.ConfirmResult.Delivered
        }
        assertThat((result as ActionProgress.Rejected).reason).isEqualTo(FailureReason.ExecutionFailed)
        assertThat(result.detail).isEqualTo("fail")
    }

    @Test fun commit_clientReachable_delegatesToDispatcher() = runTest {
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(dispatcher.run(command, "l")).thenReturn(ActionProgress.Applied)
        val result = roleBranch(client = true).commit("l", command) { error("master path must not run") }
        assertThat(result).isEqualTo(ActionProgress.Applied)
    }
}
