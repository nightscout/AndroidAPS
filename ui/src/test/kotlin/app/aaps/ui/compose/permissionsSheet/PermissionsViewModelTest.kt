package app.aaps.ui.compose.permissionsSheet

import android.content.Context
import app.aaps.core.interfaces.plugin.ActivePlugin
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class PermissionsViewModelTest {

    @Mock private lateinit var context: Context
    @Mock private lateinit var activePlugin: ActivePlugin

    private lateinit var sut: PermissionsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // requestPermission()/onPermissionsDenied() use viewModelScope; setMain keeps those deferred.
        // Construction reads nothing from deps (no init block), so no stubbing is required to build.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = PermissionsViewModel(context, activePlugin)
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
        whenever(activePlugin.collectAllPermissions(any())).thenReturn(emptyList())
        whenever(activePlugin.collectMissingPermissions(any())).thenReturn(emptyList())

        sut.refresh()

        val state = sut.uiState.value
        assertThat(state.items).isEmpty()
        assertThat(state.hasAnyMissing).isFalse()
        assertThat(state.showSheet).isFalse()
    }
}
