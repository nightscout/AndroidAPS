package info.nightscout.androidaps.plugins.general.nsclient

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.NsClientFragmentBinding
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class NSClientFragment : DaggerFragment() {

    @Inject lateinit var nsClientPlugin: NSClientPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private var _binding: NsClientFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        NsClientFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

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
        binding.clearLog.setOnClickListener { nsClientPlugin.clearLog() }
        binding.clearLog.paintFlags = binding.clearLog.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.restart.setOnClickListener { rxBus.send(EventNSClientRestart()) }
        binding.restart.paintFlags = binding.restart.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.deliverNow.setOnClickListener { nsClientPlugin.resend("GUI") }
        binding.deliverNow.paintFlags = binding.deliverNow.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.fullSync.setOnClickListener {
            context?.let { context ->
                OKDialog.showConfirmation(context, resourceHelper.gs(R.string.nsclientinternal), resourceHelper.gs(R.string.full_sync), Runnable {
                    dataSyncSelector.resetToNextFullSync()
                })
            }
        }
        binding.fullSync.paintFlags = binding.fullSync.paintFlags or Paint.UNDERLINE_TEXT_FLAG
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventNSClientUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        )
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
    }
}