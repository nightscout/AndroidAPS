package app.aaps.pump.omnipod.common.bledriver.comm.message

enum class MessageType(val value: Byte) {
    CLEAR(0),
    ENCRYPTED(1),
    SESSION_ESTABLISHMENT(2),
    PAIRING(3),

    /**
     * Signed + encrypted messages (post-pairing signed pod commands). Not yet used by any
     * caller in this codebase - there's no signed-command layer built yet (Swift's
     * O5AidCommands.swift equivalent) - but MessagePacket's tag-size handling below already
     * accounts for it, matching Swift's MessagePacket.swift exactly, so the wire format is
     * correct from the moment this type does get a caller.
     */
    ENCRYPTED_SIGNED(4);

    companion object {

        fun byValue(value: Byte): MessageType =
            MessageType.entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown MessageType: $value")
    }
}
