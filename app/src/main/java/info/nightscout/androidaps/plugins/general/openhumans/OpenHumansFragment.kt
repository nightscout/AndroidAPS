package info.nightscout.androidaps.plugins.general.openhumans

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R

class OpenHumansFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_open_humans, container, false)
        val button = view.findViewById<Button>(R.id.login)
        button.setOnClickListener {
            startActivity(Intent(context, OpenHumansLoginActivity::class.java))
        }
        return view
    }

}