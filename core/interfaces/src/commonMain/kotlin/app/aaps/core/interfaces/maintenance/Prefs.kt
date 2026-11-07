package app.aaps.core.interfaces.maintenance

typealias PrefMetadataMap = Map<PrefsMetadataKey, PrefMetadata>

data class Prefs(val values: Map<String, String>, var metadata: PrefMetadataMap)
