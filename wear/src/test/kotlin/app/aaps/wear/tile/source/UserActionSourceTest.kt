package app.aaps.wear.tile.source

import android.content.Context
import android.content.res.Resources
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.EventData.UserAction.UserActionEntry
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.AAPSLoggerTest
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.BackgroundActionActivity
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [UserActionSource] — the tile source that maps up to four user-action entries
 * (deserialized from an [EventData.UserAction] payload stored in [SP]) into tile [app.aaps.wear.tile.Action]s.
 *
 * Fresh mocks are built (rather than extending WearTestBase) because the class reads
 * [Context.getResources] which the shared base does not stub.
 */
class UserActionSourceTest {

    private val aapsLogger = AAPSLoggerTest()
    private val context: Context = mock()
    private val resources: Resources = mock()
    private val sp: SP = mock()

    private val confirmationMessage = "User Action Requested"

    private lateinit var source: UserActionSource

    /** Serialize an [EventData.UserAction] payload and stub SP to return it for the data key. */
    private fun stubEntries(vararg entries: UserActionEntry) {
        val payload = EventData.UserAction(ArrayList(entries.toList())).serialize()
        whenever(sp.getString(eq(R.string.key_user_action_data), any())).thenReturn(payload)
    }

    @BeforeEach
    fun setup() {
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getString(R.string.action_user_action_confirmation)).thenReturn(confirmationMessage)
        source = UserActionSource(context, sp, aapsLogger)
    }

    @Test
    fun getSelectedActionsMapsSingleEntry() {
        stubEntries(UserActionEntry(timeStamp = 1_000L, id = "id-1", title = "Do thing"))

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(1)
        val action = actions[0]
        assertThat(action.buttonText).isEqualTo("Do thing")
        assertThat(action.iconRes).isEqualTo(R.drawable.ic_user_options)
        assertThat(action.activityClass).isEqualTo(BackgroundActionActivity::class.java.name)
        assertThat(action.message).isEqualTo(confirmationMessage)
        assertThat(action.action).isEqualTo(EventData.ActionUserActionPreCheck("id-1", "Do thing"))
    }

    @Test
    fun getSelectedActionsPropagatesIdAndTitleInOrder() {
        stubEntries(
            UserActionEntry(timeStamp = 1L, id = "a", title = "Alpha"),
            UserActionEntry(timeStamp = 2L, id = "b", title = "Bravo"),
            UserActionEntry(timeStamp = 3L, id = "c", title = "Charlie")
        )

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(3)
        assertThat(actions.map { it.buttonText }).containsExactly("Alpha", "Bravo", "Charlie").inOrder()
        assertThat(actions.map { it.action }).containsExactly(
            EventData.ActionUserActionPreCheck("a", "Alpha"),
            EventData.ActionUserActionPreCheck("b", "Bravo"),
            EventData.ActionUserActionPreCheck("c", "Charlie")
        ).inOrder()
    }

    @Test
    fun getSelectedActionsCapsAtFour() {
        stubEntries(
            UserActionEntry(timeStamp = 1L, id = "1", title = "One"),
            UserActionEntry(timeStamp = 2L, id = "2", title = "Two"),
            UserActionEntry(timeStamp = 3L, id = "3", title = "Three"),
            UserActionEntry(timeStamp = 4L, id = "4", title = "Four"),
            UserActionEntry(timeStamp = 5L, id = "5", title = "Five"),
            UserActionEntry(timeStamp = 6L, id = "6", title = "Six")
        )

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(4)
        assertThat(actions.map { it.buttonText }).containsExactly("One", "Two", "Three", "Four").inOrder()
    }

    @Test
    fun getSelectedActionsReturnsExactlyFourWhenFourEntries() {
        stubEntries(
            UserActionEntry(timeStamp = 1L, id = "1", title = "One"),
            UserActionEntry(timeStamp = 2L, id = "2", title = "Two"),
            UserActionEntry(timeStamp = 3L, id = "3", title = "Three"),
            UserActionEntry(timeStamp = 4L, id = "4", title = "Four")
        )

        val actions = source.getSelectedActions()

        assertThat(actions).hasSize(4)
    }

    @Test
    fun getSelectedActionsEmptyForNoEntries() {
        stubEntries()

        val actions = source.getSelectedActions()

        assertThat(actions).isEmpty()
    }

    @Test
    fun getValidForIsNull() {
        assertThat(source.getValidFor()).isNull()
    }

    @Test
    fun getResourceReferencesReturnsSingleDrawable() {
        assertThat(source.getResourceReferences(resources)).containsExactly(R.drawable.ic_user_options)
    }
}
