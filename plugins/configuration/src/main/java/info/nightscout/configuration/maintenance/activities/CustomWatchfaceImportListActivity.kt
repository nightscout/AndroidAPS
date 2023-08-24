package info.nightscout.configuration.maintenance.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.core.ui.activities.TranslatedDaggerAppCompatActivity
import info.nightscout.interfaces.maintenance.PrefFileListProvider
import info.nightscout.configuration.databinding.CustomWatchfaceImportListActivityBinding
import info.nightscout.configuration.R
import info.nightscout.configuration.databinding.CustomWatchfaceImportListItemBinding
import info.nightscout.interfaces.versionChecker.VersionCheckerUtils
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventMobileDataToWear
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.weardata.CUSTOM_VERSION
import info.nightscout.rx.weardata.CwfData
import info.nightscout.rx.weardata.CwfDrawableFileMap
import info.nightscout.rx.weardata.CwfMetadataKey.*
import info.nightscout.rx.weardata.CwfMetadataMap
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class CustomWatchfaceImportListActivity: TranslatedDaggerAppCompatActivity()  {

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
        binding.recyclerview.adapter = RecyclerViewAdapter(prefFileListProvider.listCustomWatchfaceFiles())
    }

    inner class RecyclerViewAdapter internal constructor(private var customWatchfaceFileList: List<CwfData>) : RecyclerView.Adapter<RecyclerViewAdapter.CwfFileViewHolder>() {

        inner class CwfFileViewHolder(val customWatchfaceImportListItemBinding: CustomWatchfaceImportListItemBinding) : RecyclerView.ViewHolder(customWatchfaceImportListItemBinding.root) {

            init {
                with(customWatchfaceImportListItemBinding) {
                    root.isClickable = true
                    customWatchfaceImportListItemBinding.root.setOnClickListener {
                        val customWatchfaceFile = filelistName.tag as CwfData
                        val customWF = EventData.ActionSetCustomWatchface(customWatchfaceFile)
                        val i = Intent()
                        setResult(FragmentActivity.RESULT_OK, i)
                        rxBus.send(EventMobileDataToWear(customWF))
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
            val metadata = customWatchfaceFile.metadata
            val drawable = customWatchfaceFile.drawableDatas[CwfDrawableFileMap
                .CUSTOM_WATCHFACE]?.toDrawable(resources)
            with(holder.customWatchfaceImportListItemBinding) {
                filelistName.text = rh.gs(info.nightscout.shared.R.string.metadata_wear_import_filename, metadata[CWF_FILENAME])
                filelistName.tag = customWatchfaceFile
                customWatchface.setImageDrawable(drawable)
                customName.text = rh.gs(CWF_NAME.label, metadata[CWF_NAME])
                metadata[CWF_AUTHOR_VERSION]?.let { author_version ->
                    customName.text = rh.gs(CWF_AUTHOR_VERSION.label, metadata[CWF_NAME], author_version)
                }

                author.text = rh.gs(CWF_AUTHOR.label, metadata[CWF_AUTHOR] ?:"")
                createdAt.text = rh.gs(CWF_CREATED_AT.label, metadata[CWF_CREATED_AT] ?:"")
                cwfVersion.text = rh.gs(CWF_VERSION.label, metadata[CWF_VERSION] ?:"")
                val colorAttr = if (checkCustomVersion(metadata)) info.nightscout.core.ui.R.attr.metadataTextOkColor else info.nightscout.core.ui.R.attr.metadataTextWarningColor
                cwfVersion.setTextColor(rh.gac(cwfVersion.context, colorAttr))
                prefWarning.visibility = metadata.keys.any { it.isPref }.toVisibility()
                cwfPrefNumber.text = "${metadata.count { it.key.isPref }}"
                cwfPrefNumber.visibility=prefWarning.visibility
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
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