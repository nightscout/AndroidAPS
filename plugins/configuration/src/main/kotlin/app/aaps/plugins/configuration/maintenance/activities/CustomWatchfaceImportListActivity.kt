package app.aaps.plugins.configuration.maintenance.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.PrefFileListProvider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileDataToWear
import app.aaps.core.interfaces.rx.weardata.CUSTOM_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfData
import app.aaps.core.interfaces.rx.weardata.CwfFile
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_AUTHOR
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_AUTHOR_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_CREATED_AT
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_FILENAME
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_NAME
import app.aaps.core.interfaces.rx.weardata.CwfMetadataKey.CWF_VERSION
import app.aaps.core.interfaces.rx.weardata.CwfMetadataMap
import app.aaps.core.interfaces.rx.weardata.CwfResDataMap
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.ResFileMap
import app.aaps.core.interfaces.rx.weardata.ZipWatchfaceFormat
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.databinding.CustomWatchfaceImportListActivityBinding
import app.aaps.plugins.configuration.databinding.CustomWatchfaceImportListItemBinding
import javax.inject.Inject

class CustomWatchfaceImportListActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var prefFileListProvider: PrefFileListProvider
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils

    private lateinit var binding: CustomWatchfaceImportListActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CustomWatchfaceImportListActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = rh.gs(R.string.wear_import_custom_watchface_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(prefFileListProvider.listCustomWatchfaceFiles().sortedBy { it.cwfData.metadata[CWF_NAME] })
    }

    inner class RecyclerViewAdapter internal constructor(private var customWatchfaceFileList: List<CwfFile>) : RecyclerView.Adapter<RecyclerViewAdapter.CwfFileViewHolder>() {

        inner class CwfFileViewHolder(val customWatchfaceImportListItemBinding: CustomWatchfaceImportListItemBinding) : RecyclerView.ViewHolder(customWatchfaceImportListItemBinding.root) {

            init {
                with(customWatchfaceImportListItemBinding) {
                    root.isClickable = true
                    customWatchfaceImportListItemBinding.root.setOnClickListener {
                        val customWatchfaceFile = filelistName.tag as CwfFile
                        sp.putString(app.aaps.core.utils.R.string.key_wear_cwf_watchface_name, customWatchfaceFile.cwfData.metadata[CWF_NAME] ?:"")
                        sp.putString(app.aaps.core.utils.R.string.key_wear_cwf_author_version, customWatchfaceFile.cwfData.metadata[CWF_AUTHOR_VERSION] ?:"")
                        sp.putString(app.aaps.core.utils.R.string.key_wear_cwf_filename, customWatchfaceFile.cwfData.metadata[CWF_FILENAME] ?:"")

                        val i = Intent()
                        setResult(FragmentActivity.RESULT_OK, i)
                        rxBus.send(EventMobileDataToWear(customWatchfaceFile.zipByteArray))
                        finish()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CwfFileViewHolder {
            val binding = CustomWatchfaceImportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return CwfFileViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return customWatchfaceFileList.size
        }

        override fun onBindViewHolder(holder: CwfFileViewHolder, position: Int) {
            val customWatchfaceFile = customWatchfaceFileList[position]
            val metadata = customWatchfaceFile.cwfData.metadata
            val drawable = customWatchfaceFile.cwfData.resDatas[ResFileMap.CUSTOM_WATCHFACE.fileName]?.toDrawable(resources)
            with(holder.customWatchfaceImportListItemBinding) {
                val fileName = metadata[CWF_FILENAME]?.let { "$it${ZipWatchfaceFormat.CWF_EXTENTION}" } ?: ""
                filelistName.text = rh.gs(app.aaps.core.interfaces.R.string.metadata_wear_import_filename, fileName)
                filelistName.tag = customWatchfaceFile
                customWatchface.setImageDrawable(drawable)
                customName.text = rh.gs(CWF_NAME.label, metadata[CWF_NAME])
                metadata[CWF_AUTHOR_VERSION]?.let { authorVersion ->
                    customName.text = rh.gs(CWF_AUTHOR_VERSION.label, metadata[CWF_NAME], authorVersion)
                }

                author.text = rh.gs(CWF_AUTHOR.label, metadata[CWF_AUTHOR] ?: "")
                createdAt.text = rh.gs(CWF_CREATED_AT.label, metadata[CWF_CREATED_AT] ?: "")
                cwfVersion.text = rh.gs(CWF_VERSION.label, metadata[CWF_VERSION] ?: "")
                val colorAttr = if (checkCustomVersion(metadata)) app.aaps.core.ui.R.attr.metadataTextOkColor else app.aaps.core.ui.R.attr.metadataTextWarningColor
                cwfVersion.setTextColor(rh.gac(cwfVersion.context, colorAttr))
                val prefExisting = metadata.keys.any { it.isPref }
                val prefSetting = sp.getBoolean(app.aaps.core.utils.R.string.key_wear_custom_watchface_autorization, false)
                val prefColor = if (prefSetting) app.aaps.core.ui.R.attr.metadataTextWarningColor else app.aaps.core.ui.R.attr.importListFileNameColor
                prefWarning.visibility = (prefExisting && prefSetting).toVisibility()
                prefInfo.visibility = (prefExisting && !prefSetting).toVisibility()
                cwfPrefNumber.text = "${metadata.count { it.key.isPref }}"
                cwfPrefNumber.visibility = prefExisting.toVisibility()
                cwfPrefNumber.setTextColor(rh.gac(cwfPrefNumber.context, prefColor))
            }
        }
    }

    private fun checkCustomVersion(metadata: CwfMetadataMap): Boolean {
        metadata[CWF_VERSION]?.let { version ->
            val currentAppVer = versionCheckerUtils.versionDigits(CUSTOM_VERSION)
            val metadataVer = versionCheckerUtils.versionDigits(version)
            //Only check that Loaded Watchface version is lower or equal to Wear CustomWatchface version
            return ((currentAppVer.size >= 2) && (metadataVer.size >= 2) && (currentAppVer[0] >= metadataVer[0]))
        }
        return false
    }
}