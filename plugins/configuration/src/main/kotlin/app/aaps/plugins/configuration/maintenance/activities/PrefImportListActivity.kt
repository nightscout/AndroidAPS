package app.aaps.plugins.configuration.maintenance.activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.PrefsFile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.databinding.MaintenanceImportListActivityBinding
import app.aaps.plugins.configuration.databinding.MaintenanceImportListItemBinding
import app.aaps.plugins.configuration.maintenance.PrefsMetadataKeyImpl
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import javax.inject.Inject

class PrefImportListActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var importExportPrefs: ImportExportPrefs

    private lateinit var binding: MaintenanceImportListActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MaintenanceImportListActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = rh.gs(R.string.preferences_import_list_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(fileListProvider.listPreferenceFiles())
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerview.adapter = null
    }

    inner class RecyclerViewAdapter internal constructor(private var prefFileList: List<PrefsFile>) : RecyclerView.Adapter<RecyclerViewAdapter.PrefFileViewHolder>() {

        inner class PrefFileViewHolder(val maintenanceImportListItemBinding: MaintenanceImportListItemBinding) : RecyclerView.ViewHolder(maintenanceImportListItemBinding.root) {

            init {
                with(maintenanceImportListItemBinding) {
                    root.isClickable = true
                    maintenanceImportListItemBinding.root.setOnClickListener {
                        val i = Intent()
                        // Do not pass full file through intent. It crash on large file
                        // val prefFile = prefFileList[filelistName.tag as Int]
                        // i.putExtra(PrefsFileContract.OUTPUT_PARAM, prefFile)
                        importExportPrefs.selectedImportFile = prefFileList[filelistName.tag as Int]
                        setResult(RESULT_OK, i)
                        finish()
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrefFileViewHolder {
            val binding = MaintenanceImportListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PrefFileViewHolder(binding)
        }

        override fun getItemCount(): Int {
            return prefFileList.size
        }

        override fun onBindViewHolder(holder: PrefFileViewHolder, position: Int) {
            val prefFile = prefFileList[position]
            with(holder.maintenanceImportListItemBinding) {
                filelistName.text = prefFile.name
                filelistName.tag = position

                metalineName.visibility = View.VISIBLE
                metaDateTimeIcon.visibility = View.VISIBLE
                metaAppVersion.visibility = View.VISIBLE

                prefFile.metadata[PrefsMetadataKeyImpl.AAPS_FLAVOUR]?.let {
                    metaVariantFormat.text = it.value
                    val colorAttr = if (it.status == PrefsStatusImpl.OK) app.aaps.core.ui.R.attr.metadataTextOkColor else app.aaps.core.ui.R.attr.metadataTextWarningColor
                    metaVariantFormat.setTextColor(rh.gac(metaVariantFormat.context, colorAttr))
                }

                prefFile.metadata[PrefsMetadataKeyImpl.CREATED_AT]?.let {
                    metaDateTime.text = fileListProvider.formatExportedAgo(it.value)
                }

                prefFile.metadata[PrefsMetadataKeyImpl.AAPS_VERSION]?.let {
                    metaAppVersion.text = it.value
                    val colorAttr = if (it.status == PrefsStatusImpl.OK) app.aaps.core.ui.R.attr.metadataTextOkColor else app.aaps.core.ui.R.attr.metadataTextWarningColor
                    metaAppVersion.setTextColor(rh.gac(metaVariantFormat.context, colorAttr))
                }

                prefFile.metadata[PrefsMetadataKeyImpl.DEVICE_NAME]?.let {
                    metaDeviceName.text = it.value
                }

            }
        }
    }
}