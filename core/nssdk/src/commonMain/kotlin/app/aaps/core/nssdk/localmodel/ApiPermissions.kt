package app.aaps.core.nssdk.localmodel

data class ApiPermissions(
    val deviceStatus: ApiPermission,
    val entries: ApiPermission,
    val food: ApiPermission,
    val profile: ApiPermission,
    val settings: ApiPermission,
    val treatments: ApiPermission
) {

    fun isFull() = deviceStatus.full && entries.full && food.full && profile.full && settings.full && treatments.full
    fun isRead() = deviceStatus.read && entries.read && food.read && profile.read && settings.read && treatments.read

    /**
     * What an AAPS client needs: read everything, and write to the settings collection, its channel to the master. A
     * client token made by the master has exactly this.
     */
    fun isClient() = isRead() && settings.create && settings.update
}
