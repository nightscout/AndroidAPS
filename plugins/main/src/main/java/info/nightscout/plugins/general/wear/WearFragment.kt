package info.nightscout.plugins.general.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.WearFragmentBinding
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventMobileDataToWear
import info.nightscout.rx.events.EventMobileToWear
import info.nightscout.rx.events.EventWearUpdateGui
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.weardata.CustomWatchfaceDrawableDataKey
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class WearFragment : DaggerFragment() {

    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var sp:SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    private var _binding: WearFragmentBinding? = null
    private val disposable = CompositeDisposable()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = WearFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resend.setOnClickListener { rxBus.send(EventData.ActionResendData("WearFragment")) }
        binding.openSettings.setOnClickListener { rxBus.send(EventMobileToWear(EventData.OpenSettings(dateUtil.now()))) }

        binding.loadCustom.setOnClickListener {
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.importCustomWatchface(this)
            }
        }
        binding.defaultCustom.setOnClickListener {
            sp.remove(info.nightscout.shared.R.string.key_custom_watchface)
            wearPlugin.savedCustomWatchface = null
            rxBus.send(EventMobileToWear(EventData.ActionrequestSetDefaultWatchface(dateUtil.now())))
            updateGui()
        }
        binding.sendCustom.setOnClickListener {
            wearPlugin.savedCustomWatchface?.let { cwf -> rxBus.send(EventMobileDataToWear(cwf)) }
        }
        binding.exportCustom.setOnClickListener {
            wearPlugin.savedCustomWatchface?.let { importExportPrefs.exportCustomWatchface(it) }
                ?: apply { rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(true)))}
        }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventMobileDataToWear::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           loadCustom(it.payload)
                           wearPlugin.customWatchfaceSerialized = ""
                           wearPlugin.savedCustomWatchface = null
                           updateGui()
                           ToastUtils.okToast(context,rh.gs(R.string.wear_new_custom_watchface_received))
                       }, fabricPrivacy::logException)
        if (wearPlugin.savedCustomWatchface == null)
            rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(false)))
        //EventMobileDataToWear
        updateGui()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGui() {
        _binding ?: return
        sp.getString(info.nightscout.shared.R.string.key_custom_watchface, "").let {
            if (it != wearPlugin.customWatchfaceSerialized && it != "") {
                aapsLogger.debug("XXXXX Serialisation: ${it.length}")
                try {
                    wearPlugin.savedCustomWatchface = (EventData.deserialize(it) as EventData.ActionSetCustomWatchface)
                    wearPlugin.customWatchfaceSerialized = it
                }
                catch(e: Exception) {
                    wearPlugin.customWatchfaceSerialized = ""
                    wearPlugin.savedCustomWatchface = null
                }
            }
            sp.remove(info.nightscout.shared.R.string.key_custom_watchface)
        }
        wearPlugin.savedCustomWatchface?.let {
            binding.customName.text = rh.gs(R.string.wear_custom_watchface, it.name)
            binding.sendCustom.visibility = View.VISIBLE
            binding.coverChart.setImageDrawable(it.drawableDataMap[CustomWatchfaceDrawableDataKey.CUSTOM_WATCHFACE]?.toDrawable(resources))
        } ?:apply {
            binding.customName.text = rh.gs(R.string.wear_custom_watchface, rh.gs(info.nightscout.shared.R.string.wear_default_watchface))
            binding.sendCustom.visibility = View.INVISIBLE
            binding.coverChart.setImageDrawable(null)
        }
        binding.connectedDevice.text = wearPlugin.connectedDevice
    }

    private fun loadCustom(cwf: EventData.ActionSetCustomWatchface) {
        aapsLogger.debug("XXXXX EventWearCwfExported received")
        wearPlugin.savedCustomWatchface = cwf
    }
}