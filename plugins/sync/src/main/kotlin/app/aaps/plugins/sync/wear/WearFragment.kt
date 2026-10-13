package app.aaps.plugins.sync.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventWearUpdateGui
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.databinding.WearFragmentBinding
import app.aaps.plugins.sync.wear.activities.CwfInfosActivity
import app.aaps.shared.impl.weardata.ResFileMap
import app.aaps.shared.impl.weardata.toDrawable
import dagger.android.support.DaggerFragment
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
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    private var _binding: WearFragmentBinding? = null
    private val disposable = CompositeDisposable()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
        binding.moreCustom.setOnClickListener {
            val intent = Intent().apply { action = Intent.ACTION_VIEW; data = Uri.parse(rh.gs(R.string.wear_link_to_more_cwf_doc)) }
            startActivity(intent)
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
                               it.customWatchfaceData?.let { cwfData -> loadCustom(cwfData) }
                               updateGui()
                           }
                       }, fabricPrivacy::logException)
        if (wearPlugin.savedCustomWatchface == null)
            rxBus.send(EventMobileToWear(EventData.ActionrequestCustomWatchface(false)))
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
            binding.coverChart.setImageDrawable(it.resData[ResFileMap.CUSTOM_WATCHFACE.fileName]?.toDrawable(resources))
            binding.infosCustom.visibility = View.VISIBLE
        } ?: apply {
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