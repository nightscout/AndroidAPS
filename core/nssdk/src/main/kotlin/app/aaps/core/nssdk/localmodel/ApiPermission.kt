package app.aaps.core.nssdk.localmodel

data class ApiPermission(
    val create: Boolean,
    val read: Boolean,
    val update: Boolean,
    val delete: Boolean
) {
    val full: Boolean
        get() = this.create && this.read && this.update && this.delete
}
