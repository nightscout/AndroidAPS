package info.nightscout.androidaps.plugins.general.automation.triggers;

import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.view.LayoutInflater;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import butterknife.ButterKnife;
import butterknife.Unbinder;
import info.nightscout.androidaps.R;

public abstract class Trigger {

    public enum Comparator {
        IS_LOWER,
        IS_EQUAL_OR_LOWER,
        IS_EQUAL,
        IS_EQUAL_OR_GREATER,
        IS_GREATER,
        IS_NOT_AVAILABLE;

        public @StringRes int getStringRes() {
            switch (this) {
                case IS_LOWER:
                    return R.string.islower;
                case IS_EQUAL_OR_LOWER:
                    return R.string.isequalorlower;
                case IS_EQUAL:
                    return R.string.isequal;
                case IS_EQUAL_OR_GREATER:
                    return R.string.isequalorgreater;
                case IS_GREATER:
                    return R.string.isgreater;
                case IS_NOT_AVAILABLE:
                    return R.string.isnotavailable;
                default:
                    return R.string.unknown;
            }
        }

        public <T extends Comparable> boolean check(T obj1, T obj2) {
            if (obj1 == null || obj2 == null)
                return this.equals(Comparator.IS_NOT_AVAILABLE);

            int comparison = obj1.compareTo(obj2);
            switch (this) {
                case IS_LOWER:
                    return comparison < 0;
                case IS_EQUAL_OR_LOWER:
                    return comparison <= 0;
                case IS_EQUAL:
                    return comparison == 0;
                case IS_EQUAL_OR_GREATER:
                    return comparison >= 0;
                case IS_GREATER:
                    return comparison > 0;
                default:
                    return false;
            }
        }
    }

    protected ViewHolder viewHolder = null;

    protected TriggerConnector connector = null;

    Trigger() {
    }

    public Trigger getConnector() {
        return connector;
    }

    public abstract boolean shouldRun();

    abstract String toJSON();

    abstract Trigger fromJSON(String data);

    public abstract int friendlyName();

    public abstract String friendlyDescription();

    void notifyAboutRun(long time) {
    }

    static Trigger instantiate(JSONObject object) {
        try {
            String type = object.getString("type");
            String data = object.getString("data");
            Class clazz = Class.forName(type);
            return ((Trigger) clazz.newInstance()).fromJSON(data);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public abstract ViewHolder createViewHolder(LayoutInflater inflater);

    public ViewHolder getViewHolder() {
        return viewHolder;
    }

    public void destroyViewHolder() {
        if (viewHolder != null) {
            viewHolder.destroy();
        }
    }

    public static abstract class ViewHolder {
        final View view;
        final Unbinder unbinder;

        public ViewHolder(LayoutInflater inflater, @LayoutRes int layout) {
            view = inflater.inflate(layout, null);
            unbinder = ButterKnife.bind(this, view);
        }

        public void destroy() {
            unbinder.unbind();
        }

        public View getView() { return view; }
    }
}
