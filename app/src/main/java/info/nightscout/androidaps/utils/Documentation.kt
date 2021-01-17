package info.nightscout.androidaps.utils

import android.net.Uri
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.resources.ResourceHelper

object Documentation {
    fun getHelpUri(resourceHelper: ResourceHelper, urlPath: Int): Uri {
        val base = resourceHelper.gs(R.string.doc_base_url);
        val path = resourceHelper.gs(urlPath);
        val version = "latest/"
        val url = base + version + path;
        return Uri.parse(url);
    }
}
