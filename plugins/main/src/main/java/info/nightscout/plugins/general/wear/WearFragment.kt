package info.nightscout.plugins.general.wear

import android.content.Intent
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
import info.nightscout.plugins.general.wear.activities.CwfInfosActivity
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventMobileToWear
import info.nightscout.rx.events.EventWearUpdateGui
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.weardata.CwfData
import info.nightscout.rx.weardata.CwfDrawableFileMap
import info.nightscout.rx.weardata.CwfMetadataKey
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.extensions.toVisibility
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
            rxBus.send(EventMobileToWear(EventData.ActionrequestSetDefaultWatchface(dateUtil.now())))
            updateGui()
        }
        binding.exportCustom.setOnClickListener {
            rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(true)))
        }
        binding.infosCustom.setOnClickListener {
            startActivity(Intent(activity, CwfInfosActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.exportFile)
                               ToastUtils.okToast(activity, rh.gs(R.string.wear_new_custom_watchface_exported))
                           else {
                               it.customWatchfaceData?.let { loadCustom(it) }
                               updateGui()
                           }
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
        wearPlugin.savedCustomWatchface?.let {
            wearPlugin.checkCustomWatchfacePreferences()
            binding.customName.text = rh.gs(R.string.wear_custom_watchface, it.metadata[CwfMetadataKey.CWF_NAME])
            binding.coverChart.setImageDrawable(it.drawableDatas[CwfDrawableFileMap.CUSTOM_WATCHFACE]?.toDrawable(resources))
            binding.infosCustom.visibility = View.VISIBLE
        } ?:apply {
            binding.customName.text = rh.gs(R.string.wear_custom_watchface, "")
            binding.coverChart.setImageDrawable(null)
            binding.infosCustom.visibility = View.GONE
        }
        binding.connectedDevice.text = wearPlugin.connectedDevice
        binding.customWatchfaceLayout.visibility = (wearPlugin.connectedDevice != rh.gs(R.string.no_watch_connected)).toVisibility()
    }

    private fun loadCustom(cwf: CwfData) {
        wearPlugin.savedCustomWatchface = cwf
    }
}