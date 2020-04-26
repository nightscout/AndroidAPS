package info.nightscout.androidaps.interfaces;

public class PluginDescription {
    PluginType mainType = PluginType.GENERAL;
    String fragmentClass = null;
    public boolean alwaysVisible = false;
    public boolean neverVisible = false;
    public boolean alwaysEnabled = false;
    boolean showInList = true;
    int pluginName = -1;
    int shortName = -1;
    int description = -1;
    int preferencesId = -1;
    public boolean enableByDefault = false;
    public boolean visibleByDefault = false;

    public PluginDescription mainType(PluginType mainType) {
        this.mainType = mainType;
        return this;
    }

    public PluginDescription fragmentClass(String fragmentClass) {
        this.fragmentClass = fragmentClass;
        return this;
    }

    public PluginDescription alwaysEnabled(boolean alwaysEnabled) {
        this.alwaysEnabled = alwaysEnabled;
        return this;
    }

     public PluginDescription alwaysVisible(boolean alwaysVisible) {
        this.alwaysVisible = alwaysVisible;
        return this;
    }

    public PluginDescription neverVisible(boolean neverVisible) {
        this.neverVisible = neverVisible;
        return this;
    }

    public PluginDescription showInList(boolean showInList) {
        this.showInList = showInList;
        return this;
    }

    public PluginDescription pluginName(int pluginName) {
        this.pluginName = pluginName;
        return this;
    }

    public PluginDescription shortName(int shortName) {
        this.shortName = shortName;
        return this;
    }

    public PluginDescription preferencesId(int preferencesId) {
        this.preferencesId = preferencesId;
        return this;
    }

    public PluginDescription enableByDefault(boolean enableByDefault) {
        this.enableByDefault = enableByDefault;
        return this;
    }

    public PluginDescription visibleByDefault(boolean visibleByDefault) {
        this.visibleByDefault = visibleByDefault;
        return this;
    }

    public PluginDescription description(int description) {
        this.description = description;
        return this;
    }

    public String getFragmentClass() {
        return fragmentClass;
    }

    public PluginType getType() {
        return mainType;
    }
}
