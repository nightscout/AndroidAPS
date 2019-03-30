package info.nightscout.androidaps.plugins.general.actions.defs;

/**
 * Created by andy on 9/20/18.
 */

public class CustomAction {

    private int name;
    private String iconName;
    private CustomActionType customActionType;
    private boolean enabled = true;


    public CustomAction(int nameResourceId, CustomActionType actionType) {
        this(nameResourceId, actionType, true);
    }


    public CustomAction(int nameResourceId, CustomActionType actionType, boolean enabled) {
        this.name = nameResourceId;
        this.customActionType = actionType;
        this.enabled = enabled;
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


    public boolean isEnabled() {
        return enabled;
    }


    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
