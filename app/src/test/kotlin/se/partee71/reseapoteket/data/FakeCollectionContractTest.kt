package se.partee71.reseapoteket.data

import se.partee71.reseapoteket.core.schema.Doc
import se.partee71.reseapoteket.core.schema.Schema

/** `CollectionContract` mot [FakeCollection] – körs i JVM i varje PR som rör appen. */
class FakeCollectionContractTest : CollectionContract() {
    override fun createEnvironment(): Environment = object : Environment {
        private val scope = TestHouseholdScope()
        private val clock = FixedClock()
        private val factory = FakeCollectionFactory(scope = scope, clock = clock)

        override val householdId = scope.householdId.value!!
        override val collections = factory
        override val now = clock.instant

        override suspend fun writeRaw(path: String, id: String, doc: Doc) = factory.store.set(path, id, doc, merge = false)

        override suspend fun readRaw(path: String, id: String) = factory.store.read(path, id)

        override fun makeHouseholdNewerThanApp() {
            scope.schemaVersion.value = Schema.CURRENT_VERSION + 1
        }
    }
}
