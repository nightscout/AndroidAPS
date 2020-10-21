package info.nightscout.androidaps.plugins.general.maintenance.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.general.maintenance.PrefFileListProvider
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFile
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsFormatsHandler
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsMetadataKey
import info.nightscout.androidaps.plugins.general.maintenance.formats.PrefsStatus
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.maintenance_importlist_activity.*
import javax.inject.Inject

class PrefImportListActivity : DaggerAppCompatActivity() {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var prefFileListProvider: PrefFileListProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.maintenance_importlist_activity)

        title = resourceHelper.gs(R.string.preferences_import_list_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        importlist_recyclerview.layoutManager = LinearLayoutManager(this)
        importlist_recyclerview.adapter = RecyclerViewAdapter(prefFileListProvider.listPreferenceFiles(loadMetadata = true))
    }

    inner class RecyclerViewAdapter internal constructor(private var prefFileList: List<PrefsFile>) : RecyclerView.Adapter<RecyclerViewAdapter.PrefFileViewHolder>() {

        inner class PrefFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var fileName: TextView = itemView.findViewById(R.id.filelist_name)
            var fileDir: TextView = itemView.findViewById(R.id.filelist_dir)
            var metaDateTime: TextView = itemView.findViewById(R.id.meta_date_time)
            var metaDeviceName: TextView = itemView.findViewById(R.id.meta_device_name)
            var metaAppVersion: TextView = itemView.findViewById(R.id.meta_app_version)
            var metaVariantFormat: TextView = itemView.findViewById(R.id.meta_variant_format)

            var metalineName: View = itemView.findViewById(R.id.metaline_name)
            var metaDateTimeIcon: View = itemView.findViewById(R.id.meta_date_time_icon)

            init {
                itemView.isClickable = true
                itemView.setOnClickListener {
                    val prefFile = fileName.tag as PrefsFile
                    val i = Intent()

                    i.putExtra(PrefsFileContract.OUTPUT_PARAM, prefFile)
                    setResult(FragmentActivity.RESULT_OK, i)
                    finish()
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrefFileViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.maintenance_importlist_item, parent, false)
            return PrefFileViewHolder(v)
        }

        override fun getItemCount(): Int {
            return prefFileList.size
        }

        override fun onBindViewHolder(holder: PrefFileViewHolder, position: Int) {
            val prefFile = prefFileList[position]
            holder.fileName.text = prefFile.file.name
            holder.fileName.tag = prefFile

            holder.fileDir.text = resourceHelper.gs(R.string.in_directory, prefFile.file.parentFile.absolutePath)

            val visible = if (prefFile.handler == PrefsFormatsHandler.CLASSIC) View.GONE else View.VISIBLE
            holder.metalineName.visibility = visible
            holder.metaDateTimeIcon.visibility = visible
            holder.metaAppVersion.visibility = visible

            if (prefFile.handler == PrefsFormatsHandler.CLASSIC) {
                holder.metaVariantFormat.text = resourceHelper.gs(R.string.metadata_format_old)
                holder.metaVariantFormat.setTextColor(resourceHelper.gc(R.color.metadataTextWarning))
                holder.metaDateTime.text = " "
            } else {

                prefFile.metadata[PrefsMetadataKey.AAPS_FLAVOUR]?.let {
                    holder.metaVariantFormat.text = it.value
                    val color = if (it.status == PrefsStatus.OK) R.color.metadataOk else R.color.metadataTextWarning
                    holder.metaVariantFormat.setTextColor(resourceHelper.gc(color))
                }

                prefFile.metadata[PrefsMetadataKey.CREATED_AT]?.let {
                    holder.metaDateTime.text = prefFileListProvider.formatExportedAgo(it.value)
                }

                prefFile.metadata[PrefsMetadataKey.AAPS_VERSION]?.let {
                    holder.metaAppVersion.text = it.value
                    val color = if (it.status == PrefsStatus.OK) R.color.metadataOk else R.color.metadataTextWarning
                    holder.metaAppVersion.setTextColor(resourceHelper.gc(color))
                }

                prefFile.metadata[PrefsMetadataKey.DEVICE_NAME]?.let {
                    holder.metaDeviceName.text = it.value
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