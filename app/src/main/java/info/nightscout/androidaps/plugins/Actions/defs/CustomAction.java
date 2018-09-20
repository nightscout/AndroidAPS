package info.nightscout.androidaps.plugins.Actions.defs;

import info.nightscout.androidaps.R;

/**
 * Created by andy on 9/20/18.
 */

public class CustomAction {

    private int name;
    private String iconName;
    private CustomActionType customActionType;


    public CustomAction(int nameResourceId, CustomActionType actionType)
    {
        this.name = nameResourceId;
        this.customActionType = actionType;
    }


    public int getName() {

        return name;
    }

    public String getIconName() {

        return iconName;
    }

    public void setIconName(String iconName) {

        this.iconName = iconName;
    }

    public CustomActionType getCustomActionType() {

        return customActionType;
    }

    public void setCustomActionType(CustomActionType customActionType) {

        this.customActionType = customActionType;
    }
}
