package info.nightscout.androidaps.plugins.general.nsclient

import android.os.Bundle
import android.view.*
import android.widget.ScrollView
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.NsClientFragmentBinding
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class NSClientFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var uel: UserEntryLogger

    companion object {

        const val ID_MENU_CLEAR_LOG = 507
        const val ID_MENU_RESTART = 508
        const val ID_MENU_SEND_NOW = 509
        const val ID_MENU_FULL_SYNC = 510
    }

    private val disposable = CompositeDisposable()

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

        binding.autoscroll.isChecked = nsClientPlugin.autoscroll
        binding.autoscroll.setOnCheckedChangeListener { _, isChecked ->
            sp.putBoolean(R.string.key_nsclientinternal_autoscroll, isChecked)
            nsClientPlugin.autoscroll = isChecked
            updateGui()
        }

        binding.paused.isChecked = nsClientPlugin.paused
        binding.paused.setOnCheckedChangeListener { _, isChecked ->
            uel.log(if (isChecked) Action.NS_PAUSED else Action.NS_RESUME, Sources.NSClient)
            nsClientPlugin.pause(isChecked)
            updateGui()
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clearlog)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RESTART, 0, rh.gs(R.string.restart)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.deliver_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.setGroupDividerEnabled(true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                nsClientPlugin.clearLog()
                true
            }

            ID_MENU_RESTART   -> {
                rxBus.send(EventNSClientRestart())
                true
            }

            ID_MENU_SEND_NOW  -> {
                nsClientPlugin.resend("GUI")
                true
            }

            ID_MENU_FULL_SYNC -> {
                context?.let { context ->
                    OKDialog.showConfirmation(
                        context, rh.gs(R.string.nsclientinternal), rh.gs(R.string.full_sync_comment),
                        Runnable { dataSyncSelector.resetToNextFullSync() }
                    )
                }
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
        nsClientPlugin.updateLog()
        binding.paused.isChecked = sp.getBoolean(R.string.key_nsclientinternal_paused, false)
        binding.log.text = nsClientPlugin.textLog
        if (nsClientPlugin.autoscroll) binding.logScrollview.fullScroll(ScrollView.FOCUS_DOWN)
        binding.url.text = nsClientPlugin.url()
        binding.status.text = nsClientPlugin.status
        val size = dataSyncSelector.queueSize()
        binding.queue.text = if (size >= 0) size.toString() else rh.gs(R.string.notavailable)
    }
}