package app.aaps.plugins.sync.nsShared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalRxBus
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.di.ViewModelFactory
import app.aaps.plugins.sync.nsShared.compose.NSClientScreen
import app.aaps.plugins.sync.nsShared.compose.NSClientViewModel
import dagger.android.support.DaggerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val CLEANUP_RETENTION_DAYS = 93L

class NSClientFragment : DaggerFragment(), PluginFragment {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var nsClientRepository: NSClientRepository
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var viewModelFactory: ViewModelFactory

    override var plugin: PluginBase? = null
    private val nsClientPlugin get() = activePlugin.activeNsClient

    private val viewModel: NSClientViewModel by viewModels { viewModelFactory }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(
                    LocalPreferences provides preferences,
                    LocalRxBus provides rxBus,
                    LocalDateUtil provides dateUtil
                ) {
                    AapsTheme {
                        NSClientScreen(
                            viewModel = viewModel,
                            dateUtil = dateUtil,
                            title = rh.gs(R.string.ns_client),
                            setToolbarConfig = { },
                            onNavigateBack = {
                                activity?.onBackPressedDispatcher?.onBackPressed()
                            },
                            onPauseChanged = { isChecked ->
                                uel.log(action = if (isChecked) Action.NS_PAUSED else Action.NS_RESUME, source = Sources.NSClient)
                                nsClientPlugin?.pause(isChecked)
                                viewModel.updatePaused(isChecked)
                            },
                            onClearLog = {
                                nsClientRepository.clearLog()
                            },
                            onSendNow = {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                                    nsClientPlugin?.resend("GUI")
                                }
                            },
                            onFullSync = {
                                handleFullSync()
                            },
                            onSettings = {
                                uiInteraction.runPreferencesForPlugin(requireActivity(), nsClientPlugin?.javaClass?.simpleName)
                            }
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

    private fun handleFullSync() {
        uiInteraction.showOkCancelDialog(
            context = requireActivity(), title = R.string.ns_client, message = R.string.full_sync_comment,
            ok = {
                uiInteraction.showOkCancelDialog(
                    context = requireActivity(),
                    title = R.string.ns_client,
                    message = app.aaps.core.ui.R.string.cleanup_db_confirm_sync,
                    ok = {
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    persistenceLayer.cleanupDatabase(CLEANUP_RETENTION_DAYS, deleteTrackedChanges = true)
                                }
                                if (result.isNotEmpty())
                                    uiInteraction.showOkDialog(
                                        context = requireActivity(),
                                        title = rh.gs(app.aaps.core.ui.R.string.result),
                                        message = "<b>" + rh.gs(app.aaps.core.ui.R.string.cleared_entries) + "</b><br>" + result
                                    )
                                aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                                withContext(Dispatchers.IO) {
                                    nsClientPlugin?.resetToFullSync()
                                    nsClientPlugin?.resend("FULL_SYNC")
                                }
                            } catch (e: Exception) {
                                aapsLogger.error("Error cleaning up databases", e)
                            }
                        }
                        uel.log(action = Action.CLEANUP_DATABASES, source = Sources.NSClient)
                    },
                    cancel = {
                        viewLifecycleOwner.lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                nsClientPlugin?.resetToFullSync()
                                nsClientPlugin?.resend("FULL_SYNC")
                            }
                        }
                    }
                )
            }
        )
    }
}
