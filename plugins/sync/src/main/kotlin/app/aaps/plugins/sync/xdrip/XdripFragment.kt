package app.aaps.plugins.sync.xdrip

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.XdripFragmentBinding
import app.aaps.plugins.sync.xdrip.events.EventXdripUpdateGUI
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class XdripFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdrip
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var xdripPlugin: XdripPlugin
    @Inject lateinit var config: Config

    companion object {

        const val ID_MENU_CLEAR_LOG = 511
        const val ID_MENU_FULL_SYNC = 512
    }

    override var plugin: PluginBase? = null

    private val disposable = CompositeDisposable()
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var _binding: XdripFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        XdripFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                xdripPlugin.clearLog()
                true
            }

            ID_MENU_FULL_SYNC -> {
                context?.let { context ->
                    OKDialog.showConfirmation(
                        context, rh.gs(R.string.xdrip), rh.gs(R.string.full_xdrip_sync_comment),
                        Runnable { handler.post { dataSyncSelector.resetToNextFullSync() } }
                    )
                }
                true
            }

            else              -> false
        }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventXdripUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.log.text = xdripPlugin.textLog()
        val size = dataSyncSelector.queueSize()
        binding.queue.text = if (size >= 0) size.toString() else rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
    }
}