package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.RM
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.scenes.SceneAutomationApi
import app.aaps.core.interfaces.scenes.SceneAutomationResult
import app.aaps.core.interfaces.scenes.SceneIconResolver
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.sync.R
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import app.aaps.core.ui.R as CoreUiR

class NfcCommandsPluginTest : TestBaseWithProfile() {
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var loop: app.aaps.core.interfaces.aps.Loop
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var configBuilder: ConfigBuilder
    @Mock lateinit var mockProfileStore: ProfileStore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var wizardBolusExecutor: WizardBolusExecutor
    @Mock lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Mock lateinit var sceneAutomationApi: SceneAutomationApi
    @Mock lateinit var bolusProgressData: BolusProgressData
    @Mock lateinit var sceneIconResolver: SceneIconResolver

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
                nfcTagStore = NfcTagStore(TestNfcPreferences().preferences),
                constraintChecker = constraintsChecker,
                profileFunction = profileFunction,
                profileUtil = profileUtil,
                profileRepository = profileRepository,
                activePlugin = activePlugin,
                commandQueue = commandQueue,
                loop = loop,
                dateUtil = dateUtil,
                persistenceLayer = persistenceLayer,
                decimalFormatter = decimalFormatter,
                configBuilder = configBuilder,
                rxBus = rxBus,
                uel = uel,
                wizardBolusExecutor = wizardBolusExecutor,
                iobCobCalculator = iobCobCalculator,
                bolusProgressData = bolusProgressData,
                glucoseStatusProvider = glucoseStatusProvider,
                sceneAutomationApi = sceneAutomationApi,
                sceneIconResolver = sceneIconResolver,
            )
        plugin.setPluginEnabledBlocking(PluginType.SYNC, true)

        runTest {
            whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
            whenever(commandQueue.cancelTempBasal(any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
            whenever(commandQueue.cancelExtended()).thenReturn(pumpEnactResultProvider.get().success(true))
            whenever(commandQueue.bolus(any())).thenReturn(pumpEnactResultProvider.get().success(true))
            whenever(commandQueue.tempBasalPercent(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
            whenever(commandQueue.tempBasalAbsolute(any(), any(), any(), any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
            whenever(commandQueue.extendedBolus(any(), any())).thenReturn(pumpEnactResultProvider.get().success(true))
        }
        whenever(preferences.get(BooleanKey.NfcAllowRemoteCommands)).thenReturn(true)
        whenever(rh.gs(any<Int>())).thenReturn("Mock String")
        whenever(rh.gs(any<Int>(), any())).thenReturn("Mock String")
        whenever(rh.gs(any<Int>(), any(), any())).thenReturn("Mock String")
        whenever(rh.gsNotLocalised(any<Int>())).thenReturn("Mock String")
        whenever(rh.gsNotLocalised(any<Int>(), any())).thenReturn("Mock String")
        whenever(rh.gs(R.string.wrong_format)).thenReturn("Wrong format")
        whenever(rh.gs(R.string.nfccommands_wrong_duration)).thenReturn("Wrong duration")
        whenever(rh.gs(CoreUiR.string.pump_disconnected)).thenReturn("Pump disconnected")
        whenever(rh.gs(CoreUiR.string.noprofile)).thenReturn("No profile")
        whenever(rh.gs(CoreUiR.string.ok)).thenReturn("OK")
    }

    private fun execute(command: String): NfcExecutionResult = runBlocking { plugin.executeCommand(command) }
    private fun execute(code: NfcCommandCode, params: JSONObject = JSONObject()): NfcExecutionResult = execute(NfcTagStore.buildCommand(code, params))

    private fun cascade(commands: List<String>): NfcExecutionResult = runBlocking { plugin.executeCascade(commands) }

    @Test
    fun `NfcCategories build should include new categories and commands`() {
        whenever(sceneAutomationApi.getScenes()).thenReturn(listOf(mock()))
        testPumpPlugin.pumpDescription.bolusStep = 0.1

        val categories = NfcCategories.build(plugin)

        assertThat(categories.any { it.labelResId == CoreUiR.string.scenes }).isTrue()
        
        val scenesCat = categories.find { it.labelResId == CoreUiR.string.scenes }
        assertThat(scenesCat?.commands).contains(NfcCommandCode.RUN_SCENE)
        
        val treatmentsCat = categories.find { it.labelResId == CoreUiR.string.treatments }
        assertThat(treatmentsCat?.commands).contains(NfcCommandCode.BOLUS_WIZARD)
    }

    // ── prepareExecution tests ─────────────────────────────────────────────────

    @Test
    fun `prepareExecution returns Ready with commands when tag registered`() {
        val cmd = NfcTagStore.buildCommand(NfcCommandCode.LOOP_STOP)
        val tag = NfcCreatedTag(tagUid = tagUid, name = "Test", commands = listOf(cmd), createdAtMillis = 0L)
        plugin.nfcTagStore.saveCreatedTag(tag)
        whenever(rh.gs(R.string.nfccommands_tag_not_registered)).thenReturn("Not registered")

        val result = plugin.prepareExecution(tagUid)

        assertThat(result).isInstanceOf(NfcPrepareResult.Ready::class.java)
        val ready = result as NfcPrepareResult.Ready
        assertThat(ready.commands).isEqualTo(listOf(cmd))
        assertThat(ready.tagName).isEqualTo("Test")
    }

    @Test
    fun `prepareExecution returns Error when plugin disabled`() {
        plugin.setPluginEnabledBlocking(PluginType.SYNC, false)
        whenever(rh.gs(R.string.nfccommands_plugin_disabled)).thenReturn("Plugin disabled")

        val result = plugin.prepareExecution(tagUid)

        assertThat(result).isInstanceOf(NfcPrepareResult.Error::class.java)
    }

    // ── processLoop tests ──────────────────────────────────────────────────────

    @Test
    fun `executeCommand LOOP_STOP should disable loop`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")

        val result = execute(NfcCommandCode.LOOP_STOP)

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.DISABLED_LOOP),
                eq(Action.LOOP_DISABLED),
                eq(Sources.NfcCommands),
                any(),
                eq(Int.MAX_VALUE),
                eq(effectiveProfile),
            )
        }
    }

    @Test
    fun `executeCommand LOOP_RESUME should resume loop`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.RESUME)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(R.string.nfccommands_loop_resumed)).thenReturn("Loop resumed")

        val result = execute(NfcCommandCode.LOOP_RESUME)

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.RESUME),
                eq(Action.RESUME),
                eq(Sources.NfcCommands),
                any(),
                any(),
                eq(effectiveProfile),
            )
        }
    }

    @Test
    fun `executeCommand LOOP_SUSPEND should call handleRunningModeChange directly`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.SUSPENDED_BY_USER)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(CoreUiR.string.loopsuspended)).thenReturn("Loop suspended")

        val result = execute(NfcCommandCode.LOOP_SUSPEND, JSONObject().put(NfcJsonKeys.DURATION, 30))

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.SUSPENDED_BY_USER),
                eq(Action.SUSPEND),
                eq(Sources.NfcCommands),
                any(),
                eq(30),
                eq(effectiveProfile),
            )
        }
    }

    @Test
    fun `executeCommand LOOP_LGS should switch to LGS mode`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP_LGS)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(CoreUiR.string.lowglucosesuspend)).thenReturn("LGS")
        whenever(rh.gs(eq(R.string.nfccommands_current_loop_mode), any())).thenReturn("LGS mode")

        val result = execute(NfcCommandCode.LOOP_LGS)

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.CLOSED_LOOP_LGS),
                eq(Action.LGS_LOOP_MODE),
                eq(Sources.NfcCommands),
                any(),
                any(),
                eq(effectiveProfile),
            )
        }
    }

    @Test
    fun `executeCommand LOOP_CLOSED should switch to closed loop`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.CLOSED_LOOP)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(CoreUiR.string.closedloop)).thenReturn("Closed")
        whenever(rh.gs(eq(R.string.nfccommands_current_loop_mode), any())).thenReturn("Closed loop")

        val result = execute(NfcCommandCode.LOOP_CLOSED)

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.CLOSED_LOOP),
                eq(Action.CLOSED_LOOP_MODE),
                eq(Sources.NfcCommands),
                any(),
                any(),
                eq(effectiveProfile),
            )
        }
    }

    // ── processAapsClient tests ────────────────────────────────────────────────
    /* Command removed
    @Test
    fun `executeCommand AAPSCLIENT_RESTART should send restart event`() {
        whenever(rh.gs(R.string.nfccommands_aapsclient_restart_sent)).thenReturn("AAPSClient restart sent")

        val result = execute(NfcCommandCode.AAPSCLIENT_RESTART)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("AAPSClient restart sent")
    }

     */

    // ── processPump tests ──────────────────────────────────────────────────────

    @Test
    fun `executeCommand PUMP_DISCONNECT should disconnect pump`() {
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(CoreUiR.string.pump_disconnected)).thenReturn("Pump disconnected")

        val result = execute(NfcCommandCode.PUMP_DISCONNECT, JSONObject().put(NfcJsonKeys.DURATION, 180))

        assertThat(result.success).isTrue()
        runTest {
            verify(loop).handleRunningModeChange(
                eq(RM.Mode.DISCONNECTED_PUMP),
                eq(Action.DISCONNECT),
                eq(Sources.NfcCommands),
                any(),
                eq(180),
                eq(effectiveProfile),
            )
        }
    }

    @Test
    fun `executeCommand PUMP_CONNECT returns connected when reconnect is not needed`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(emptyList()) }
        whenever(rh.gs(app.aaps.core.interfaces.R.string.connected)).thenReturn("Connected")

        val result = execute(NfcCommandCode.PUMP_CONNECT)

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Connected")
    }

    // ── processBasal tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand BASAL_STOP should cancel temp basal`() {
        whenever(rh.gs(CoreUiR.string.stoptemptarget)).thenReturn("Temp basal canceled")

        val result = execute(NfcCommandCode.BASAL_STOP)

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).cancelTempBasal(eq(true), any()) }
    }

    @Test
    fun `executeCommand BASAL_PCT should enqueue percent temp basal`() {
        whenever(constraintsChecker.applyBasalPercentConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(120, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = execute(NfcCommandCode.BASAL_PCT, JSONObject().put(NfcJsonKeys.PERCENT, 120).put(NfcJsonKeys.DURATION, 30))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).tempBasalPercent(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `executeCommand BASAL_ABS should enqueue absolute temp basal`() {
        whenever(constraintsChecker.applyBasalConstraints(any(), any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.5, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = execute(NfcCommandCode.BASAL_ABS, JSONObject().put(NfcJsonKeys.RATE, 1.5).put(NfcJsonKeys.DURATION, 30))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).tempBasalAbsolute(any(), any(), any(), any(), any()) }
    }

    // ── processExtended tests ──────────────────────────────────────────────────

    @Test
    fun `executeCommand EXTENDED_STOP should cancel extended bolus`() {
        whenever(rh.gs(R.string.nfccommands_extended_canceled)).thenReturn("Extended canceled")

        val result = execute(NfcCommandCode.EXTENDED_STOP)

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).cancelExtended() }
    }

    @Test
    fun `executeCommand EXTENDED_SET should enqueue extended bolus`() {
        whenever(constraintsChecker.applyExtendedBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(2.0, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_extended_set), any(), any())).thenReturn("Extended set")

        val result = execute(NfcCommandCode.EXTENDED_SET, JSONObject().put(NfcJsonKeys.AMOUNT, 2.0).put(NfcJsonKeys.DURATION, 60))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).extendedBolus(any(), any()) }
    }

    // ── processBolus tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand BOLUS should enqueue bolus`() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(bolusProgressData.isStopPressed).thenReturn(false)
        runTest { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")

        val result = execute(NfcCommandCode.BOLUS, JSONObject().put(NfcJsonKeys.AMOUNT, 1.0))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).bolus(any()) }
    }

    @Test
    fun `executeCommand BOLUS should handle user stop as success`() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        whenever(bolusProgressData.isStopPressed).thenReturn(true)
        runTest { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        
        // Mock a failure from commandQueue (which happens on stop on some pumps)
        runTest {
            whenever(commandQueue.bolus(any())).thenReturn(
                pumpEnactResultProvider.get().success(false).bolusDelivered(0.5)
            )
        }
        whenever(rh.gs(eq(CoreUiR.string.stop_pressed), any())).thenReturn("Stop pressed")

        val result = execute(NfcCommandCode.BOLUS, JSONObject().put(NfcJsonKeys.AMOUNT, 1.0))

        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Stop pressed")
        runTest { verify(commandQueue).bolus(any()) }
    }

    @Test
    fun `executeCommand BOLUS MEAL should enqueue bolus and set TT`() {
        whenever(constraintsChecker.applyBolusConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(1.0, aapsLogger),
        )
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        runTest { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        whenever(rh.gs(eq(R.string.nfccommands_command_executed), any())).thenReturn("Command executed")
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }

        val result = execute(NfcCommandCode.BOLUS, JSONObject().put(NfcJsonKeys.AMOUNT, 1.0).put(NfcJsonKeys.IS_MEAL, true))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).bolus(any()) }
        runTest { verify(persistenceLayer).insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()) }
    }

    // ── processCarbs tests ─────────────────────────────────────────────────────

    @Test
    fun `executeCommand CARBS should enqueue carbs`() {
        whenever(constraintsChecker.applyCarbsConstraints(any())).thenReturn(
            app.aaps.core.objects.constraints.ConstraintObject(20, aapsLogger),
        )
        whenever(rh.gs(eq(R.string.nfccommands_carbs_set), any())).thenReturn("Carbs set")

        val result = execute(NfcCommandCode.CARBS, JSONObject().put(NfcJsonKeys.AMOUNT, 20))

        assertThat(result.success).isTrue()
        runTest { verify(commandQueue).bolus(any()) }
    }

    // ── processTarget tests ────────────────────────────────────────────────────

    @Test
    fun `executeCommand TARGET_MEAL should set eating soon target`() {
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = execute(NfcCommandCode.TARGET_MEAL)

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET_STOP should cancel temp target`() {
        runTest {
            whenever(persistenceLayer.cancelCurrentTemporaryTargetIfAny(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")
        whenever(rh.gsNotLocalised(R.string.nfccommands_tt_canceled)).thenReturn("TT canceled")

        val result = execute(NfcCommandCode.TARGET_STOP)

        assertThat(result.success).isTrue()
        runTest { verify(persistenceLayer).cancelCurrentTemporaryTargetIfAny(any(), eq(Action.CANCEL_TT), eq(Sources.NfcCommands), anyOrNull(), any()) }
    }

    @Test
    fun `executeCommand TARGET_ACTIVITY should set activity target`() {
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = execute(NfcCommandCode.TARGET_ACTIVITY)

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET_HYPO should set hypo target`() {
        whenever(preferences.get(StringNonKey.TempTargetPresets)).thenReturn("[]")
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = execute(NfcCommandCode.TARGET_HYPO)

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand TARGET_MANUAL should set manual target`() {
        runTest {
            whenever(persistenceLayer.insertAndCancelCurrentTemporaryTarget(any(), any(), any(), anyOrNull(), any()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }
        whenever(rh.gs(eq(R.string.nfccommands_tt_set), any(), any())).thenReturn("Target set")

        val result = execute(NfcCommandCode.TARGET_MANUAL, JSONObject().put(NfcJsonKeys.GLUCOSE, 100.0).put(NfcJsonKeys.DURATION, 30))

        assertThat(result.success).isTrue()
    }

    @Test
    fun `executeCommand RUN_SCENE should run scene`() {
        runTest {
            whenever(sceneAutomationApi.runScene(any(), anyOrNull())).thenReturn(SceneAutomationResult.Success)
        }
        whenever(rh.gs(CoreUiR.string.ok)).thenReturn("OK")

        val result = execute(NfcCommandCode.RUN_SCENE, JSONObject().put(NfcJsonKeys.SCENE_ID, "scene1"))

        assertThat(result.success).isTrue()
        runTest { verify(sceneAutomationApi).runScene(eq("scene1"), anyOrNull()) }
    }

    @Test
    fun `executeCommand BOLUS_WIZARD should execute wizard bolus`() {
        val params = JSONObject()
            .put(NfcJsonKeys.AMOUNT, 20)
            .put(NfcJsonKeys.PERCENT, 100)
        
        val prepared = WizardBolusExecutor.PrepareResult.Preview(insulin = 1.5, carbs = 20, bolusId = 123L)
        plugin.setActionState(params.toString(), prepared)
        runTest { 
            whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP)
            whenever(wizardBolusExecutor.confirm(eq(123L), any(), any(), any(), any())).thenReturn(WizardBolusExecutor.ConfirmResult.Delivered)
        }
        whenever(rh.gs(eq(R.string.smscommunicator_bolus_delivered), any())).thenReturn("Bolus delivered")

        val result = execute(NfcCommandCode.BOLUS_WIZARD, params)

        assertThat(result.success).isTrue()
        runTest { verify(wizardBolusExecutor).confirm(eq(123L), eq(Sources.NfcCommands), any(), any(), any()) }
    }

    @Test
    fun `BolusWizardAction formatParams should perform calculation and store state`() = runTest {
        val params = JSONObject()
            .put(NfcJsonKeys.AMOUNT, 20)
            .put(NfcJsonKeys.PERCENT, 100)
        val action = plugin.getAction(NfcCommandCode.BOLUS_WIZARD)
        action.params = params

        whenever(profileFunction.getOriginalProfileName()).thenReturn("Default")
        val prepared = WizardBolusExecutor.PrepareResult.Preview(insulin = 1.5, carbs = 20, bolusId = 123L)
        whenever(wizardBolusExecutor.prepareWizard(any())).thenReturn(prepared)
        whenever(rh.gs(any<Int>(), eq(1.5))).thenReturn("Going to deliver 1.5U")
        whenever(rh.gs(any<Int>(), eq(20))).thenReturn("20g carbs")

        val result = action.formatParams()

        assertThat(result).contains("Going to deliver")
        verify(wizardBolusExecutor).prepareWizard(any())
        assertThat(plugin.getActionState(params.toString())).isEqualTo(prepared)
    }

    // ── processProfile tests ───────────────────────────────────────────────────

    @Test
    fun `executeCommand PROFILE_SWITCH should create profile switch`() {
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(mockProfileStore))
        runBlocking { whenever(profileFunction.getRunningOrRequestedICfg()).thenReturn(ICfg("",55,8.0,1.0))}
        runTest {
            whenever(profileFunction.createProfileSwitch(any(), any(), any(), any(), any(), any(), any(), any(), anyOrNull(), any(), any()))
                .thenReturn(mock())
        }
        whenever(rh.gs(R.string.nfccommands_profile_switch_created)).thenReturn("Profile switch created")

        val result = execute(NfcCommandCode.PROFILE_SWITCH, JSONObject().put(NfcJsonKeys.PROFILE_NAME, "Default").put(NfcJsonKeys.PERCENT, 100))

        assertThat(result.success).isTrue()
        runTest {
            verify(profileFunction).createProfileSwitch(
                eq(mockProfileStore),
                eq("Default"),
                eq(0),
                eq(100),
                eq(0),
                any(),
                eq(Action.PROFILE_SWITCH),
                eq(Sources.NfcCommands),
                any(),
                any(),
                any(),
            )
        }
    }

    // ── general command tests ──────────────────────────────────────────────────

    @Test
    fun `executeCommand should fail when remote commands not allowed`() {
        whenever(preferences.get(BooleanKey.NfcAllowRemoteCommands)).thenReturn(false)
        whenever(rh.gs(R.string.nfccommands_remote_command_not_allowed)).thenReturn("Remote commands not allowed")

        val result = execute(NfcCommandCode.LOOP_STOP)

        assertThat(result.success).isFalse()
    }

    @Test
    fun `executeCommand BOLUS MEAL should respect cooldown`() {
        whenever(commandQueue.bolusInQueue()).thenReturn(false)
        runTest { whenever(loop.runningMode()).thenReturn(RM.Mode.CLOSED_LOOP) }
        whenever(rh.gs(R.string.nfccommands_remote_bolus_not_allowed)).thenReturn("Remote bolus not allowed")
        val now = Constants.REMOTE_BOLUS_MIN_DISTANCE * 2
        whenever(dateUtil.now()).thenReturn(now)
        plugin.setLastRemoteBolusTime(now)

        val result = execute(NfcCommandCode.BOLUS, JSONObject().put(NfcJsonKeys.AMOUNT, 1.0).put(NfcJsonKeys.IS_MEAL, true))

        assertThat(result.success).isFalse()
    }

    /* COmmand Remonved
    @Test
    fun `executeCommand RESTART should exit app`() {
        whenever(rh.gs(R.string.nfccommands_restarting)).thenReturn("Restarting")

        val result = execute(NfcCommandCode.RESTART)

        assertThat(result.success).isTrue()
        verify(configBuilder).exitApp(eq("NFC"), eq(Sources.NfcCommands), eq(true))
    }
    */

    // ── executeCascade tests ───────────────────────────────────────────────────

    @Test
    fun `executeCascade all succeed returns success with combined message`() {
        runTest { whenever(loop.allowedNextModes()).thenReturn(listOf(RM.Mode.DISABLED_LOOP)) }
        runTest { whenever(loop.handleRunningModeChange(any(), any(), any(), any(), any(), any())).thenReturn(true) }
        whenever(rh.gs(R.string.nfccommands_loop_has_been_disabled)).thenReturn("Loop disabled")
        whenever(rh.gs(CoreUiR.string.stoptemptarget)).thenReturn("Temp basal canceled")

        val cmd1 = NfcTagStore.buildCommand(NfcCommandCode.LOOP_STOP)
        val cmd2 = NfcTagStore.buildCommand(NfcCommandCode.BASAL_STOP)
        val result = cascade(listOf(cmd1, cmd2))

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Loop disabled")
        assertThat(result.message).contains("Temp basal canceled")
    }
}
