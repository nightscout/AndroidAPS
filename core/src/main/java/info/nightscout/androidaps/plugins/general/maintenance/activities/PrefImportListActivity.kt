package info.nightscout.androidaps.plugins.general.maintenance.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.core.databinding.MaintenanceImportListActivityBinding
import info.nightscout.androidaps.core.databinding.MaintenanceImportListItemBinding
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFile
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFormatsHandler
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsMetadataKey
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsStatus
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

class PrefImportListActivity : DaggerAppCompatActivity() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var prefFileListProvider: PrefFileListProvider

    private lateinit var binding: MaintenanceImportListActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        binding = MaintenanceImportListActivityBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        title = resourceHelper.gs(R.string.preferences_import_list_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(prefFileListProvider.listPreferenceFiles(loadMetadata = true))
    }

    inner class RecyclerViewAdapter internal constructor(private var prefFileList: List<PrefsFile>) : RecyclerView.Adapter<RecyclerViewAdapter.PrefFileViewHolder>() {

        inner class PrefFileViewHolder(val maintenanceImportListItemBinding: MaintenanceImportListItemBinding) : RecyclerView.ViewHolder(maintenanceImportListItemBinding.root) {

            init {
                with(maintenanceImportListItemBinding) {
                    root.isClickable = true
                    maintenanceImportListItemBinding.root.setOnClickListener {
                        val prefFile = filelistName.tag as PrefsFile
                        val i = Intent()

                        i.putExtra(PrefsFileContract.OUTPUT_PARAM, prefFile)
                        setResult(FragmentActivity.RESULT_OK, i)
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
                filelistName.text = prefFile.file.name
                filelistName.tag = prefFile

                filelistDir.text = resourceHelper.gs(R.string.in_directory, prefFile.file.parentFile.absolutePath)

                val visible = (prefFile.handler != PrefsFormatsHandler.CLASSIC).toVisibility()
                metalineName.visibility = visible
                metaDateTimeIcon.visibility = visible
                metaAppVersion.visibility = visible

                if (prefFile.handler == PrefsFormatsHandler.CLASSIC) {
                    metaVariantFormat.text = resourceHelper.gs(R.string.metadata_format_old)
                    metaVariantFormat.setTextColor(resourceHelper.gc(R.color.metadataTextWarning))
                    metaDateTime.text = " "
                } else {

                    prefFile.metadata[PrefsMetadataKey.AAPS_FLAVOUR]?.let {
                        metaVariantFormat.text = it.value
                        val color = if (it.status == PrefsStatus.OK) R.color.metadataOk else R.color.metadataTextWarning
                        metaVariantFormat.setTextColor(resourceHelper.gc(color))
                    }

                    prefFile.metadata[PrefsMetadataKey.CREATED_AT]?.let {
                        metaDateTime.text = prefFileListProvider.formatExportedAgo(it.value)
                    }

                    prefFile.metadata[PrefsMetadataKey.AAPS_VERSION]?.let {
                        metaAppVersion.text = it.value
                        val color = if (it.status == PrefsStatus.OK) R.color.metadataOk else R.color.metadataTextWarning
                        metaAppVersion.setTextColor(resourceHelper.gc(color))
                    }

                    prefFile.metadata[PrefsMetadataKey.DEVICE_NAME]?.let {
                        metaDeviceName.text = it.value
                    }

                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return false
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}