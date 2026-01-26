package app.aaps.ui.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.ui.UiInteraction
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class ConcentrationFragment : DaggerFragment() {

    @Inject lateinit var uiInteraction: UiInteraction

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        uiInteraction.runConcentrationDialog(childFragmentManager)
        return View(context).apply {
            visibility = View.GONE
        }
    }
}