package info.nightscout.plugins.sync.nsShared

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpanned
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.HtmlHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.PluginBase
import info.nightscout.interfaces.plugin.PluginFragment
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.databinding.NsClientFragmentBinding
import info.nightscout.plugins.sync.databinding.NsClientLogItemBinding
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiData
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import info.nightscout.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientNewLog
import info.nightscout.rx.events.EventNSClientRestart
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NSClientFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var persistenceLayer: PersistenceLayer

    companion object {

        const val ID_MENU_CLEAR_LOG = 507
        const val ID_MENU_RESTART = 508
        const val ID_MENU_SEND_NOW = 509
        const val ID_MENU_FULL_SYNC = 510
    }

    override var plugin: PluginBase? = null
    private val nsClientPlugin
        get() = activePlugin.activeNsClient

    private val disposable = CompositeDisposable()

    private var _binding: NsClientFragmentBinding? = null
    private lateinit var logAdapter: RecyclerViewAdapter
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // https://stackoverflow.com/questions/31759171/recyclerview-and-java-lang-indexoutofboundsexception-inconsistency-detected-in
    class FixedLinearLayoutManager(context: Context?, @RecyclerView.Orientation orientation: Int = RecyclerView.VERTICAL, reverseLayout: Boolean = false) :
        LinearLayoutManager(context, orientation, reverseLayout) {

        override fun supportsPredictiveItemAnimations(): Boolean = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        NsClientFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.paused.isChecked = sp.getBoolean(R.string.key_ns_paused, false)
        binding.paused.setOnCheckedChangeListener { _, isChecked ->
            uel.log(if (isChecked) UserEntry.Action.NS_PAUSED else UserEntry.Action.NS_RESUME, UserEntry.Sources.NSClient)
            nsClientPlugin?.pause(isChecked)
        }

        logAdapter = RecyclerViewAdapter(nsClientPlugin?.listLog ?: emptyList())
        binding.recyclerview.layoutManager = FixedLinearLayoutManager(context)
        binding.recyclerview.adapter = logAdapter
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RESTART, 0, rh.gs(R.string.restart)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.deliver_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                nsClientPlugin?.listLog?.let {
                    synchronized(it) {
                        it.clear()
                        updateLog()
                    }
                }
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
                var result = ""
                context?.let { context ->
                    OKDialog.showConfirmation(
                        context, rh.gs(R.string.ns_client), rh.gs(R.string.full_sync_comment),
                        Runnable {
                            OKDialog.showConfirmation(requireContext(), rh.gs(R.string.ns_client), rh.gs(info.nightscout.core.ui.R.string.cleanup_db_confirm_sync), Runnable {
                                disposable += Completable.fromAction { result = persistenceLayer.cleanupDatabase(93, deleteTrackedChanges = true) }
                                    .subscribeOn(aapsSchedulers.io)
                                    .observeOn(aapsSchedulers.main)
                                    .subscribeBy(
                                        onError = { aapsLogger.error("Error cleaning up databases", it) },
                                        onComplete = {
                                            if (result.isNotEmpty())
                                                OKDialog.show(
                                                    requireContext(),
                                                    rh.gs(info.nightscout.core.ui.R.string.result),
                                                    HtmlHelper.fromHtml("<b>" + rh.gs(info.nightscout.core.ui.R.string.cleared_entries) + "</b><br>" + result)
                                                        .toSpanned()
                                                )
                                            aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                                            handler.post {
                                                nsClientPlugin?.resetToFullSync()
                                                nsClientPlugin?.resend("FULL_SYNC")
                                            }
                                        }
                                    )
                                uel.log(UserEntry.Action.CLEANUP_DATABASES, UserEntry.Sources.NSClient)
                            }, Runnable {
                                handler.post {
                                    nsClientPlugin?.resetToFullSync()
                                    nsClientPlugin?.resend("FULL_SYNC")
                                }
                            })
                        }
                    )
                }
                true
            }

            else              -> false
        }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiData::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                {
                    _binding?.recyclerview?.swapAdapter(RecyclerViewAdapter(nsClientPlugin?.listLog ?: arrayListOf()), true)
                }, fabricPrivacy::logException
            )
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiQueue::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateQueue() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNSClientUpdateGuiStatus::class.java)
            .debounce(3L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateStatus() }, fabricPrivacy::logException)
        updateStatus()
        updateQueue()
        updateLog()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun updateQueue() {
        val size = nsClientPlugin?.dataSyncSelector?.queueSize() ?: 0L
        _binding?.queue?.text = if (size >= 0) size.toString() else rh.gs(info.nightscout.core.ui.R.string.value_unavailable_short)
    }

    private fun updateStatus() {
        if (_binding == null) return
        binding.paused.isChecked = sp.getBoolean(R.string.key_ns_paused, false)
        binding.url.text = nsClientPlugin?.address
        binding.status.text = nsClientPlugin?.status
    }

    private fun updateLog() {
        _binding?.recyclerview?.swapAdapter(RecyclerViewAdapter(nsClientPlugin?.listLog ?: arrayListOf()), true)
    }

    private inner class RecyclerViewAdapter(private var logList: List<EventNSClientNewLog>) : RecyclerView.Adapter<RecyclerViewAdapter.NsClientLogViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NsClientLogViewHolder =
            NsClientLogViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.ns_client_log_item, viewGroup, false))

        override fun onBindViewHolder(holder: NsClientLogViewHolder, position: Int) {
            holder.binding.logText.text = HtmlHelper.fromHtml(logList[position].toPreparedHtml().toString())
        }

        override fun getItemCount() = logList.size

        inner class NsClientLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = NsClientLogItemBinding.bind(view)
        }
    }
}