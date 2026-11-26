package app.aaps.ui.compose.permissionsSheet

import app.aaps.core.interfaces.plugin.PermissionGroup
import app.aaps.core.interfaces.plugin.PluginPermissions
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class PermissionsViewModelTest {

    @Mock private lateinit var pluginPermissions: PluginPermissions
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: PermissionsViewModel

    /** What "Not now" stored on this phone, as the real preferences would keep it. */
    private var declinedStored = ""

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // requestPermission()/onPermissionsDenied() use viewModelScope; setMain keeps those deferred.
        // Construction reads nothing from deps (no init block), so no stubbing is required to build.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(preferences.get(StringNonKey.PermissionsDeclined)).thenAnswer { declinedStored }
        doAnswer { declinedStored = it.getArgument(1); null }.whenever(preferences).put(eq(StringNonKey.PermissionsDeclined), any<String>())
        sut = PermissionsViewModel(pluginPermissions, preferences)
    }

    private val needed = PermissionGroup(listOf("android.permission.POST_NOTIFICATIONS"), TextRef.Literal("n"), TextRef.Literal("n"))
    private val optional = PermissionGroup(listOf("android.permission.SYSTEM_ALERT_WINDOW"), TextRef.Literal("o"), TextRef.Literal("o"), special = true, optional = true)

    private fun missing(vararg groups: PermissionGroup) {
        whenever(pluginPermissions.collectAllPermissions()).thenReturn(listOf(needed, optional))
        whenever(pluginPermissions.collectMissingPermissions()).thenReturn(groups.toList())
    }

    /** Recommended and not answered yet: it is shown, once, like a needed one. */
    @Test
    fun `a missing optional permission opens the sheet until it is declined`() {
        missing(optional)

        sut.refresh()

        assertThat(sut.uiState.value.showSheet).isTrue()
        assertThat(sut.uiState.value.hasAnyMissing).isTrue()
    }

    /** "Not now": it no longer opens the sheet by itself nor keeps the button in the bottom bar, but it stays listed. */
    @Test
    fun `a declined optional permission no longer opens the sheet`() {
        missing(optional)
        sut.refresh()

        sut.decline(optional)
        assertThat(sut.uiState.value.hasAnyMissing).isFalse()
        sut.refresh()

        assertThat(sut.uiState.value.showSheet).isFalse()
        assertThat(sut.uiState.value.items.single { it.group == optional }.declined).isTrue()
        assertThat(sut.uiState.value.items.single { it.group == optional }.granted).isFalse()
    }

    /** A needed one cannot be declined: it keeps opening the sheet. */
    @Test
    fun `a needed permission cannot be declined`() {
        missing(needed)

        sut.decline(needed)
        sut.refresh()

        assertThat(sut.uiState.value.showSheet).isTrue()
        assertThat(declinedStored).isEmpty()
    }

    /** A declined optional one beside a missing needed one: the sheet opens for the needed one, and lists both. */
    @Test
    fun `a missing needed permission still opens the sheet`() {
        declinedStored = "android.permission.SYSTEM_ALERT_WINDOW"
        missing(needed, optional)

        sut.refresh()

        assertThat(sut.uiState.value.showSheet).isTrue()
        assertThat(sut.uiState.value.items).hasSize(2)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState has no items and sheet hidden`() {
        val state = sut.uiState.value
        assertThat(state.items).isEmpty()
        assertThat(state.hasAnyMissing).isFalse()
        assertThat(state.showSheet).isFalse()
    }

    @Test
    fun `showSheet sets showSheet true`() {
        sut.showSheet()
        assertThat(sut.uiState.value.showSheet).isTrue()
    }

    @Test
    fun `dismissSheet sets showSheet false`() {
        sut.showSheet()
        sut.dismissSheet()
        assertThat(sut.uiState.value.showSheet).isFalse()
    }

    @Test
    fun `refresh with no permissions produces empty granted state`() {
        whenever(pluginPermissions.collectAllPermissions()).thenReturn(emptyList())
        whenever(pluginPermissions.collectMissingPermissions()).thenReturn(emptyList())

        sut.refresh()

        val state = sut.uiState.value
        assertThat(state.items).isEmpty()
        assertThat(state.hasAnyMissing).isFalse()
        assertThat(state.showSheet).isFalse()
    }
}
