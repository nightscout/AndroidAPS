package app.aaps.plugins.sync.openhumans.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.LiveData
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.openhumans.OpenHumansState
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import com.google.android.material.button.MaterialButton
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class OHFragment : DaggerFragment() {

    @Inject
    internal lateinit var stateLiveData: LiveData<OpenHumansState?>

    @Inject
    internal lateinit var plugin: OpenHumansUploaderPlugin

    private lateinit var setup: MaterialButton
    private lateinit var logout: MaterialButton
    private lateinit var uploadNow: MaterialButton
    private lateinit var info: TextView
    private lateinit var memberId: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val contextWrapper = ContextThemeWrapper(requireActivity(), R.style.OpenHumans)
        val wrappedInflater = inflater.cloneInContext(contextWrapper)
        val view = wrappedInflater.inflate(R.layout.fragment_open_humans_new, container, false)
        setup = view.findViewById(R.id.setup)
        setup.setOnClickListener { startActivity(Intent(context, OHLoginActivity::class.java)) }
        logout = view.findViewById(R.id.logout)
        logout.setOnClickListener { plugin.logout() }
        info = view.findViewById(R.id.info)
        memberId = view.findViewById(R.id.member_id)
        uploadNow = view.findViewById(R.id.upload_now)
        uploadNow.setOnClickListener { plugin.uploadNow() }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stateLiveData.observe(viewLifecycleOwner) { state ->
            if (state == null) {
                setup.visibility = View.VISIBLE
                logout.visibility = View.GONE
                memberId.visibility = View.GONE
                uploadNow.visibility = View.GONE
                info.setText(R.string.not_setup_info)
            } else {
                setup.visibility = View.GONE
                logout.visibility = View.VISIBLE
                memberId.visibility = View.VISIBLE
                uploadNow.visibility = View.VISIBLE
                memberId.text = getString(R.string.project_member_id, state.projectMemberId)
                info.setText(R.string.setup_completed_info)
            }
        }
    }

}