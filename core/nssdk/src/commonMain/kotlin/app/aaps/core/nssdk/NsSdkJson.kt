package app.aaps.core.nssdk

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration the Nightscout wire layer uses, for both Retrofit and the string
 * mappers, so they cannot drift apart.
 *
 * Each flag is here to match what Gson did before, not because it is a good default in the
 * abstract. `GsonTypeCoercionTest` pins the old behaviour and must keep passing.
 *
 * - **`ignoreUnknownKeys`** - Nightscout documents carry fields this version has never seen, from
 *   other uploaders and newer AAPS builds. Gson dropped them silently; without this, kotlinx throws.
 *
 * - **`explicitNulls = false`** - on the way out, a null field is omitted rather than written as
 *   `"field": null`. Gson omits nulls by default, and NS validation rejects some explicit nulls.
 *
 * - **`isLenient`** - the one that needs justifying. It exists for a single measured difference:
 *   a bare **number arriving in a `String` field**. Strict kotlinx already accepts a quoted number
 *   in a `Long` / `Double` / `Int` field and `"true"` in a `Boolean` field, so those need nothing.
 *   The gap is real data, not a hypothetical: [app.aaps.core.nssdk.remotemodel.RemoteTreatment]
 *   declares `created_at` as `String?` and its own comment records that servers send it "with string
 *   others with long".
 *
 *   The cost is that some malformed JSON which throws today would parse instead. That is the safer
 *   direction here: the socket.io listener that feeds this
 *   (`NSClientV3Service.onDataCreateUpdate`) has no try/catch, so a throw escapes onto the socket
 *   callback thread and loses the record. `RealNightscoutTreatmentTest` pins which malformed inputs
 *   still throw.
 *
 * - **`encodeDefaults = true`** - required for backward compatibility, and the easiest one to get
 *   wrong, because kotlinx's default is the opposite of Gson's.
 *
 *   Gson writes every non-null field. kotlinx omits any field whose value equals its default, so
 *   `isReadOnly = false`, `duration = 0` and every one of the nullable fields that were given a
 *   `= null` default would simply stop being written. A document AAPS uploads has to stay readable
 *   by older AAPS versions and by the Nightscout server's own validation, neither of which is
 *   updated at the same time as this app - and NS rejects some documents outright for a missing
 *   field (`"Bad or missing utcOffset field"`). Absent is not the same as false.
 *
 *   `NsSdkWireFormatTest` compares what goes onto the wire, field by field.
 *
 * - **`coerceInputValues = true`** - forward compatibility with senders that are newer than this
 *   app, and the other flag that is not optional.
 *
 *   An **unknown enum value** throws in kotlinx but was silently mapped to `null` by Gson. That is
 *   not a corner case: `eventType` is an enum, Nightscout is written to by many uploaders, and a
 *   newer AAPS adding one event type would otherwise make every older client throw on that record -
 *   inside a socket.io listener with no try/catch, so the record is lost and the exception escapes
 *   onto the callback thread. Coercing to the default (`null`) keeps the old, tolerant behaviour:
 *   the treatment is simply not recognised and is skipped, which is what already happens for an
 *   absent `eventType`.
 *
 *   Found by `RealNightscoutTreatmentTest`, which parses an `"eventType":"Something New"` record for
 *   exactly this reason.
 */
internal val nsSdkJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}
