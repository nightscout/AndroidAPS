package info.nightscout.androidaps.plugins.general.automation.triggers;


import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentManager;

import com.google.common.base.Optional;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.general.automation.dialogs.TriggerListAdapter;
import info.nightscout.androidaps.utils.JsonHelper;

public class TriggerConnector extends Trigger {
    private static Logger log = LoggerFactory.getLogger(L.AUTOMATION);

    public enum Type {
        AND,
        OR,
        XOR;

        public boolean apply(boolean a, boolean b) {
            switch (this) {
                case AND:
                    return a && b;
                case OR:
                    return a || b;
                case XOR:
                    return a ^ b;
            }
            return false;
        }

        public @StringRes
        int getStringRes() {
            switch (this) {
                case OR:
                    return R.string.or;
                case XOR:
                    return R.string.xor;

                default:
                case AND:
                    return R.string.and;
            }
        }

        public static List<String> labels() {
            List<String> list = new ArrayList<>();
            for (Type t : values()) {
                list.add(MainApp.gs(t.getStringRes()));
            }
            return list;
        }
    }

    public static void fillIconSet(TriggerConnector connector, HashSet<Integer> set) {
        for (Trigger t : connector.list) {
            if (t instanceof TriggerConnector) {
                fillIconSet((TriggerConnector) t, set);
            } else {
                Optional<Integer> icon = t.icon();
                if (icon.isPresent()) {
                    set.add(icon.get());
                }
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

    public void changeConnectorType(Type type) {
        this.connectorType = type;
    }

    public Type getConnectorType() {
        return connectorType;
    }

    public synchronized void add(Trigger t) {
        list.add(t);
        t.connector = this;
    }

    public synchronized void add(int pos, Trigger t) {
        list.add(pos, t);
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

    public int pos(Trigger trigger) {
        for (int i = 0; i < list.size(); ++i) {
            if (list.get(i) == trigger) return i;
        }
        return -1;
    }

    @Override
    public synchronized boolean shouldRun() {
        boolean result = true;

        // check first trigger
        if (list.size() > 0)
            result = list.get(0).shouldRun();

        // check all others
        for (int i = 1; i < list.size(); ++i) {
            result = connectorType.apply(result, list.get(i).shouldRun());
        }
        if (result)
            if (L.isEnabled(L.AUTOMATION))
                log.debug("Ready for execution: " + friendlyDescription().replace("\n", " "));

        return result;
    }

    @Override
    public synchronized String toJSON() {
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
            list.clear();
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
            if (counter++ > 0) result.append("\n").append(MainApp.gs(friendlyName())).append("\n");
            result.append(t.friendlyDescription());
        }
        return result.toString();
    }

    @Override
    public Optional<Integer> icon() {
        return Optional.absent();
    }

    @Override
    public void executed(long time) {
        for (int i = 0; i < list.size(); ++i) {
            list.get(i).executed(time);
        }
    }

    @Override
    public Trigger duplicate() {
        return null;
    }

    private TriggerListAdapter adapter;

    public void rebuildView(FragmentManager fragmentManager) {
        if (adapter != null)
            adapter.rebuild(fragmentManager);
    }

    @Override
    public void generateDialog(LinearLayout root, FragmentManager fragmentManager) {
        final int padding = MainApp.dpToPx(5);

        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundResource(R.drawable.border_automation_unit);

        LinearLayout triggerListLayout = new LinearLayout(root.getContext());
        triggerListLayout.setOrientation(LinearLayout.VERTICAL);
        triggerListLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(triggerListLayout);

        adapter = new TriggerListAdapter(fragmentManager, root.getContext(), triggerListLayout, this);
    }

    public TriggerConnector simplify() {
        // simplify children
        for (int i = 0; i < size(); ++i) {
            if (get(i) instanceof TriggerConnector) {
                TriggerConnector t = (TriggerConnector) get(i);
                t.simplify();
            }
        }

        // drop connector with only 1 element
        if (size() == 1 && get(0) instanceof TriggerConnector) {
            TriggerConnector c = (TriggerConnector) get(0);
            remove(c);
            changeConnectorType(c.getConnectorType());
            for (Trigger t : c.list) {
                add(t);
            }
            c.list.clear();
            return simplify();
        }

        // merge connectors
        if (connector != null && (connector.getConnectorType().equals(connectorType) || size() == 1)) {
            final int pos = connector.pos(this);
            connector.remove(this);
            // move triggers of child connector into parent connector
            for (int i = size() - 1; i >= 0; --i) {
                connector.add(pos, get(i));
            }
            list.clear();
            return connector.simplify();
        }

        return this;
    }
}
