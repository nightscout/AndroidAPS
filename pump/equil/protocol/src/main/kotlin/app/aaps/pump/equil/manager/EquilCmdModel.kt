package app.aaps.pump.equil.manager

class EquilCmdModel {

    var code: String? = null
    var iv: String? = null
    var tag: String? = null
    var ciphertext: String? = null

    override fun toString(): String = "EquilCmdModel{code='$code', iv='$iv', tag='$tag', ciphertext='$ciphertext'}"
}
