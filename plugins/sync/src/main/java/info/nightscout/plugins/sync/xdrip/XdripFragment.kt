package info.nightscout.plugins.sync.xdrip

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import dagger.android.support.DaggerFragment
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginFragment
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.databinding.XdripFragmentBinding
import info.nightscout.plugins.sync.xdrip.events.EventXdripUpdateGUI
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class XdripFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dataSyncSelector: DataSyncSelectorXdripImpl
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
        menu.setGroupDividerEnabled(true)
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
                        context, rh.gs(R.string.ns_client), rh.gs(R.string.full_sync_comment),
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
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.log.text = xdripPlugin.textLog()
        val size = dataSyncSelector.queueSize()
        binding.queue.text = if (size >= 0) size.toString() else rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short)
    }
}