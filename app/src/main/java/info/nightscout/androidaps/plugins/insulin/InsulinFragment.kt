package info.nightscout.androidaps.plugins.insulin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.insulin_fragment.*
import javax.inject.Inject

class InsulinFragment : DaggerFragment() {
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var resourceHelper: ResourceHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.insulin_fragment, container, false)
    }

    override fun onResume() {
        super.onResume()
        insulin_name?.setText(activePlugin.getActiveInsulin().getFriendlyName())
        insulin_comment?.setText(activePlugin.getActiveInsulin().getComment())
        insulin_dia?.text = resourceHelper.gs(R.string.dia) + ":  " + activePlugin.getActiveInsulin().getDia() + "h"
        insulin_graph?.show(activePlugin.getActiveInsulin())
    }
}