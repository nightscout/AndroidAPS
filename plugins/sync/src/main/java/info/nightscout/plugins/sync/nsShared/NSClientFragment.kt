package info.nightscout.plugins.sync.nsShared

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import dagger.android.support.DaggerFragment
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginFragment
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.sync.NsClient
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.databinding.NsClientFragmentBinding
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGUI
import info.nightscout.plugins.sync.nsclientV3.NSClientV3Plugin
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientRestart
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class NSClientFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config

    companion object {

        const val ID_MENU_CLEAR_LOG = 507
        const val ID_MENU_RESTART = 508
        const val ID_MENU_SEND_NOW = 509
        const val ID_MENU_FULL_SYNC = 510
        const val ID_MENU_TEST = 601
    }

    override var plugin: PluginBase? = null
    private val nsClientPlugin
        get() = activePlugin.activeNsClient
    private val version: NsClient.Version get() = nsClientPlugin?.version ?: NsClient.Version.NONE

    private val disposable = CompositeDisposable()

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var _binding: NsClientFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        NsClientFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.autoscroll.isChecked = sp.getBoolean(R.string.key_ns_client_autoscroll, true)
        binding.autoscroll.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(R.string.key_ns_client_autoscroll, isChecked)
            updateGui()
        }

        binding.paused.isChecked = sp.getBoolean(R.string.key_ns_client_paused, false)
        binding.paused.setOnCheckedChangeListener { _, isChecked ->
            uel.log(if (isChecked) UserEntry.Action.NS_PAUSED else UserEntry.Action.NS_RESUME, UserEntry.Sources.NSClient)
            nsClientPlugin?.pause(isChecked)
            updateGui()
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        if (config.isUnfinishedMode())
            menu.add(Menu.FIRST, ID_MENU_TEST, 0, "Test").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RESTART, 0, rh.gs(R.string.restart)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.deliver_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.setGroupDividerEnabled(true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                nsClientPlugin?.clearLog()
                true
            }

            ID_MENU_RESTART   -> {
                rxBus.send(EventNSClientRestart())
                true
            }

            ID_MENU_SEND_NOW  -> {
                nsClientPlugin?.resend("GUI")
                true
            }

            ID_MENU_FULL_SYNC -> {
                context?.let { context ->
                    OKDialog.showConfirmation(
                        context, rh.gs(R.string.ns_client), rh.gs(R.string.full_sync_comment),
                        Runnable { nsClientPlugin?.resetToFullSync() }
                    )
                }
                true
            }

            ID_MENU_TEST      -> {
                nsClientPlugin?.let { plugin -> if (plugin is NSClientV3Plugin) handler.post { plugin.test() } }
                true
            }

            else              -> false
        }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventNSClientUpdateGUI::class.java)
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
        binding.paused.isChecked = sp.getBoolean(R.string.key_ns_client_paused, false)
        binding.log.text = nsClientPlugin?.textLog()
        if (sp.getBoolean(R.string.key_ns_client_autoscroll, true)) binding.logScrollview.fullScroll(ScrollView.FOCUS_DOWN)
        binding.url.text = nsClientPlugin?.address
        binding.status.text = nsClientPlugin?.status
        val size = dataSyncSelector.queueSize()
        binding.queue.text = if (size >= 0) size.toString() else rh.gs(R.string.value_unavailable_short)
    }
}