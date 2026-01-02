package app.aaps.plugins.automation.actions

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileSource
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.plugins.automation.triggers.Trigger
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever

open class
ActionsTestBase : TestBaseWithProfile() {

    @Mock lateinit var profilePlugin: ProfileSource
    @Mock lateinit var smsCommunicator: SmsCommunicator
    @Mock lateinit var loop: Loop
    @Mock lateinit var persistenceLayer: PersistenceLayer

    init {
        addInjector {
            if (it is Action) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.pumpEnactResultProvider = pumpEnactResultProvider
            }
            if (it is ActionStopTempTarget) {
                it.dateUtil = dateUtil
                it.persistenceLayer = persistenceLayer
            }
            if (it is ActionStartTempTarget) {
                it.activePlugin = activePlugin
                it.persistenceLayer = persistenceLayer
                it.profileFunction = profileFunction
                it.dateUtil = dateUtil
                it.profileUtil = profileUtil
            }
            if (it is ActionSendSMS) {
                it.smsCommunicator = smsCommunicator
            }
            if (it is ActionProfileSwitch) {
                it.activePlugin = activePlugin
                it.profileFunction = profileFunction
                it.dateUtil = dateUtil
            }
            if (it is ActionProfileSwitchPercent) {
                it.profileFunction = profileFunction
            }
            if (it is ActionNotification) {
                it.rxBus = rxBus
            }
            if (it is ActionSMBChange) {
                it.dateUtil = dateUtil
                it.preferences = preferences
            }
            if (it is ActionCarePortalEvent) {
                it.persistenceLayer = persistenceLayer
                it.dateUtil = dateUtil
                it.profileFunction = profileFunction
            }
            if (it is Trigger) {
                it.rh = rh
                it.profileFunction = profileFunction
            }
        }
    }

    @BeforeEach
    fun mock() {
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(profileFunction.getProfile()).thenReturn(validProfile)
        whenever(activePlugin.activeProfileSource).thenReturn(profilePlugin)
        whenever(profilePlugin.profile).thenReturn(getValidProfileStore())
        whenever(loop.handleRunningModeChange(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyInt(), anyOrNull())).thenReturn(true)

        whenever(rh.gs(app.aaps.core.ui.R.string.ok)).thenReturn("OK")
        whenever(rh.gs(app.aaps.core.ui.R.string.error)).thenReturn("Error")
    }
}