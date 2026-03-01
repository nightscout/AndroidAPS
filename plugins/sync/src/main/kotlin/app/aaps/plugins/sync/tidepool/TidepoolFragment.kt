package app.aaps.plugins.sync.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.di.ViewModelFactory
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.compose.TidepoolScreen
import app.aaps.plugins.sync.tidepool.compose.TidepoolViewModel
import app.aaps.plugins.sync.tidepool.events.EventTidepoolDoUpload
import app.aaps.plugins.sync.tidepool.keys.TidepoolLongNonKey
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class TidepoolFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var authFlowOut: AuthFlowOut
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var tidepoolRepository: TidepoolRepository
    @Inject lateinit var viewModelFactory: ViewModelFactory

    private val viewModel: TidepoolViewModel by viewModels { viewModelFactory }

    companion object {

        const val ID_MENU_LOGIN = 530
        const val ID_MENU_LOGOUT = 531
        const val ID_MENU_SEND_NOW = 532
        const val ID_MENU_FULL_SYNC = 534
        const val ID_MENU_CLEAR_LOG = 535
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences,
                    LocalRxBus provides rxBus,
                    LocalDateUtil provides dateUtil
                ) {
                    AapsTheme {
                        TidepoolScreen(
                            viewModel = viewModel,
                            dateUtil = dateUtil,
                            setToolbarConfig = { },
                            onNavigateBack = {},
                            onSettings = null,
                            onLogin = { authFlowOut.doTidePoolInitialLogin("menu") },
                            onLogout = {
                                authFlowOut.clearAllSavedData()
                                tidepoolUploader.resetInstance()
                            },
                            onUploadNow = { rxBus.send(EventTidepoolDoUpload()) },
                            onFullSync = { preferences.put(TidepoolLongNonKey.LastEnd, 0) },
                            onClearLog = { tidepoolRepository.clearLog() }
                        )
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInitialData()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_LOGIN, 0, rh.gs(app.aaps.core.ui.R.string.login)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_LOGOUT, 0, rh.gs(app.aaps.core.ui.R.string.logout)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.upload_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_LOGIN     -> {
                authFlowOut.doTidePoolInitialLogin("menu")
                true
            }

            ID_MENU_LOGOUT    -> {
                authFlowOut.clearAllSavedData()
                tidepoolUploader.resetInstance()
                true
            }

            ID_MENU_SEND_NOW  -> {
                rxBus.send(EventTidepoolDoUpload())
                true
            }

            ID_MENU_FULL_SYNC -> {
                preferences.put(TidepoolLongNonKey.LastEnd, 0)
                true
            }

            ID_MENU_CLEAR_LOG -> {
                tidepoolRepository.clearLog()
                true
            }

            else              -> false
        }
}
