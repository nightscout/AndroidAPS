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
import app.aaps.plugins.configuration.maintenance.ImportExportPrefsImpl
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.formats.EncryptedPrefsFormat
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class CloudPrefImportListActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var cloudStorageManager: CloudStorageManager
    @Inject lateinit var encryptedPrefsFormat: EncryptedPrefsFormat

    private lateinit var binding: MaintenanceImportListActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MaintenanceImportListActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = rh.gs(R.string.import_from_cloud)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        
        // Use cloud file list
        val cloudFiles = ImportExportPrefsImpl.cloudPrefsFiles.toMutableList()
        val adapter = RecyclerViewAdapter(cloudFiles)
        binding.recyclerview.adapter = adapter

        // Update file count display
        updateFileCountDisplay(cloudFiles.size)
        
        // Show or hide "Load More" and update button text
        updateLoadMoreButton(cloudFiles.size)
        binding.loadMore.setOnClickListener {
            // Click load more: fetch PAGE_SIZE more
            binding.loadMore.isEnabled = false
            binding.loadMore.text = rh.gs(R.string.loading)
            lifecycleScope.launch {
                val nextToken = ImportExportPrefsImpl.cloudNextPageToken
                if (nextToken == null) {
                    binding.loadMore.visibility = View.GONE
                    return@launch
                }
                
                // Get active cloud provider
                val provider = cloudStorageManager.getActiveProvider()
                if (provider == null) {
                    binding.loadMore.visibility = View.GONE
                    return@launch
                }
                
                val currentLoadedCount = cloudFiles.size  // Already loaded count
                val page = provider.listSettingsFiles(CloudConstants.DEFAULT_PAGE_SIZE, nextToken)
                ImportExportPrefsImpl.cloudNextPageToken = page.nextPageToken
                // Download and parse each entry then append
                val appended = mutableListOf<PrefsFile>()
                val namePattern = Regex("^\\d{4}-\\d{2}-\\d{2}_\\d{6}.*\\.json$", RegexOption.IGNORE_CASE)
                val filesToProcess = page.files.filter { namePattern.containsMatchIn(it.name) }
                var processedCount = 0
                
                for (f in filesToProcess) {
                    try {
                        // Update progress on button - add current loaded count
                        processedCount++
                        val currentItemNumber = currentLoadedCount + processedCount
                        val totalItemsInThisBatch = currentLoadedCount + filesToProcess.size
                        runOnUiThread {
                            binding.loadMore.text = rh.gs(R.string.loading_progress, currentItemNumber, totalItemsInThisBatch)
                        }
                        
                        val bytes = provider.downloadFile(f.id)
                        if (bytes != null) {
                            val content = String(bytes, Charsets.UTF_8)
                            val metadata = encryptedPrefsFormat.loadMetadata(content)
                            appended.add(PrefsFile(f.name, content, metadata))
                        }
                    } catch (_: Exception) {
                        // Ignore single entry error
                    }
                }
                val start = cloudFiles.size
                cloudFiles.addAll(appended)
                adapter.notifyItemRangeInserted(start, appended.size)
                binding.loadMore.isEnabled = true
                updateLoadMoreButton(cloudFiles.size)
                updateFileCountDisplay(cloudFiles.size)
            }
        }
    }

    private fun updateLoadMoreButton(currentCount: Int) {
        if (ImportExportPrefsImpl.cloudNextPageToken == null) {
            binding.loadMore.visibility = View.GONE
        } else {
            binding.loadMore.visibility = View.VISIBLE
            // Calculate remaining files to load
            val totalCount = ImportExportPrefsImpl.cloudTotalFilesCount
            val remainingCount = if (totalCount > 0) {
                minOf(CloudConstants.DEFAULT_PAGE_SIZE, totalCount - currentCount)
            } else {
                CloudConstants.DEFAULT_PAGE_SIZE
            }
            binding.loadMore.text = rh.gs(R.string.load_more_with_count, remainingCount, currentCount)
        }
    }
    
    private fun updateFileCountDisplay(currentCount: Int) {
        val totalCount = ImportExportPrefsImpl.cloudTotalFilesCount
        if (totalCount > 0) {
            binding.fileCount.visibility = View.VISIBLE
            if (currentCount >= totalCount || ImportExportPrefsImpl.cloudNextPageToken == null) {
                // All files loaded
                binding.fileCount.text = rh.gs(R.string.cloud_import_file_count_all, currentCount)
            } else {
                // Partial files loaded
                binding.fileCount.text = rh.gs(R.string.cloud_import_file_count, currentCount, totalCount)
            }
        } else {
            binding.fileCount.visibility = View.GONE
        }
    }

    inner class RecyclerViewAdapter internal constructor(private var prefFileList: MutableList<PrefsFile>) : RecyclerView.Adapter<RecyclerViewAdapter.PrefFileViewHolder>() {

        inner class PrefFileViewHolder(val maintenanceImportListItemBinding: MaintenanceImportListItemBinding) : RecyclerView.ViewHolder(maintenanceImportListItemBinding.root) {

            init {
                with(maintenanceImportListItemBinding) {
                    root.isClickable = true
                    maintenanceImportListItemBinding.root.setOnClickListener {
                        val i = Intent()
                        // Set selected file
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

        override fun getItemCount(): Int = prefFileList.size

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
