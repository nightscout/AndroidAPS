package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.plugins.general.automation.AutomationFragment;
import info.nightscout.utils.JsonHelper;

public class TriggerConnector extends Trigger {
    public enum Type {
        AND,
        OR;

        public boolean apply(boolean a, boolean b) {
            switch (this) {
                case AND:
                    return a && b;
                case OR:
                    return a || b;
            }
            return false;
        }

        public @StringRes int getStringRes() {
            switch (this) {
                case OR:
                    return R.string.or;

                default:
                case AND:
                    return R.string.and;
            }
        }
    }

    protected List<Trigger> list = new ArrayList<>();
    private Type connectorType;

    public TriggerConnector() {
        connectorType = Type.AND;
    }

    public TriggerConnector(Type connectorType) {
        this.connectorType = connectorType;
    }

    public void changeConnectorType(Type type) { this.connectorType = type; }

    public Type getConnectorType() { return connectorType; }

    public synchronized void add(Trigger t) {
        list.add(t);
        t.connector = this;
    }

    public synchronized boolean remove(Trigger t) {
        return list.remove(t);
    }

    public int size() {
        return list.size();
    }

    public Trigger get(int i) {
        return list.get(i);
    }

    @Override
    public synchronized boolean shouldRun() {
        boolean result = false;

        for (Trigger t : list) {
            result = connectorType.apply(result, t.shouldRun());
        }
        return result;
    }

    @Override
    synchronized String toJSON() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", TriggerConnector.class.getName());
            JSONObject data = new JSONObject();
            data.put("connectorType", connectorType.toString());
            JSONArray array = new JSONArray();
            for (Trigger t : list) {
                array.put(t.toJSON());
            }
            data.put("triggerList", array);
            o.put("data", data);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return o.toString();
    }

    @Override
    Trigger fromJSON(String data) {
        try {
            JSONObject d = new JSONObject(data);
            connectorType = Type.valueOf(JsonHelper.safeGetString(d, "connectorType"));
            JSONArray array = d.getJSONArray("triggerList");
            for (int i = 0; i < array.length(); i++) {
                Trigger newItem = instantiate(new JSONObject(array.getString(i)));
                add(newItem);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public int friendlyName() {
        return connectorType.getStringRes();
    }

    @Override
    public String friendlyDescription() {
        int counter = 0;
        StringBuilder result = new StringBuilder();
        for (Trigger t : list) {
            if (counter++ > 0) result.append(friendlyName());
            result.append(t.friendlyDescription());
        }
        return result.toString();
    }

    @Override
    public ViewHolder createViewHolder(LayoutInflater inflater) {
        ViewHolder v = new ViewHolder(inflater);
        viewHolder = v;
        return v;
    }


    class ViewHolder extends Trigger.ViewHolder {

        @BindView(R.id.triggerListLayout)
        LinearLayout triggerListLayout;

        @BindView(R.id.title)
        TextView titleView;

        AutomationFragment.TriggerListAdapter adapter;

        public ViewHolder(LayoutInflater inflater) {
            super(inflater, R.layout.automation_trigger_connector);
            titleView.setText(friendlyName());
            adapter = new AutomationFragment.TriggerListAdapter(inflater, triggerListLayout, list);
        }

        @OnClick(R.id.buttonRemove)
        public void onButtonClickRemove(View view) {
            if (connector != null) {
                connector.remove(TriggerConnector.this);
                ((TriggerConnector.ViewHolder)connector.getViewHolder()).adapter.rebuild();
            } else {
                // no parent
                list.clear();
                adapter.rebuild();
            }
        }

        @OnClick(R.id.buttonAddAnd)
        public void onButtonClickAnd(View view) {
            addTrigger(new TriggerTime(), Type.AND);
        }

        @OnClick(R.id.buttonAddOr)
        public void onButtonClickOr(View view) {
            addTrigger(new TriggerTime(), Type.OR);
        }

        private void addTrigger(Trigger trigger, Type connection) {
            if (getConnectorType().equals(connection)) {
                add(trigger);
            } else {
                TriggerConnector t = new TriggerConnector(connection);
                t.add(trigger);
                add(t);
            }
            adapter.rebuild();
        }

        @Override
        public void destroy() {
            adapter.destroy();
            super.destroy();
        }
    }
}
