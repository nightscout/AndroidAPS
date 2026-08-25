package app.aaps.pump.eopatch.core.code

class PatchAeCode private constructor(val aeValue: Int) {

    companion object {

        fun create(aeValue: Int, @Suppress("UNUSED_PARAMETER") timeOffset: Int) = PatchAeCode(aeValue)
    }
}
