package info.nightscout.androidaps.plugins.general.actions.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 9/20/18.
 */

public class CustomAction {

    private int name;
    private String iconName;
    private CustomActionType customActionType;
    private int iconResourceId;
    private boolean enabled = true;


    public CustomAction(int nameResourceId, CustomActionType actionType) {
        this(nameResourceId, actionType, R.drawable.icon_actions_profileswitch, true);
    }


    public CustomAction(int nameResourceId, CustomActionType actionType, int iconResourceId) {
        this(nameResourceId, actionType, iconResourceId, true);
    }

    public CustomAction(int nameResourceId, CustomActionType actionType, boolean enabled) {
        this(nameResourceId, actionType, R.drawable.icon_actions_profileswitch, enabled);
    }

    public CustomAction(int nameResourceId, CustomActionType actionType, int iconResourceId, boolean enabled) {
        this.name = nameResourceId;
        this.customActionType = actionType;
        this.iconResourceId = iconResourceId;
        this.enabled = enabled;
    }


    public int getName() {
        return name;
    }


    public CustomActionType getCustomActionType() {
        return customActionType;
    }


    public int getIconResourceId() {
        return iconResourceId;
    }


    public boolean isEnabled() {
        return enabled;
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
