package info.nightscout.androidaps.interfaces

class UpdateReturn(var success: Boolean, var newRecord: Boolean) {

    override fun toString(): String {
        return "UpdateReturn [" +
            "newRecord=" + newRecord +
            ", success=" + success +
            ']'
    }
}