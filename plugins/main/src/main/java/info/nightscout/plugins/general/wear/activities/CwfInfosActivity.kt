package info.nightscout.plugins.general.wear.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.CwfInfosActivityBinding
import info.nightscout.plugins.databinding.CwfInfosActivityPrefItemBinding
import info.nightscout.plugins.databinding.CwfInfosActivityViewItemBinding
import info.nightscout.plugins.general.wear.WearPlugin
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventWearUpdateGui
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.rx.weardata.CUSTOM_VERSION
import info.nightscout.rx.weardata.CwfDrawableFileMap
import info.nightscout.rx.weardata.CwfMetadataKey
import info.nightscout.rx.weardata.CwfMetadataMap
import info.nightscout.rx.weardata.JsonKeyValues
import info.nightscout.rx.weardata.JsonKeys
import info.nightscout.rx.weardata.ViewKeys
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject

class CwfInfosActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils

    private val disposable = CompositeDisposable()
    private lateinit var binding: CwfInfosActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CwfInfosActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        updateGui()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           it.customWatchfaceData?.let { cwf ->
                               if (!it.exportFile) {
                                   wearPlugin.savedCustomWatchface = cwf
                                   updateGui()
                               }
                           }
                       }, fabricPrivacy::logException)

        updateGui()
    }

    private fun updateGui() {
        wearPlugin.savedCustomWatchface?.let {
            val cwfAuthorization = sp.getBoolean(info.nightscout.core.utils.R.string.key_wear_custom_watchface_autorization, false)
            val metadata = it.metadata
            val drawable = it.drawableDatas[CwfDrawableFileMap.CUSTOM_WATCHFACE]?.toDrawable(resources)
            binding.customWatchface.setImageDrawable(drawable)
            title = rh.gs(CwfMetadataKey.CWF_NAME.label, metadata[CwfMetadataKey.CWF_NAME])
            metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]?.let { authorVersion ->
                title = "${metadata[CwfMetadataKey.CWF_NAME]} ($authorVersion)"
            }
            binding.filelistName.text = rh.gs(CwfMetadataKey.CWF_FILENAME.label, metadata[CwfMetadataKey.CWF_FILENAME] ?: "")
            binding.author.text = rh.gs(CwfMetadataKey.CWF_AUTHOR.label, metadata[CwfMetadataKey.CWF_AUTHOR] ?: "")
            binding.createdAt.text = rh.gs(CwfMetadataKey.CWF_CREATED_AT.label, metadata[CwfMetadataKey.CWF_CREATED_AT] ?: "")
            binding.cwfVersion.text = rh.gs(CwfMetadataKey.CWF_VERSION.label, metadata[CwfMetadataKey.CWF_VERSION] ?: "")
            val colorAttr = if (checkCustomVersion(metadata)) info.nightscout.core.ui.R.attr.metadataTextOkColor else info.nightscout.core.ui.R.attr.metadataTextWarningColor
            binding.cwfVersion.setTextColor(rh.gac(binding.cwfVersion.context, colorAttr))
            binding.cwfComment.text = rh.gs(CwfMetadataKey.CWF_COMMENT.label, metadata[CwfMetadataKey.CWF_COMMENT] ?: "")
            if (metadata.count { it.key.isPref } > 0) {
                binding.prefLayout.visibility = View.VISIBLE
                binding.prefTitle.text = rh.gs(if (cwfAuthorization) R.string.cwf_infos_pref_locked else R.string.cwf_infos_pref_required)
                binding.prefRecyclerview.layoutManager = LinearLayoutManager(this)
                binding.prefRecyclerview.adapter = PrefRecyclerViewAdapter(
                    metadata.filter { it.key.isPref && (it.value.lowercase() == "true" || it.value.lowercase() == "false") }.toList()
                )
            } else
                binding.prefLayout.visibility = View.GONE
            binding.viewRecyclerview.layoutManager = LinearLayoutManager(this)
            binding.viewRecyclerview.adapter = ViewRecyclerViewAdapter(listVisibleView(it.json))
        }

    }

    inner class PrefRecyclerViewAdapter internal constructor(private var prefList: List<Pair<CwfMetadataKey, String>>) : RecyclerView.Adapter<PrefRecyclerViewAdapter.CwfPrefViewHolder>() {

        inner class CwfPrefViewHolder(val cwfInfosActivityPrefItemBinding: CwfInfosActivityPrefItemBinding) : RecyclerView.ViewHolder(cwfInfosActivityPrefItemBinding.root) {
            init {
                with(cwfInfosActivityPrefItemBinding) {
                    root.isClickable = false
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CwfPrefViewHolder {
            val binding = CwfInfosActivityPrefItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CwfPrefViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return prefList.size
        }

        override fun onBindViewHolder(holder: CwfPrefViewHolder, position: Int) {
            val pref = prefList[position]
            val key = pref.first
            val value = pref.second.lowercase().toBooleanStrictOrNull()                                                     // should never be null here, just safety to avoid exception
            with(holder.cwfInfosActivityPrefItemBinding) {
                prefLabel.text = rh.gs(key.label)
                value?.let { prefValue.setImageResource(if (it) R.drawable.settings_on else R.drawable.settings_off) }// should never be null here, just safety to avoid exception
            }
        }
    }

    inner class ViewRecyclerViewAdapter internal constructor(private var viewList: List<Pair<ViewKeys, Boolean>>) : RecyclerView.Adapter<ViewRecyclerViewAdapter.CwfViewHolder>() {

        inner class CwfViewHolder(val cwfInfosActivityViewItemBinding: CwfInfosActivityViewItemBinding) : RecyclerView.ViewHolder(cwfInfosActivityViewItemBinding.root) {
            init {
                with(cwfInfosActivityViewItemBinding) {
                    root.isClickable = false
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CwfViewHolder {
            val binding = CwfInfosActivityViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CwfViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return viewList.size
        }

        override fun onBindViewHolder(holder: CwfViewHolder, position: Int) {
            val cwfView = viewList[position]
            val key = cwfView.first.key
            val value = cwfView.first.comment
            //val visible = cwfView.second        // will be used if all keys included into RecyclerView
            with(holder.cwfInfosActivityViewItemBinding) {
                viewKey.text = "\"$key\":"
                viewComment.text = rh.gs(value)
            }
        }
    }

    private fun checkCustomVersion(metadata: CwfMetadataMap): Boolean {
        metadata[CwfMetadataKey.CWF_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(CUSTOM_VERSION)
            val metadataVer = versionCheckerUtils.versionDigits(version)
            //Only check that Loaded Watchface version is lower or equal to Wear CustomWatchface version
            return ((currentAppVer.size >= 2) && (metadataVer.size >= 2) && (currentAppVer[0] >= metadataVer[0]))
        }
        return false
    }

    private fun listVisibleView(jsonString: String, allViews: Boolean = false): List<Pair<ViewKeys, Boolean>> {
        val json = JSONObject(jsonString)

        val visibleKeyPairs = mutableListOf<Pair<ViewKeys, Boolean>>()

        for (viewKey in ViewKeys.values()) {
            try {
                val jsonValue = json.optJSONObject(viewKey.key)
                if (jsonValue != null) {
                    val visibility = jsonValue.optString(JsonKeys.VISIBILITY.key) == JsonKeyValues.VISIBLE.key
                    if (visibility || allViews)
                        visibleKeyPairs.add(Pair(viewKey, visibility))
                }
            } catch (e: Exception) {
                aapsLogger.debug(LTag.WEAR, "Wrong key in json file: ${viewKey.key}")
            }
        }
        return visibleKeyPairs
    }

}