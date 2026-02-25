package app.aaps.plugins.sync.xdrip

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.xdrip.mvvm.XdripMvvmRepository
import app.aaps.plugins.sync.xdrip.mvvm.XdripViewModel
import dagger.android.support.DaggerFragment
import kotlinx.coroutines.launch
import javax.inject.Inject

class XdripFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Inject lateinit var xdripPlugin: XdripPlugin
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var xdripMvvmRepository: XdripMvvmRepository
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus

    companion object {

        const val ID_MENU_CLEAR_LOG = 511
        const val ID_MENU_FULL_SYNC = 512
    }

    override var plugin: PluginBase? = null

    private var viewModel: XdripViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel = XdripViewModel(
            rh = rh,
            xdripMvvmRepository = xdripMvvmRepository,
            dataSyncSelector = dataSyncSelector
        )

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences,
                    LocalRxBus provides rxBus,
                    LocalDateUtil provides dateUtil
                ) {
                    AapsTheme {
                        viewModel?.let { vm ->
                            XdripScreen(
                                viewModel = vm,
                                dateUtil = dateUtil,
                                rh = rh
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel?.loadInitialData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel = null
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                xdripMvvmRepository.clearLog()
                true
            }

            ID_MENU_FULL_SYNC -> {
                handleFullSync()
                true
            }

            else              -> false
        }

    private fun handleFullSync() {
        uiInteraction.showOkCancelDialog(
            context = requireActivity(),
            title = R.string.xdrip,
            message = R.string.full_xdrip_sync_comment,
            ok = {
                viewLifecycleOwner.lifecycleScope.launch {
                    dataSyncSelector.resetToNextFullSync()
                }
            }
        )
    }
}
