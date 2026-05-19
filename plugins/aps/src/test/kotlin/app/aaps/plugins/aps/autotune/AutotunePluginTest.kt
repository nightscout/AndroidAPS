package app.aaps.plugins.aps.autotune

import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.plugins.aps.autotune.data.ATProfile
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mock
import javax.inject.Provider

class AutotunePluginTest : TestBaseWithProfile() {

    @Mock lateinit var autotuneFS: AutotuneFS
    @Mock lateinit var autotuneIob: AutotuneIob
    @Mock lateinit var autotunePrep: AutotunePrep
    @Mock lateinit var autotuneCore: AutotuneCore
    @Mock lateinit var uel: UserEntryLogger
    @Mock lateinit var loop: Loop
    private lateinit var autotunePlugin: AutotunePlugin

    @BeforeEach fun prepare() {
        val atProfileProvider = Provider {
            ATProfile(preferences, profileUtil, dateUtil, rh, profileStoreProvider, aapsLogger)
        }
        autotunePlugin = AutotunePlugin(
            aapsLogger = aapsLogger,
            rh = rh,
            preferences = preferences,
            rxBus = rxBus,
            profileFunction = profileFunction,
            profileUtil = profileUtil,
            dateUtil = dateUtil,
            insulin = insulin,
            profileRepository = profileRepository,
            autotuneFS = autotuneFS,
            autotuneIob = autotuneIob,
            autotunePrep = autotunePrep,
            autotuneCore = autotuneCore,
            config = config,
            uel = uel,
            loop = loop,
            profileStoreProvider = profileStoreProvider,
            atProfileProvider = atProfileProvider
        )
    }
}
