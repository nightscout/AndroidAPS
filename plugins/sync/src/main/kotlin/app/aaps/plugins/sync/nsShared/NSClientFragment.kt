package app.aaps.plugins.sync.nsShared

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
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.rx.events.EventNSClientRestart
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.NsClientFragmentBinding
import app.aaps.plugins.sync.databinding.NsClientLogItemBinding
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiData
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiQueue
import app.aaps.plugins.sync.nsShared.events.EventNSClientUpdateGuiStatus
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class NSClientFragment : DaggerFragment(), MenuProvider, PluginFragment {

    @Inject lateinit var preferences: Preferences
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

        binding.paused.isChecked = preferences.get(NsclientBooleanKey.NsPaused)
        binding.paused.setOnCheckedChangeListener { _, isChecked ->
            uel.log(action = if (isChecked) Action.NS_PAUSED else Action.NS_RESUME, source = Sources.NSClient)
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
                        val size = it.size
                        binding.recyclerview.adapter?.notifyItemRangeRemoved(0, size)
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
                handler.post { nsClientPlugin?.resend("GUI") }
                true
            }

            ID_MENU_FULL_SYNC -> {
                var result = ""
                context?.let { context ->
                    OKDialog.showConfirmation(
                        context, rh.gs(R.string.ns_client), rh.gs(R.string.full_sync_comment),
                        {
                            OKDialog.showConfirmation(requireContext(), rh.gs(R.string.ns_client), rh.gs(app.aaps.core.ui.R.string.cleanup_db_confirm_sync), {
                                disposable += Completable.fromAction { result = persistenceLayer.cleanupDatabase(93, deleteTrackedChanges = true) }
                                    .subscribeOn(aapsSchedulers.io)
                                    .observeOn(aapsSchedulers.main)
                                    .subscribeBy(
                                        onError = { aapsLogger.error("Error cleaning up databases", it) },
                                        onComplete = {
                                            if (result.isNotEmpty())
                                                OKDialog.show(
                                                    requireContext(),
                                                    rh.gs(app.aaps.core.ui.R.string.result),
                                                    HtmlHelper.fromHtml("<b>" + rh.gs(app.aaps.core.ui.R.string.cleared_entries) + "</b><br>" + result).toSpanned()
                                                )
                                            aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                                            handler.post {
                                                nsClientPlugin?.resetToFullSync()
                                                nsClientPlugin?.resend("FULL_SYNC")
                                            }
                                        }
                                    )
                                uel.log(action = Action.CLEANUP_DATABASES, source = Sources.NSClient)
                            }, {
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

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

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    private fun updateQueue() {
        val size = nsClientPlugin?.dataSyncSelector?.queueSize() ?: 0L
        _binding?.queue?.text = if (size >= 0) size.toString() else rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
    }

    private fun updateStatus() {
        if (_binding == null) return
        binding.paused.isChecked = preferences.get(NsclientBooleanKey.NsPaused)
        binding.url.text = nsClientPlugin?.address
        binding.status.text = nsClientPlugin?.status
    }

    private fun updateLog() {
        _binding?.recyclerview?.recycledViewPool?.clear()
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