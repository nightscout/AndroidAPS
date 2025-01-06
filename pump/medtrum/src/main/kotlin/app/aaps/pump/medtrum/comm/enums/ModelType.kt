package app.aaps.pump.medtrum.comm.enums

enum class ModelType(val value: Int) {
    INVALID(-1),
    MD0201(80),
    MD5201(81),
    MD0202(82),
    MD5202(83),
    MD8201(88),
    MD8301(98);

    companion object {

        fun fromValue(value: Int): ModelType {
            return ModelType.entries.find { it.value == value } ?: INVALID
        }
    }
}
