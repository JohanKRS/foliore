package com.foliore.android.storage

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Immutable commits: concurrent devices never replace one another's index. */
internal class RemoteManifestJournal(private val store: RemoteLibraryStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private var observed = emptyList<Commit>()
    private var baseline: JsonObject? = null
    private var epoch = ""
    private val startedNanos = System.nanoTime()

    /** Carries both the validated view and its observed causal parents into the sync. */
    class Snapshot internal constructor(
        val journal: RemoteManifestJournal,
        val store: RemoteLibraryStore,
        val text: String?,
    )

    fun snapshot(): Snapshot = Snapshot(this, store, read())

    fun read(): String? {
        val entries = store.listRootEntries()
        epoch = deletionEpoch(refresh = false)
        observed = readCommitted(entries).values.filter { it.epoch == epoch }
        if (observed.isEmpty()) {
            return null
        }
        baseline = merge(observed)
        return baseline.toString()
    }

    fun publish(manifestText: String) {
        check(System.nanoTime() - startedNanos < MAX_TRANSACTION_NANOS) { "Sync expired; retry before publishing" }
        val manifest = json.parseToJsonElement(manifestText).jsonObject
        val prior = baseline?.books().orEmpty().associateBy { it.id() }
        val explicitIds = observed.flatMap { it.manifest.books() }.map { it.id() }.toSet()
        val materialize = baseline?.books().orEmpty().filter { it.id() !in explicitIds && it["conflictOriginId"] != null }
            .flatMap { listOf(it.id(), it.getValue("conflictOriginId").jsonPrimitive.content) }.toSet()
        val changed = manifest.books().filter { it.id() in materialize || prior[it.id()]?.let { previous -> JsonObject(previous - "mergeBase") } != it }.map { book ->
            val baseMetadata = prior[book.id()]?.get("metadataSnapshot")
            if (baseMetadata == null) book else JsonObject(book + ("mergeBase" to baseMetadata))
        }
        // The first commit records the full library; later commits contain causal changes.
        val payload = if (observed.isEmpty()) manifest else JsonObject(manifest + ("books" to JsonArray(changed)))
        val commit = Commit(UUID.randomUUID().toString(), observed.map { it.id }, payload, epoch = epoch)
        val path = "$COMMIT_PREFIX${commit.id}.json"
        val encoded = json.encodeToString(commit)
        val recordPath = "$RECORD_PREFIX${commit.id}.json"
        store.writeText(recordPath, encoded)
        check(store.readText(recordPath) == encoded) { "Could not verify index record" }
        check(deletionEpoch() == epoch) { "Remote library was deleted during sync" }
        val digest = encoded.digest()
        store.writeText(path, digest, "text/plain")
        check(store.readText(path) == digest) { "Could not verify commit marker" }
    }

    private fun readCommitted(entries: List<RemoteRootEntry>): Map<RemoteRootEntry, Commit> {
        val candidates = entries.filter { it.name.startsWith(COMMIT_PREFIX) && !it.isDirectory }
        val parallelism = store.maxConcurrentDownloads.coerceIn(1, 3).coerceAtMost(candidates.size)
        if (parallelism < 2) return candidates.mapNotNull(::readCommit).toMap()
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            val reads = candidates.map { entry -> executor.submit<Pair<RemoteRootEntry, Commit>?> { readCommit(entry) } }
            // Preserve listing order, and fail the whole read if any committed record is invalid.
            return reads.mapNotNull { it.get() }.toMap()
        } finally {
            executor.shutdownNow()
            while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                // Do not leave provider requests running after this journal operation exits.
            }
        }
    }

    private fun readCommit(entry: RemoteRootEntry): Pair<RemoteRootEntry, Commit>? {
        val id = entry.name.removePrefix(COMMIT_PREFIX).removeSuffix(".json")
        requireSafeBookId(id)
        val marker = store.readText(entry.name).orEmpty()
        // Only a complete 64-byte checksum publishes a record. Interrupted
        // marker writes are not commits; complete but damaged commits fail closed.
        if (!Regex("[a-f0-9]{64}").matches(marker)) return null
        val text = checkNotNull(store.readText("$RECORD_PREFIX$id.json")) { "Committed index record is missing" }
        check(text.digest() == marker) { "Committed index checksum mismatch" }
        val commit = json.decodeFromString<Commit>(text)
        check(commit.id == id && commit.schema == 1) { "Unsupported or invalid commit" }
        return entry to commit
    }

    private fun deletionEpoch(refresh: Boolean = true): String {
        if (refresh) store.listRootEntries()
        return store.readText("library.deleted.json")?.digest().orEmpty()
    }

    private fun String.digest() = java.security.MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { "%02x".format(it) }

    /** Keep recovery history for 30 days; only collect objects owned by this protocol. */
    fun collectGarbage(nowMillis: Long? = null) {
        val entries = store.listRootEntries()
        val clock = nowMillis ?: store.currentTimeMillis ?: return
        val cutoff = clock - RETENTION_MILLIS
        // Nothing can be collected before the retention deadline. Avoid downloading
        // the entire journal again when every owned object is still recent.
        if (entries.none { entry ->
                entry.modifiedAtMillis in 1 until cutoff &&
                    (entry.isDirectory && entry.name.startsWith(DATA_PREFIX) ||
                        !entry.isDirectory && (entry.name.startsWith(COMMIT_PREFIX) || entry.name.startsWith(RECORD_PREFIX)))
            }) return
        val collectionEpoch = deletionEpoch(refresh = false)
        val commits = readCommitted(entries)
        val newest = commits.values.filter { it.epoch == collectionEpoch }.maxByOrNull { it.manifest["updatedAt"]?.jsonPrimitive?.content.orEmpty() }?.id
        val obsolete = commits.filter { (entry, candidate) ->
            entry.modifiedAtMillis in 1 until cutoff && candidate.id != newest &&
                (candidate.epoch != collectionEpoch || candidate.manifest.books().all { book ->
                    commits.values.any { other -> candidate.id in other.parents && other.manifest.books().any { it.id() == book.id() } }
                })
        }.keys.take(100)
        // Remove redundant index records before their unreferenced payloads. A failure
        // leaves extra retained data, never a manifest pointing to a collected file.
        deletePaths(obsolete.map { it.name })
        deletePaths(obsolete.map { RECORD_PREFIX + it.name.removePrefix(COMMIT_PREFIX) })
        val retained = commits.filterKeys { it !in obsolete }
        val referenced = retained.flatMap { (entry, commit) ->
            if (entry.modifiedAtMillis >= cutoff) references(commit.manifest)
            else commit.manifest.books().filter { it["deleted"]?.jsonPrimitive?.booleanOrNull != true }.flatMap(::references)
        }.toSet()
        val committedRecordNames = commits.keys.map { RECORD_PREFIX + it.name.removePrefix(COMMIT_PREFIX) }.toSet()
        deletePaths(entries.filter { entry -> !entry.isDirectory && entry.modifiedAtMillis in 1 until cutoff &&
            ((entry.name.startsWith(RECORD_PREFIX) && entry.name !in committedRecordNames) ||
                (entry.name.startsWith(COMMIT_PREFIX) && entry !in commits.keys))
        }.take(100).map { it.name })
        deletePaths(entries.filter { entry ->
            entry.isDirectory && entry.name.startsWith(DATA_PREFIX) &&
                entry.modifiedAtMillis in 1 until cutoff && entry.name !in referenced
        }.take(100).map { it.name })
    }

    private fun deletePaths(paths: List<String>) {
        store.deleteTrees(paths)
    }

    private fun references(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.values.flatMap(::references)
        is JsonArray -> element.flatMap(::references)
        is JsonPrimitive -> element.content.takeIf { it.startsWith(DATA_PREFIX) }?.let { listOf(it.substringBefore('/')) }.orEmpty()
        else -> emptyList()
    }

    @Serializable
    internal data class Commit(
        val id: String,
        val parents: List<String>,
        val manifest: JsonObject,
        val schema: Int = 1,
        val epoch: String = "",
    )

    companion object {
        const val COMMIT_PREFIX = "foliore-v4-commit-"
        const val RECORD_PREFIX = "foliore-v4-record-"
        const val DATA_PREFIX = "foliore-v4-data-"
        const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val MAX_TRANSACTION_NANOS = 60L * 60 * 1_000_000_000

        internal fun merge(commits: List<Commit>): JsonObject {
            check(commits.isNotEmpty())
            val entries = commits.flatMap { c -> c.manifest.books().map { c to it } }
            val explicitIds = entries.map { it.second.id() }.toSet()
            val books = entries.groupBy { it.second.id() }.flatMap { (bookId, versions) ->
                val live = versions.filter { (commit, _) ->
                    versions.none { (other, _) -> commit.id in other.parents }
                }.map { it.second }.distinct().sortedBy { it.toString() }
                val mergedMetadata = mergeIndependentMetadata(live)
                if (mergedMetadata != null) return@flatMap listOf(mergedMetadata)
                val primary = live.firstOrNull { it["deleted"]?.jsonPrimitive?.booleanOrNull == true } ?: live.first()
                listOf(primary) + live.filter { it != primary && it["deleted"]?.jsonPrimitive?.booleanOrNull != true }
                    .map { version ->
                        val recoveryId = UUID.nameUUIDFromBytes((bookId + version.toString()).toByteArray()).toString()
                        JsonObject(version + mapOf("bookId" to JsonPrimitive(recoveryId), "conflictOriginId" to JsonPrimitive(bookId)))
                    }.filter { it.id() !in explicitIds }
            }.distinctBy { it.id() }
            val latest = commits.maxBy { it.manifest["updatedAt"]?.jsonPrimitive?.content.orEmpty() }
            val heads = commits.filter { candidate -> commits.none { candidate.id in it.parents } }
            val prompts = heads.flatMap { it.manifest["aiCoverPrompts"]?.jsonArray.orEmpty() }
                .groupBy { it.jsonObject["id"]?.jsonPrimitive?.content }
                .values.map { group -> group.maxBy { it.jsonObject["selectedAtEpochMillis"]?.jsonPrimitive?.longOrNull ?: 0L } }
            return JsonObject(latest.manifest + mapOf("books" to JsonArray(books), "aiCoverPrompts" to JsonArray(prompts)))
        }

        internal fun mergeIndependentMetadata(versions: List<JsonObject>): JsonObject? {
            if (versions.size < 2 || versions.any { it["deleted"]?.jsonPrimitive?.booleanOrNull == true }) return null
            val bases = versions.map { it["mergeBase"] as? JsonObject ?: return null }.distinct()
            if (bases.size != 1 || versions.map { it["coverHash"] ?: JsonNull }.distinct().size != 1 ||
                versions.map { it["originalHashSha256"] }.distinct().size != 1) return null
            val base = bases.single()
            val snapshots = versions.map { it["metadataSnapshot"] as? JsonObject ?: return null }
            val ignored = setOf("bookId", "schemaVersion", "revision", "updatedAt", "updatedByDeviceId", "status", "originalHashSha256")
            val merged = base.toMutableMap()
            (base.keys + snapshots.flatMap { it.keys }).filterNot { it in ignored }.forEach { field ->
                val changes = snapshots.map { it[field] ?: JsonNull }.filter { it != (base[field] ?: JsonNull) }.distinct()
                if (changes.size > 1) return null
                changes.singleOrNull()?.let { merged[field] = it }
            }
            val primary = versions.minBy { it.toString() }
            val revision = versions.maxOf { it.getValue("revision").jsonPrimitive.long } + 1
            val time = versions.mapNotNull { it["updatedAt"]?.jsonPrimitive?.content }.maxOrNull().orEmpty()
            val bookkeeping = mapOf("revision" to JsonPrimitive(revision), "updatedAt" to JsonPrimitive(time),
                "updatedByDeviceId" to JsonPrimitive("merge"))
            merged.putAll(bookkeeping)
            merged["status"] = JsonPrimitive("OUTDATED_PREPARED")
            return JsonObject(primary + bookkeeping + mapOf("metadataSnapshot" to JsonObject(merged),
                "preparedFile" to JsonNull, "autoMerged" to JsonPrimitive(true)))
        }

        private fun JsonObject.books() = this["books"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        private fun JsonObject.id() = getValue("bookId").jsonPrimitive.content
    }
}
