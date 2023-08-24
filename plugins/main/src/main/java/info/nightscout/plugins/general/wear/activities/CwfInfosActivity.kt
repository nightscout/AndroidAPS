package info.nightscout.plugins.general.wear.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.plugins.R
import info.nightscout.plugins.databinding.CwfInfosActivityBinding
import info.nightscout.plugins.databinding.CwfInfosActivityPrefItemBinding
import info.nightscout.plugins.general.wear.WearPlugin
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.weardata.CUSTOM_VERSION
import info.nightscout.rx.weardata.CwfDrawableFileMap
import info.nightscout.rx.weardata.CwfMetadataKey
import info.nightscout.rx.weardata.CwfMetadataMap
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class CwfInfosActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils

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




        // Add menu items without overriding methods in the Activity
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        })
    }

    private fun updateGui() {
        wearPlugin.savedCustomWatchface?.let {
            val cwf_authorization = sp.getBoolean(info.nightscout.core.utils.R.string.key_wear_custom_watchface_autorization, false)
            val metadata = it.metadata
            val drawable = it.drawableDatas[CwfDrawableFileMap.CUSTOM_WATCHFACE]?.toDrawable(resources)
            binding.customWatchface.setImageDrawable(drawable)
            title = rh.gs(CwfMetadataKey.CWF_NAME.label, metadata[CwfMetadataKey.CWF_NAME])
            metadata[CwfMetadataKey.CWF_AUTHOR_VERSION]?.let { author_version ->
                title = "${metadata[CwfMetadataKey.CWF_NAME]} ($author_version)"
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
                binding.prefTitle.text = rh.gs(if (cwf_authorization) R.string.cwf_infos_pref_locked else R.string.cwf_infos_pref_requested)
                binding.prefRecyclerview.layoutManager = LinearLayoutManager(this)
                binding.prefRecyclerview.adapter = PrefRecyclerViewAdapter(
                    metadata.filter { it.key.isPref && (it.value.lowercase() == "true" || it.value.lowercase() == "false") }.toList()
                )
            } else
                binding.prefLayout.visibility = View.GONE
        }

    }

    inner class PrefRecyclerViewAdapter internal constructor(private var prefList: List<Pair<CwfMetadataKey, String>>) : RecyclerView.Adapter<PrefRecyclerViewAdapter.CwfFileViewHolder>() {

        inner class CwfFileViewHolder(val cwfInfosActivityPrefItemBinding: CwfInfosActivityPrefItemBinding) : RecyclerView.ViewHolder(cwfInfosActivityPrefItemBinding.root) {
            init {
                with(cwfInfosActivityPrefItemBinding) {
                    root.isClickable = false
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CwfFileViewHolder {
            val binding = CwfInfosActivityPrefItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CwfFileViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return prefList.size
        }

        override fun onBindViewHolder(holder: CwfFileViewHolder, position: Int) {
            val pref = prefList[position]
            val key = pref.first
            val value = pref.second.lowercase().toBooleanStrictOrNull()                                                     // should never be null here, just safety to avoid exception
            with(holder.cwfInfosActivityPrefItemBinding) {
                prefLabel.text = rh.gs(key.label)
                value?.let { prefValue.setImageResource(if (it) R.drawable.settings_on else R.drawable.settings_off) }// should never be null here, just safety to avoid exception
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

}