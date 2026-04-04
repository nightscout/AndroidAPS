package app.aaps.plugins.main.general.nfcCommands

import android.content.SharedPreferences
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.plugins.main.R
import app.aaps.shared.tests.SharedPreferencesMock
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NfcCommandsPluginTest : TestBaseWithProfile() {
    @Mock lateinit var commandQueue: CommandQueue

    @Mock lateinit var loop: app.aaps.core.interfaces.aps.Loop

    @Mock lateinit var persistenceLayer: PersistenceLayer

    @Mock lateinit var configBuilder: ConfigBuilder

    @Mock lateinit var mockProfileStore: ProfileStore

    private val secret = "0123456789abcdef0123456789abcdef".toByteArray()
    private val tagUid = "aabbccdd"
    private lateinit var plugin: NfcCommandsPlugin

    @BeforeEach
    fun setupPlugin() {
        plugin =
            NfcCommandsPlugin(
                context = context,
                aapsLogger = aapsLogger,
                rh = rh,
                preferences = preferences,
                constraintChecker = constraintsChecker,
                profileFunction = profileFunction,
                profileUtil = profileUtil,
                localProfileManager = localProfileManager,
                insulin = insulin,
                activePlugin = activePlugin,
                commandQueue = commandQueue,
                loop = loop,
                dateUtil = dateUtil,
                persistenceLayer = persistenceLayer,
                decimalFormatter = decimalFormatter,
                configBuilder = configBuilder,
                rxBus = rxBus,
            )
        plugin.setPluginEnabledBlocking(PluginType.GENERAL, true)

        runTest {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        }
        whenever(preferences.get(BooleanKey.NfcAllowRemoteCommands)).thenReturn(true)
        whenever(rh.gs(R.string.wrong_format)).thenReturn("Wrong format")
        whenever(rh.gs(R.string.nfccommands_wrong_duration)).thenReturn("Wrong duration")
        whenever(rh.gs(R.string.nfccommands_pump_disconnected)).thenReturn("Pump disconnected")
        whenever(rh.gs(app.aaps.core.ui.R.string.noprofile)).thenReturn("No profile")
    }

    // ── Template / buildCommand tests ─────────────────────────────────────────

    @Test
    fun `available command templates should expose current NFC command set`() {
        val labelResIds = NfcTokenSupport.availableCommands().map { it.labelResId }

        assertThat(labelResIds).contains(R.string.nfccommands_cmd_aapsclient_restart)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_restart_aaps)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_loop_closed)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_loop_lgs)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_loop_suspend)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_profile_switch)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_pump_disconnect)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_basal_absolute)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_basal_percent)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_carbs)
        assertThat(labelResIds).contains(R.string.nfccommands_cmd_extended_bolus)
    }

    @Test
    fun `buildCommand should ignore stale args for commands without arguments`() {
        val template = NfcTokenSupport.availableCommands().first { it.labelResId == R.string.nfccommands_cmd_loop_stop }

        val command = NfcTokenSupport.buildCommand(template, "unexpected")

        assertThat(command).isEqualTo("LOOP STOP")
    }

    @Test
    fun `buildCommand should return parametric disconnect command with provided duration`() {
        val template = NfcTokenSupport.availableCommands().first { it.labelResId == R.string.nfccommands_cmd_pump_disconnect }

        val command = NfcTokenSupport.buildCommand(template, "60")

        assertThat(command).isEqualTo("PUMP DISCONNECT 60")
    }

    @Test
    fun `buildCommand should return null when required args are missing`() {
        val template = NfcTokenSupport.availableCommands().first { it.labelResId == R.string.nfccommands_cmd_loop_suspend }

        val command = NfcTokenSupport.buildCommand(template, "")

        assertThat(command).isNull()
    }

    @Test
    fun `buildCommand should include args when template requires them`() {
        val template = NfcTokenSupport.availableCommands().first { it.labelResId == R.string.nfccommands_cmd_loop_suspend }

        val command = NfcTokenSupport.buildCommand(template, "30")

        assertThat(command).isEqualTo("LOOP SUSPEND 30")
    }

    // ── Token issue / verify tests ─────────────────────────────────────────────

    @Test
    fun `issued token should verify until expiry`() {
        val now = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "BOLUS 1.0", now, tagUid = tagUid)

        val verified = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = tagUid)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
        verified as NfcTokenVerificationResult.Success
        assertThat(verified.commands).isEqualTo(listOf("BOLUS 1.0"))
        assertThat(verified.expiresAtMillis).isEqualTo(now + NfcTokenSupport.ONE_YEAR_MILLIS)
    }

    @Test
    fun `expired token should be rejected`() {
        val now = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)

        val verified = NfcTokenSupport.verifyToken(secret, issued.token, now + NfcTokenSupport.ONE_YEAR_MILLIS + 1L, tagUid = tagUid)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
        verified as NfcTokenVerificationResult.Failure
        assertThat(verified.reason).isEqualTo("Token expired")
    }

    @Test
    fun `tampered signature should be rejected`() {
        val now = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)
        val parts = issued.token.split(".")
        val tamperedToken = "${parts[0]}.${parts[1]}.invalidsignature"

        val verified = NfcTokenSupport.verifyToken(secret, tamperedToken, now + 1_000L, tagUid = tagUid)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
    }

    @Test
    fun `token with wrong secret should be rejected`() {
        val now = 1_700_000_000_000L
        val otherSecret = "ffffffffffffffffffffffffffffffff".toByteArray()
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)

        val verified = NfcTokenSupport.verifyToken(otherSecret, issued.token, now + 1_000L, tagUid = tagUid)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
        verified as NfcTokenVerificationResult.Failure
        assertThat(verified.reason).isEqualTo("Invalid token signature")
    }

    @Test
    fun `malformed token with wrong number of parts should be rejected`() {
        val verified = NfcTokenSupport.verifyToken(secret, "not.a.valid.jwt.token", 0L)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
    }

    @Test
    fun `token with iat after exp should be rejected`() {
        val now = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)
        val headerB64 = issued.token.split(".")[0]
        val badPayload =
            org.json
                .JSONObject()
                .put("jti", "test-id")
                .put("cmds", org.json.JSONArray().put("LOOP STOP"))
                .put("tid", tagUid)
                .put("iat", (now / 1000L) + 100_000L) // iat after exp
                .put("exp", now / 1000L)
                .toString()
                .toByteArray(Charsets.UTF_8)
        val badPayloadB64 =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(badPayload)
        val signingInput = "$headerB64.$badPayloadB64"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"))
        val sig =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(signingInput.toByteArray()))
        val badToken = "$signingInput.$sig"

        val verified = NfcTokenSupport.verifyToken(secret, badToken, now, tagUid = tagUid)

        assertThat(verified).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
        verified as NfcTokenVerificationResult.Failure
        assertThat(verified.reason).isEqualTo("Invalid token timestamps")
    }

    // ── processLoop tests ──────────────────────────────────────────────────────

    @Test
    fun `executeCommand LOOP STOP should disable loop`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.success).isTrue()
        // Positional order: newRM, action, source, listValues, durationInMinutes, profile
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.DISABLED_LOOP),
            eq(Action.LOOP_DISABLED),
            eq(Sources.NfcCommands),
            any(),
            eq(Int.MAX_VALUE),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP DISABLE should disable loop`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")

        val result = plugin.executeCommand("LOOP DISABLE")

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand LOOP RESUME should resume loop`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_resumed)).thenReturn("Loop resumed")

        val result = plugin.executeCommand("LOOP RESUME")

        assertThat(result.success).isTrue()
        // Positional order: newRM, action, source, listValues, durationInMinutes, profile
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.RESUME),
            eq(Action.RESUME),
            eq(Sources.NfcCommands),
            any(),
            any(),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP RESUME from disabled state should re-enable loop`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS))
        whenever(loop.runningMode).thenReturn(RM.Mode.DISABLED_LOOP)
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_resumed)).thenReturn("Loop resumed")

        val result = plugin.executeCommand("LOOP RESUME")

        assertThat(result.success).isTrue()
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.RESUME),
            eq(Action.RESUME),
            eq(Sources.NfcCommands),
            any(),
            any(),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP SUSPEND should call handleRunningModeChange directly without pre-cancelling TBR`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_suspended)).thenReturn("Loop suspended")

        val result = plugin.executeCommand("LOOP SUSPEND 30")

        assertThat(result.success).isTrue()
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.SUSPENDED_BY_USER),
            eq(Action.SUSPEND),
            eq(Sources.NfcCommands),
            any(),
            eq(30),
            eq(effectiveProfile),
        )
        Mockito.verify(commandQueue, Mockito.never()).cancelTempBasal(any(), any(), anyOrNull())
    }

    @Test
    fun `executeCommand LOOP SUSPEND with invalid duration should fail`() {
        whenever(rh.gs(R.string.nfccommands_wrong_duration)).thenReturn("Wrong duration")

        val result = plugin.executeCommand("LOOP SUSPEND 0")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP SUSPEND clamps duration above three hours`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_suspended)).thenReturn("Loop suspended")

        val result = plugin.executeCommand("LOOP SUSPEND 999")

        assertThat(result.success).isTrue()
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.SUSPENDED_BY_USER),
            eq(Action.SUSPEND),
            eq(Sources.NfcCommands),
            any(),
            eq(180),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP LGS should switch to LGS mode`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP_LGS))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)).thenReturn("LGS")
        whenever(rh.gs(eq(R.string.nfccommands_current_loop_mode), any())).thenReturn("LGS mode")

        val result = plugin.executeCommand("LOOP LGS")

        assertThat(result.success).isTrue()
        // Positional order: newRM, action, source, listValues, durationInMinutes, profile
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.CLOSED_LOOP_LGS),
            eq(Action.LGS_LOOP_MODE),
            eq(Sources.NfcCommands),
            any(),
            any(),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP CLOSED should switch to closed loop`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(app.aaps.core.ui.R.string.closedloop)).thenReturn("Closed")
        whenever(rh.gs(eq(R.string.nfccommands_current_loop_mode), any())).thenReturn("Closed loop")

        val result = plugin.executeCommand("LOOP CLOSED")

        assertThat(result.success).isTrue()
        // Positional order: newRM, action, source, listValues, durationInMinutes, profile
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.CLOSED_LOOP),
            eq(Action.CLOSED_LOOP_MODE),
            eq(Sources.NfcCommands),
            any(),
            any(),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand LOOP STOP should fail if loop mode not allowed`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(rh.gs(app.aaps.core.ui.R.string.loopisdisabled)).thenReturn("Loop is disabled")

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP RESUME should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP RESUME")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP SUSPEND should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP SUSPEND 30")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP LGS should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP_LGS))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP LGS")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP CLOSED should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP CLOSED")

        assertThat(result.success).isFalse()
    }

    // ── processAapsClient tests ────────────────────────────────────────────────

    @Test
    fun `executeCommand AAPSCLIENT RESTART should send restart event`() {
        whenever(rh.gs(R.string.nfccommands_aapsclient_restart_sent)).thenReturn("AAPSClient restart sent")

        val result = plugin.executeCommand("AAPSCLIENT RESTART")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("AAPSClient restart sent")
    }

    @Test
    fun `executeCommand AAPSCLIENT with invalid subcommand should fail`() {
        whenever(rh.gs(R.string.wrong_format)).thenReturn("Wrong format")

        val result = plugin.executeCommand("AAPSCLIENT STOP")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand AAPSCLIENT RESTART should be blocked when remote commands disabled`() {
        whenever(preferences.get(BooleanKey.NfcAllowRemoteCommands)).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_allowed)).thenReturn("Remote commands not allowed")

        val result = plugin.executeCommand("AAPSCLIENT RESTART")

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Remote commands not allowed")
    }

    // ── processPump tests ──────────────────────────────────────────────────────

    @Test
    fun `executeCommand should disconnect pump for three hours`() {
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_pump_disconnected)).thenReturn("Pump disconnected")

        val result = plugin.executeCommand("PUMP DISCONNECT 180")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Pump disconnected")
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.DISCONNECTED_PUMP),
            eq(Action.DISCONNECT),
            eq(Sources.NfcCommands),
            any(),
            eq(180),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand should clamp disconnect duration to three hours`() {
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_pump_disconnected)).thenReturn("Pump disconnected")

        val result = plugin.executeCommand("PUMP DISCONNECT 240")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Pump disconnected")
        verify(loop).handleRunningModeChange(
            eq(RM.Mode.DISCONNECTED_PUMP),
            eq(Action.DISCONNECT),
            eq(Sources.NfcCommands),
            any(),
            eq(180),
            eq(effectiveProfile),
        )
    }

    @Test
    fun `executeCommand PUMP DISCONNECT should fail when handleRunningModeChange returns false`() {
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("PUMP DISCONNECT 60")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PUMP CONNECT should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("PUMP CONNECT")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PUMP CONNECT returns connected when reconnect is not needed`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(rh.gs(app.aaps.core.interfaces.R.string.connected)).thenReturn("Connected")

        val result = plugin.executeCommand("PUMP CONNECT")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Connected")
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    // ── processBasal tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand BASAL STOP should cancel temp basal`() {
        whenever(rh.gs(R.string.nfccommands_tempbasal_canceled)).thenReturn("Temp basal canceled")

        val result = plugin.executeCommand("BASAL STOP")

        assertThat(result.success).isTrue()
        verify(commandQueue).cancelTempBasal(eq(true), any(), any())
    }

    @Test
    fun `executeCommand BASAL CANCEL should cancel temp basal`() {
        whenever(rh.gs(R.string.nfccommands_tempbasal_canceled)).thenReturn("Temp basal canceled")

        val result = plugin.executeCommand("BASAL CANCEL")

        assertThat(result.success).isTrue()
        verify(commandQueue).cancelTempBasal(eq(true), any(), any())
    }

    @Test
    fun `executeCommand BASAL percent should enqueue percent temp basal`() {
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(120, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        // GENERIC_AAPS has tbrSettings durationStep=30; duration 30 satisfies 30%30==0
        val result = plugin.executeCommand("BASAL 120% 30")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalPercent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute should enqueue absolute temp basal`() {
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(1.5, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        // GENERIC_AAPS has tbrSettings durationStep=30; duration 30 satisfies 30%30==0
        val result = plugin.executeCommand("BASAL 1.5 30")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalAbsolute(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute with non-multiple duration rounds up to next pump step`() {
        // DANA_R has tbrSettings durationStep=60; a 30-min duration is not a multiple
        // of 60. The plugin must round UP to 60 instead of returning an error.
        val mockPump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(mockPump)
        whenever(mockPump.model()).thenReturn(PumpType.DANA_R)
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(1.5, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BASAL 1.5 30")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalAbsolute(any(), eq(60), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL percent with non-multiple duration rounds up to next pump step`() {
        // DANA_R has tbrSettings durationStep=60; a 30-min duration is not a multiple
        // of 60. The plugin must round UP to 60 instead of returning an error.
        val mockPump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(mockPump)
        whenever(mockPump.model()).thenReturn(PumpType.DANA_R)
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(120, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BASAL 120% 30")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalPercent(any(), eq(60), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute with exact-multiple duration is unchanged`() {
        // 60 min on a 60-step pump — must pass through unchanged, not doubled
        val mockPump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(mockPump)
        whenever(mockPump.model()).thenReturn(PumpType.DANA_R)
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(1.5, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BASAL 1.5 60")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalAbsolute(any(), eq(60), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL percent without explicit duration uses pump step`() {
        val mockPump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(mockPump)
        whenever(mockPump.model()).thenReturn(PumpType.DANA_R)
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(120, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BASAL 120%")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalPercent(any(), eq(60), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute without explicit duration uses pump step`() {
        val mockPump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(mockPump)
        whenever(mockPump.model()).thenReturn(PumpType.DANA_R)
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.5, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BASAL 1.5")

        assertThat(result.success).isTrue()
        verify(commandQueue).tempBasalAbsolute(any(), eq(60), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL percent with malformed amount should fail`() {
        val result = plugin.executeCommand("BASAL abc% 30")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute with malformed amount should fail`() {
        val result = plugin.executeCommand("BASAL abc 30")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), any())
    }

    // ── processExtended tests ──────────────────────────────────────────────────

    @Test
    fun `executeCommand EXTENDED STOP should cancel extended bolus`() {
        whenever(rh.gs(R.string.nfccommands_extended_canceled)).thenReturn("Extended canceled")

        val result = plugin.executeCommand("EXTENDED STOP")

        assertThat(result.success).isTrue()
        verify(commandQueue).cancelExtended(any())
    }

    @Test
    fun `executeCommand EXTENDED bolus should enqueue extended bolus`() {
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(2.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_extended_set), any(), any())).thenReturn("Extended set")

        val result = plugin.executeCommand("EXTENDED 2.0 60")

        assertThat(result.success).isTrue()
        verify(commandQueue).extendedBolus(any(), any(), any())
    }

    @Test
    fun `executeCommand EXTENDED with negative duration should fail`() {
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(2.0, aapsLogger),
        )

        val result = plugin.executeCommand("EXTENDED 2.0 -30")

        assertThat(result.success).isFalse()
        verify(commandQueue, Mockito.never()).extendedBolus(any(), any(), any())
    }

    @Test
    fun `executeCommand EXTENDED with negative amount should fail`() {
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(-1.0, aapsLogger),
        )

        val result = plugin.executeCommand("EXTENDED -1.0 60")

        assertThat(result.success).isFalse()
        verify(commandQueue, Mockito.never()).extendedBolus(any(), any(), any())
    }

    @Test
    fun `executeCommand EXTENDED without duration should fail`() {
        val result = plugin.executeCommand("EXTENDED 2.0")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).extendedBolus(any(), any(), any())
    }

    // ── processBolus tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand BOLUS should enqueue bolus`() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(1.0, aapsLogger),
        )
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BOLUS 1.0")

        assertThat(result.success).isTrue()
        verify(commandQueue).bolus(any(), any())
    }

    @Test
    fun `executeCommand BOLUS MEAL should enqueue bolus`() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(1.0, aapsLogger),
        )
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = plugin.executeCommand("BOLUS 1.0 MEAL")

        assertThat(result.success).isTrue()
        verify(commandQueue).bolus(any(), any())
    }

    @Test
    fun `executeCommand BOLUS MEAL should respect cooldown`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_bolus_not_allowed)).thenReturn("Remote bolus not allowed")
        val now = app.aaps.core.data.configuration.Constants.remoteBolusMinDistance * 2
        whenever(dateUtil.now()).thenReturn(now)
        val field = NfcCommandsPlugin::class.java.getDeclaredField("lastRemoteBolusTime")
        field.isAccessible = true
        field.set(plugin, now)

        val result = plugin.executeCommand("BOLUS 1.0 MEAL")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand BOLUS MEAL should respect suspended pump`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.SUSPENDED_BY_USER)
        whenever(rh.gs(app.aaps.core.ui.R.string.pumpsuspended)).thenReturn("Pump suspended")

        val result = plugin.executeCommand("BOLUS 1.0 MEAL")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand BOLUS should fail when another bolus is in queue`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_another_bolus_in_queue)).thenReturn("Another bolus in queue")

        val result = plugin.executeCommand("BOLUS 1.0")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand BOLUS with unsupported third argument should fail`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )

        val result = plugin.executeCommand("BOLUS 1.0 OTHER")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).bolus(any(), any())
    }

    @Test
    fun `successful bolus callback updates last remote bolus time`() {
        val firstNow = Constants.remoteBolusMinDistance * 2L
        val callbackNow = firstNow + 2_345L
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")
        whenever(dateUtil.now()).thenReturn(firstNow, callbackNow)
        whenever(commandQueue.bolus(any(), any())).thenReturn(true)
        val callbackCaptor = argumentCaptor<Callback>()

        val result = plugin.executeCommand("BOLUS 1.0")

        assertThat(result.success).isTrue()
        verify(commandQueue).bolus(any(), callbackCaptor.capture())
        val enactResult = mock<PumpEnactResult>()
        whenever(enactResult.success).thenReturn(true)
        whenever(enactResult.comment).thenReturn("ok")
        callbackCaptor.firstValue.result(enactResult).run()

        val field = NfcCommandsPlugin::class.java.getDeclaredField("lastRemoteBolusTime")
        field.isAccessible = true
        assertThat(field.get(plugin) as Long).isEqualTo(callbackNow)
    }

    @Test
    fun `failed bolus callback does not update last remote bolus time`() {
        val firstNow = Constants.remoteBolusMinDistance * 2L
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")
        whenever(dateUtil.now()).thenReturn(firstNow)
        whenever(commandQueue.bolus(any(), any())).thenReturn(true)
        val callbackCaptor = argumentCaptor<Callback>()

        plugin.executeCommand("BOLUS 1.0")

        verify(commandQueue).bolus(any(), callbackCaptor.capture())
        val enactResult = mock<PumpEnactResult>()
        whenever(enactResult.success).thenReturn(false)
        whenever(enactResult.comment).thenReturn("failed")
        callbackCaptor.firstValue.result(enactResult).run()

        val field = NfcCommandsPlugin::class.java.getDeclaredField("lastRemoteBolusTime")
        field.isAccessible = true
        assertThat(field.get(plugin) as Long).isEqualTo(0L)
    }

    @Test
    fun `successful meal bolus callback creates eating soon target when profile exists`() {
        val firstNow = Constants.remoteBolusMinDistance * 2L
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")
        whenever(commandQueue.bolus(any(), any())).thenReturn(true)
        whenever(preferences.get(IntKey.OverviewEatingSoonDuration)).thenReturn(45)
        whenever(preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)).thenReturn(5.5)
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(dateUtil.now()).thenReturn(firstNow, firstNow + 1_000L, firstNow + 2_000L)
        val callbackCaptor = argumentCaptor<Callback>()

        val result = plugin.executeCommand("BOLUS 1.0 MEAL")

        assertThat(result.success).isTrue()
        verify(commandQueue).bolus(any(), callbackCaptor.capture())
        val enactResult = mock<PumpEnactResult>()
        whenever(enactResult.success).thenReturn(true)
        whenever(enactResult.comment).thenReturn("ok")
        callbackCaptor.firstValue.result(enactResult).run()

        verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(any(), eq(Action.TT), eq(Sources.NfcCommands), anyOrNull(), any())
    }

    @Test
    fun `successful meal bolus callback skips eating soon target when profile is missing`() {
        val firstNow = Constants.remoteBolusMinDistance * 2L
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")
        whenever(commandQueue.bolus(any(), any())).thenReturn(true)
        runTest {
            whenever(profileFunction.getProfile()).thenReturn(null)
        }
        whenever(dateUtil.now()).thenReturn(firstNow, firstNow + 1_000L)
        val callbackCaptor = argumentCaptor<Callback>()

        plugin.executeCommand("BOLUS 1.0 MEAL")

        verify(commandQueue).bolus(any(), callbackCaptor.capture())
        val enactResult = mock<PumpEnactResult>()
        whenever(enactResult.success).thenReturn(true)
        whenever(enactResult.comment).thenReturn("ok")
        callbackCaptor.firstValue.result(enactResult).run()

        verify(persistenceLayer, never()).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any())
    }

    // ── processCarbs tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand CARBS should enqueue carbs`() {
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(20, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_carbs_set), any())).thenReturn("Carbs set")

        val result = plugin.executeCommand("CARBS 20")

        assertThat(result.success).isTrue()
        verify(commandQueue).bolus(any(), any())
    }

    @Test
    fun `executeCommand CARBS with zero grams should fail`() {
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints
                .ConstraintObject(0, aapsLogger),
        )

        val result = plugin.executeCommand("CARBS 0")

        assertThat(result.success).isFalse()
    }

    // ── processTarget tests ────────────────────────────────────────────────────

    @Test
    fun `executeCommand TARGET MEAL should set eating soon target`() {
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = plugin.executeCommand("TARGET MEAL")

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET ACTIVITY should set activity target`() {
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = plugin.executeCommand("TARGET ACTIVITY")

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET HYPO should set hypo target`() {
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = plugin.executeCommand("TARGET HYPO")

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET STOP should cancel temp target`() {
        runTest {
            whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")
        whenever(rh.gsNotLocalised(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")

        val result = plugin.executeCommand("TARGET STOP")

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET CANCEL should cancel temp target`() {
        runTest {
            whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")
        whenever(rh.gsNotLocalised(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")

        val result = plugin.executeCommand("TARGET CANCEL")

        assertThat(result.success).isTrue()
        verify(persistenceLayer).cancelCurrentTemporaryTargetIfAny(any(), eq(Action.CANCEL_TT), eq(Sources.NfcCommands), anyOrNull(), any())
    }

    @Test
    fun `executeCommand TARGET with invalid subcommand should fail`() {
        val result = plugin.executeCommand("TARGET UNKNOWN")

        assertThat(result.success).isFalse()
    }

    // ── processProfile tests ───────────────────────────────────────────────────

    @Test
    fun `executeCommand PROFILE with invalid non-numeric index should fail`() {
        val result = plugin.executeCommand("PROFILE abc")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PROFILE with zero percentage should fail`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))

        val result = plugin.executeCommand("PROFILE 1 0")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PROFILE with negative percentage should fail`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))

        val result = plugin.executeCommand("PROFILE 1 -50")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PROFILE with extreme percentage should fail`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))

        val result = plugin.executeCommand("PROFILE 1 9999")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand PROFILE should create profile switch with default percentage`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_profile_switch_created)).thenReturn("Profile switch created")
        whenever(rh.gsNotLocalised(R.string.nfccommands_profile_switch_created)).thenReturn("Profile switch created")
        whenever(dateUtil.now()).thenReturn(55_000L)

        val result = plugin.executeCommand("PROFILE 1")

        assertThat(result.success).isTrue()
        verify(profileFunction).createProfileSwitch(
            eq(mockProfileStore),
            eq("Default"),
            eq(0),
            eq(100),
            eq(0),
            eq(55_000L),
            eq(Action.PROFILE_SWITCH),
            eq(Sources.NfcCommands),
            eq("Profile switch created"),
            any(),
        )
    }

    @Test
    fun `executeCommand PROFILE should fail when profile source is not configured`() {
        whenever(localProfileManager.profile).thenReturn(null)
        whenever(rh.gs(app.aaps.core.ui.R.string.notconfigured)).thenReturn("Not configured")

        val result = plugin.executeCommand("PROFILE 1")

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Not configured")
    }

    @Test
    fun `executeCommand PROFILE should fail when profile switch creation fails`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))
        whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any()))
            .thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_profile_switch_created)).thenReturn("Profile switch created")
        whenever(rh.gsNotLocalised(R.string.nfccommands_profile_switch_created)).thenReturn("Profile switch created")
        whenever(rh.gs(app.aaps.core.ui.R.string.invalid_profile)).thenReturn("Invalid profile")

        val result = plugin.executeCommand("PROFILE 1")

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Invalid profile")
    }

    // ── general command tests ──────────────────────────────────────────────────

    @Test
    fun `executeCommand with unknown command should fail`() {
        whenever(rh.gs(R.string.nfccommands_unknown_command)).thenReturn("Unknown command")

        val result = plugin.executeCommand("UNKNOWN")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand should fail when remote commands not allowed`() {
        whenever(preferences.get(BooleanKey.NfcAllowRemoteCommands)).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_allowed)).thenReturn("Remote commands not allowed")

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand always returns eraseTag=false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.eraseTag).isFalse()
    }

    @Test
    fun `executeCommand RESTART should exit app`() {
        whenever(rh.gs(R.string.nfccommands_restarting)).thenReturn("Restarting")

        val result = plugin.executeCommand("RESTART")

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Restarting")
        verify(configBuilder).exitApp("NFC", Sources.NfcCommands, true)
    }

    @Test
    fun `executeCommand RESTART with extra argument should fail`() {
        val result = plugin.executeCommand("RESTART NOW")

        assertThat(result.success).isFalse()
        verify(configBuilder, never()).exitApp(any(), any(), any())
    }

    @Test
    fun `executeCommand blank string should fail with wrong format`() {
        val result = plugin.executeCommand("   ")

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Wrong format")
    }

    // ── blacklist tests ────────────────────────────────────────────────────────

    private fun withFakePrefs(block: (SharedPreferencesMock) -> Unit) {
        val fakePrefs = SharedPreferencesMock()
        val encodedSecret =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(secret)
        fakePrefs.edit().putString("nfccommunicator_jwt_secret_v1", encodedSecret).apply()
        Mockito.mockStatic(androidx.preference.PreferenceManager::class.java).use { mockedStatic ->
            mockedStatic
                .`when`<SharedPreferences> {
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(any())
                }.thenReturn(fakePrefs)
            block(fakePrefs)
        }
    }

    // ── executeCascade tests ───────────────────────────────────────────────────

    @Test
    fun `executeCascade all succeed returns success with combined message`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true)
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")
        whenever(rh.gs(R.string.nfccommands_tempbasal_canceled)).thenReturn("Temp basal canceled")

        val result = plugin.executeCascade(listOf("LOOP STOP", "BASAL STOP"))

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Loop disabled")
        assertThat(result.message).contains("Temp basal canceled")
    }

    @Test
    fun `executeCascade one fails returns failure and does not execute remaining commands`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList()) // LOOP STOP will fail
        whenever(rh.gs(app.aaps.core.ui.R.string.loopisdisabled)).thenReturn("Loop is disabled")

        val result = plugin.executeCascade(listOf("LOOP STOP", "BASAL STOP"))

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("Loop is disabled")
        // Second command must not have been attempted
        verify(commandQueue, Mockito.never()).cancelTempBasal(any(), any(), any())
    }

    // ── prepareExecution tests ─────────────────────────────────────────────────

    @Test
    fun `prepareExecution returns Error when plugin is disabled`() {
        plugin.setPluginEnabledBlocking(PluginType.GENERAL, false)
        whenever(rh.gs(R.string.nfccommands_plugin_disabled)).thenReturn("NFC communicator is disabled")

        val result = plugin.prepareExecution("any.token.here")

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
        result as NfcPrepareResult.Error
        assertThat(result.eraseTag).isFalse()
    }

    @Test
    fun `prepareExecution returns Error for malformed token`() {
        withFakePrefs {
            val result = plugin.prepareExecution("not.a.valid.token")

            assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
        }
    }

    @Test
    fun `prepareExecution returns Error for expired token`() {
        val issuedAt = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", issuedAt, tagUid = tagUid)
        val now = issued.expiresAtMillis + 1L
        whenever(dateUtil.now()).thenReturn(now)

        withFakePrefs {
            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
            result as NfcPrepareResult.Error
            assertThat(result.message).isEqualTo("Token expired")
        }
    }

    @Test
    fun `prepareExecution returns Error with eraseTag=true for blacklisted token`() {
        val now = System.currentTimeMillis() - 1_000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(rh.gs(R.string.nfccommands_tag_erased_blacklisted)).thenReturn("Tag erased")
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)

        withFakePrefs { fakePrefs ->
            NfcTokenSupport.blacklistTag(
                fakePrefs,
                NfcCreatedTag(
                    issued.tokenId,
                    "test",
                    listOf("LOOP STOP"),
                    issued.token,
                    now,
                    issued.expiresAtMillis,
                ),
            )

            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
            result as NfcPrepareResult.Error
            assertThat(result.eraseTag).isTrue()
        }
    }

    @Test
    fun `prepareExecution returns Ready with null rewriteWith for non-expiring token in list`() {
        val issuedAt = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", issuedAt, tagUid = tagUid)
        val now = issuedAt + (NfcTokenSupport.ONE_YEAR_MILLIS / 2)
        whenever(dateUtil.now()).thenReturn(now)

        withFakePrefs { fakePrefs ->
            NfcTokenSupport.saveCreatedTag(
                fakePrefs,
                NfcCreatedTag(
                    issued.tokenId,
                    "test",
                    listOf("LOOP STOP"),
                    issued.token,
                    issuedAt,
                    issued.expiresAtMillis,
                ),
            )

            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
            result as NfcPrepareResult.Ready
            assertThat(result.rewriteWith).isNull()
            assertThat(result.oldTag).isNull()
            assertThat(result.commands).isEqualTo(listOf("LOOP STOP"))
        }
    }

    @Test
    fun `prepareExecution returns Ready with null rewriteWith for valid token not in list`() {
        val issuedAt = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", issuedAt, tagUid = tagUid)
        val now = issuedAt + 1_000L
        whenever(dateUtil.now()).thenReturn(now)

        withFakePrefs {
            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
            result as NfcPrepareResult.Ready
            assertThat(result.rewriteWith).isNull()
            assertThat(result.oldTag).isNull()
        }
    }

    @Test
    fun `prepareExecution returns Ready with rewriteWith for soon-expiring token in list`() {
        val issuedAt = 1_700_000_000_000L
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", issuedAt, tagUid = tagUid)
        val now = issued.expiresAtMillis - 15L * 24L * 60L * 60L * 1000L
        whenever(dateUtil.now()).thenReturn(now)

        withFakePrefs { fakePrefs ->
            NfcTokenSupport.saveCreatedTag(
                fakePrefs,
                NfcCreatedTag(
                    issued.tokenId,
                    "test",
                    listOf("LOOP STOP"),
                    issued.token,
                    issuedAt,
                    issued.expiresAtMillis,
                ),
            )

            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
            result as NfcPrepareResult.Ready
            assertThat(result.rewriteWith).isNotNull()
            assertThat(result.oldTag).isNotNull()
            assertThat(result.oldTag!!.id).isEqualTo(issued.tokenId)
            assertThat(result.commands).isEqualTo(listOf("LOOP STOP"))
            assertThat(result.rewriteWith!!.expiresAtMillis)
                .isGreaterThan(issued.expiresAtMillis)
        }
    }

    @Test
    fun `prepareExecution ignores expired blacklist entry`() {
        val now = System.currentTimeMillis() - 1_000L
        whenever(dateUtil.now()).thenReturn(now)
        val issued = NfcTokenSupport.issueToken(secret, "LOOP STOP", now, tagUid = tagUid)

        withFakePrefs { fakePrefs ->
            val expiredEntry = NfcBlacklistEntry(issued.tokenId, now - 1_000L)
            val array = org.json.JSONArray()
            array.put(
                org.json
                    .JSONObject()
                    .put("tokenId", expiredEntry.tokenId)
                    .put("expiresAtMillis", expiredEntry.expiresAtMillis),
            )
            fakePrefs.edit().putString("nfccommunicator_blacklisted_tokens_v1", array.toString()).apply()

            val result = plugin.prepareExecution(issued.token, tagUid)

            assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
        }
    }

    // ── additional gap-coverage tests ─────────────────────────────────────────

    // processLoop – missing branches

    @Test
    fun `executeCommand LOOP STOP should fail when handleRunningModeChange returns false`() {
        whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP))
        whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP SUSPEND should fail when mode not in allowedNextModes`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP SUSPEND 30")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand LOOP LGS should fail when mode not in allowedNextModes`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP LGS")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand LOOP CLOSED should fail when mode not in allowedNextModes`() {
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP CLOSED")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand LOOP RESUME should fail when not in allowedNextModes and loop is not disabled`() {
        // allowedNextModes does not contain RESUME, and runningMode is not DISABLED_LOOP,
        // so the "resume from disabled" fallback does not apply either
        whenever(loop.allowedNextModes()).thenReturn(emptyList())
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_possible)).thenReturn("Remote command is not possible")

        val result = plugin.executeCommand("LOOP RESUME")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand LOOP with unknown subcommand should fail`() {
        val result = plugin.executeCommand("LOOP BADWORD")

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand LOOP should fail when no profile`() {
        runTest {
            whenever(profileFunction.getProfile()).thenReturn(null)
        }

        val result = plugin.executeCommand("LOOP STOP")

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("No profile")
    }

    // processPump – missing branches

    @Test
    fun `executeCommand PUMP DISCONNECT with zero duration should fail`() {
        val result = plugin.executeCommand("PUMP DISCONNECT 0")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand PUMP with wrong format should fail`() {
        val result = plugin.executeCommand("PUMP")

        assertThat(result.success).isFalse()
        verify(loop, never()).handleRunningModeChange(any(), any(), any(), any(), any(), any())
    }

    // processBasal – missing branches

    @Test
    fun `executeCommand BASAL alone should fail`() {
        val result = plugin.executeCommand("BASAL")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).cancelTempBasal(any(), any(), any())
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any(), any())
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL percent with zero duration should fail`() {
        whenever(rh.gs(eq(R.string.nfccommands_wrong_tbr_duration), any())).thenReturn("Wrong TBR duration")

        val result = plugin.executeCommand("BASAL 120% 0")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).tempBasalPercent(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `executeCommand BASAL absolute with zero duration should fail`() {
        whenever(rh.gs(eq(R.string.nfccommands_wrong_tbr_duration), any())).thenReturn("Wrong TBR duration")

        val result = plugin.executeCommand("BASAL 1.5 0")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).tempBasalAbsolute(any(), any(), any(), any(), any(), any())
    }

    // processExtended – CANCEL alias

    @Test
    fun `executeCommand EXTENDED CANCEL should cancel extended bolus`() {
        whenever(rh.gs(R.string.nfccommands_extended_canceled)).thenReturn("Extended canceled")

        val result = plugin.executeCommand("EXTENDED CANCEL")

        assertThat(result.success).isTrue()
        verify(commandQueue).cancelExtended(any())
    }

    // processBolus – zero/negative amount

    @Test
    fun `executeCommand BOLUS with zero amount should fail`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(loop.runningMode).thenReturn(RM.Mode.CLOSED_LOOP)
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(0.0, aapsLogger),
        )

        val result = plugin.executeCommand("BOLUS 0.0")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).bolus(any(), any())
    }

    // processCarbs – format errors

    @Test
    fun `executeCommand CARBS with extra argument should fail`() {
        val result = plugin.executeCommand("CARBS 20 extra")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).bolus(any(), any())
    }

    @Test
    fun `executeCommand CARBS with non-numeric amount should fail`() {
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(0, aapsLogger),
        )

        val result = plugin.executeCommand("CARBS abc")

        assertThat(result.success).isFalse()
        verify(commandQueue, never()).bolus(any(), any())
    }

    // processProfile – index boundary errors

    @Test
    fun `executeCommand PROFILE with zero index should fail`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))

        val result = plugin.executeCommand("PROFILE 0")

        assertThat(result.success).isFalse()
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `executeCommand PROFILE with out-of-bounds index should fail`() {
        whenever(localProfileManager.profile).thenReturn(mockProfileStore)
        whenever(mockProfileStore.getProfileList()).thenReturn(arrayListOf("Default"))

        val result = plugin.executeCommand("PROFILE 99")

        assertThat(result.success).isFalse()
        verify(profileFunction, never()).createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any())
    }

    // processAapsClient – wrong argument count

    @Test
    fun `executeCommand AAPSCLIENT with too many arguments should fail`() {
        val result = plugin.executeCommand("AAPSCLIENT RESTART EXTRA")

        assertThat(result.success).isFalse()
    }
}
