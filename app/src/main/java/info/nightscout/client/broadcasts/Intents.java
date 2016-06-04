package info.nightscout.client.broadcasts;

public interface Intents {
    // NSClient -> App
    String ACTION_NEW_TREATMENT = "info.nightscout.client.NEW_TREATMENT";
    String ACTION_CHANGED_TREATMENT = "info.nightscout.client.CHANGED_TREATMENT";
    String ACTION_REMOVED_TREATMENT = "info.nightscout.client.REMOVED_TREATMENT";
    String ACTION_NEW_PROFILE = "info.nightscout.client.NEW_PROFILE";
    String ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV";
    String ACTION_NEW_STATUS = "info.nightscout.client.NEW_STATUS";


    // App -> NSClient
    String ACTION_DATABASE = "info.nightscout.client.DBACCESS";
}
