package app.aaps.plugins.automation.compose

import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.plugins.automation.AutomationEventFactory
import app.aaps.plugins.automation.AutomationStringsValues
import app.aaps.plugins.automation.actions.ActionCarePortalEvent
import app.aaps.plugins.automation.actions.ActionFactory
import app.aaps.plugins.automation.elements.InputCarePortalMenu
import app.aaps.plugins.automation.triggers.TriggerDeps
import app.aaps.plugins.automation.triggers.TriggerFactory
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.mock

class AutomationStateHolderTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var pumpEnactResultProvider: () -> PumpEnactResult
    private val rh = generatedTextResolver("automation" to AutomationStringsValues::textOf)

    private val triggerDeps: TriggerDeps by lazy {
        TriggerDeps(aapsLogger, mock(), rh, profileFunction, mock(), preferences, mock(), mock(), mock(), mock(), mock(), dateUtil)
    }

    private val eventFactory: AutomationEventFactory by lazy {
        val actionFactory = ActionFactory(
            triggerDeps, aapsLogger, rh, pumpEnactResultProvider, mock(), dateUtil, mock(), mock(), mock(),
            profileFunction, mock(), mock(), mock(), mock(), mock(), preferences, mock(), mock(), mock(), mock(),
            mock(), mock(), mock()
        )
        AutomationEventFactory(aapsLogger, dateUtil, actionFactory, TriggerFactory(triggerDeps, mock(), mock(), mock()), triggerDeps)
    }

    /** An action editor changes a field in place. The action's card used to keep its old title and icon. */
    @Test
    fun changingAnActionInItsEditorUpdatesItsCard() {
        val holder = AutomationStateHolder(plugin = mock(), rxBus = mock(), aapsLogger = aapsLogger, automationEventFactory = eventFactory)
        holder.openNew()
        val careportal = ActionCarePortalEvent(aapsLogger, rh, pumpEnactResultProvider, mock(), profileFunction, dateUtil, mock())
        holder.addAction(careportal)
        assertThat(holder.editState.value.actions.single().elementType).isEqualTo(ElementType.NOTE)

        careportal.cpEvent.value = InputCarePortalMenu.EventType.CANNULA_CHANGE
        holder.onWorkingEventChanged()

        val card = holder.editState.value.actions.single()
        assertThat(card.elementType).isEqualTo(ElementType.CANNULA_CHANGE)
        assertThat(card.icon).isEqualTo(IcCannulaChange)
        assertThat(card.title).startsWith(rh.gs(InputCarePortalMenu.EventType.CANNULA_CHANGE.stringRes))
    }
}
