package info.nightscout.pump.medtrum.util

import info.nightscout.pump.medtrum.encryption.Crypt

class MedtrumSnUtil {

    companion object {

        const val INVALID = -1
        const val MD_0201 = 80
        const val MD_5201 = 81
        const val MD_0202 = 82
        const val MD_5202 = 83
        const val MD_8201 = 88
        const val MD_8301 = 98
    }

    fun getDeviceTypeFromSerial(serial: Long): Int {
        if (serial in 106000000..106999999) {
            return INVALID
        }

        return when (Crypt().simpleDecrypt(serial)) {
            in 126000000..126999999 -> MD_0201
            in 127000000..127999999 -> MD_5201
            in 128000000..128999999 -> MD_8201
            in 130000000..130999999 -> MD_0202
            in 131000000..131999999 -> MD_5202
            in 148000000..148999999 -> MD_8301
            else                    -> INVALID
        }
    }
}
