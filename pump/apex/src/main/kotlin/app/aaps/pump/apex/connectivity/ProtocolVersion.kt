package app.aaps.pump.apex.connectivity

enum class ProtocolVersion(
    val major: Int,
    val minor: Int,
) {
    /** The first publicly available protocol.
     **/
    PROTO_4_10(4, 10),

    /** * Became incompatible: `UpdateSettings`, `Status`
     ** * New commands: `GetLatestTemporaryBasals`
     **/
    PROTO_4_11(4, 11),
}