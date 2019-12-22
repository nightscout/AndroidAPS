package info.nightscout.androidaps.plugins.general.automation.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.automation.actions.Action
import info.nightscout.androidaps.plugins.general.automation.events.EventAutomationUpdateGui

class ActionListAdapter(private val fragmentManager: FragmentManager, private val actionList: MutableList<Action>) : RecyclerView.Adapter<ActionListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.automation_action_item, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val action = actionList[position]
        holder.bind(action, fragmentManager, this, position, actionList)
    }

    override fun getItemCount(): Int {
        return actionList.size
    }

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {

        fun bind(action: Action, fragmentManager: FragmentManager, recyclerView: RecyclerView.Adapter<ViewHolder>, position: Int, actionList: MutableList<Action>) {
            view.findViewById<LinearLayout>(R.id.automation_layoutText).setOnClickListener {
                if (action.hasDialog()) {
                    val args = Bundle()
                    args.putInt("actionPosition", position)
                    args.putString("action", action.toJSON())
                    val dialog = EditActionDialog()
                    dialog.arguments = args
                    dialog.show(fragmentManager, "EditActionDialog")
                }
            }
            view.findViewById<ImageView>(R.id.automation_iconTrash).setOnClickListener {
                actionList.remove(action)
                recyclerView.notifyDataSetChanged()
                RxBus.send(EventAutomationUpdateGui())
            }
            if (action.icon().isPresent) view.findViewById<ImageView>(R.id.automation_action_image).setImageResource(action.icon().get())
            view.findViewById<TextView>(R.id.automation_viewActionTitle).text = action.shortDescription()
        }
    }
}
