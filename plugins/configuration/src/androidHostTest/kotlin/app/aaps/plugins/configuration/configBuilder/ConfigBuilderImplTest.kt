package app.aaps.plugins.configuration.configBuilder

import android.content.Context
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the PR2 ConfigBuilder ↔ ActivePlugin-key bridge write path: a plugin switch mirrors the new
 * selection (by [PluginBase.pluginId]) into the synthetic synced key, and the write is suppressed when
 * the key already holds that value (the no-op guard that prevents spurious publishes / echo). The async
 * observer-adoption direction is left to integration/device coverage.
 */
internal class ConfigBuilderImplTest {

    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var uel: UserEntryLogger
    @Mock private lateinit var pumpSync: PumpSync
    @Mock private lateinit var context: Context
    @Mock private lateinit var config: Config
    @Mock private lateinit var sens2: PluginBase
    @Mock private lateinit var sens2Desc: PluginDescription

    private lateinit var sut: ConfigBuilderImpl
    private var sensEnabled = false   // stateful so setPluginEnabled is reflected by isEnabled

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(config.AAPSCLIENT).thenReturn(true)   // skip the UEL block in performPluginSwitch

        whenever(sens2.getType()).thenReturn(PluginType.SENSITIVITY)
        whenever(sens2.pluginId).thenReturn("Sens2")
        whenever(sens2.pluginDescription).thenReturn(sens2Desc)
        whenever(sens2Desc.alwaysEnabled).thenReturn(false)
        whenever(sens2.isEnabled(PluginType.SENSITIVITY)).thenAnswer { sensEnabled }
        whenever(sens2.isEnabled()).thenAnswer { sensEnabled }
        whenever(sens2.setPluginEnabled(eq(PluginType.SENSITIVITY), any())).thenAnswer { sensEnabled = it.getArgument(1); }

        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsListByInterface(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(activePlugin.getSpecificPluginsList(any())).thenReturn(arrayListOf<PluginBase>(sens2))
        whenever(preferences.get(any<StringNonPreferenceKey>())).thenReturn("")

        sut = ConfigBuilderImpl(aapsLogger, rh, preferences, rxBus, activePlugin, uel, pumpSync, context, config)
    }

    /** A local plugin switch mirrors the new selection (by pluginId) into the synced ActivePlugin key. */
    @Test
    fun performPluginSwitchMirrorsSelectionIntoKey() {
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences).put(StringNonKey.ActivePluginSensitivity, "Sens2")
    }

    /** No-op guard: when the key already holds the current selection, the mirror write is skipped. */
    @Test
    fun syncSkipsNoOpWriteWhenUnchanged() {
        whenever(preferences.get(StringNonKey.ActivePluginSensitivity)).thenReturn("Sens2")  // already current
        sut.performPluginSwitch(sens2, true, PluginType.SENSITIVITY)
        verify(preferences, never()).put(eq(StringNonKey.ActivePluginSensitivity), any<String>())
    }

    /**
     * SSOT guard: PluginType.singleSelect / selectionSyncs are the single source of truth, and the
     * activePluginKey wiring must agree with them for EVERY type — a single-select type has exactly one
     * ActivePlugin key, and a selection-syncing type's key carries a SyncSpec. Fails the build if either
     * drifts (e.g. a new synced category whose key wasn't flagged, or a flag flipped without the key).
     */
    @Test
    fun activePluginKeyWiringMatchesPluginTypeFlags() {
        PluginType.entries.forEach { type ->
            assertEquals(type.singleSelect, sut.activePluginKey(type) != null, "singleSelect mismatch for $type")
            assertEquals(type.selectionSyncs, sut.activePluginKey(type)?.sync != null, "selectionSyncs mismatch for $type")
        }
    }
}
