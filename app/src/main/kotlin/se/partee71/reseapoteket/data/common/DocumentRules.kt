package se.partee71.reseapoteket.data.common

import kotlin.time.Instant
import se.partee71.reseapoteket.core.schema.DocCodec
import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema
import se.partee71.reseapoteket.core.schema.SchemaMigrator

// Regler för läsning och skrivning som gäller alla samlingar – används av både
// FirestoreCollection och FakeCollection, så att de bevisligen beter sig lika.

/**
 * Före skrivning: `updatedAt` sätts till [now] när modellen har fältet, och ett `createdAt`
 * utan värde skrivs inte – annars skulle en merge radera det lagrade värdet.
 */
fun prepareForWrite(encoded: Doc, now: Instant): Doc = buildMap {
    for ((key, value) in encoded) {
        when {
            key == "updatedAt" -> put(key, now)
            key == "createdAt" && value == null -> Unit
            else -> put(key, value)
        }
    }
}

/**
 * Läsning: dokument i ett äldre format lyfts av [SchemaMigrator] innan de avkodas; nyare
 * format avkodas som de är (appen är då skrivskyddad, se [HouseholdScope.writeBlocker]).
 */
fun <T> readDocument(codec: DocCodec<T>, collection: String, version: Int?, id: String, raw: Doc): T {
    val from = version ?: Schema.CURRENT_VERSION
    val doc = if (Schema.isNewerThanApp(from)) raw else SchemaMigrator.migrate(from, collection, raw)
    return codec.decode(id, doc)
}
