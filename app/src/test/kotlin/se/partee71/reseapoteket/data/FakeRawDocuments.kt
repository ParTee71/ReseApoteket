package se.partee71.reseapoteket.data

import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.data.firestore.RawDocuments

/** [RawDocuments] över [FakeStore] – samma dokument som `FakeCollection` skriver och läser. */
class FakeRawDocuments(private val store: FakeStore) : RawDocuments {
    /** Antal gånger exporten väntat in enhetens skrivningar – i FakeStore är de alltid framme. */
    var pendingWaits = 0
        private set

    override suspend fun awaitPendingWrites() {
        pendingWaits++
    }

    override suspend fun document(path: String): Doc? = store.read(path.substringBeforeLast('/'), path.substringAfterLast('/'))

    override suspend fun collection(path: String): Map<String, Doc> = store.documents.value[path].orEmpty()
}
