package info.nightscout.androidaps.plugins.Actions.defs;

/**
 * Created by andy on 9/20/18.
 */

public class CustomAction {

    private String name;
    private String iconName;
    private CustomActionType customActionType;


    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
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
