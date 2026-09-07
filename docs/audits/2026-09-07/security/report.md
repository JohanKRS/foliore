# Security Review: foliore

## Scope

Repository-wide static security review at cbc480d3928222065b3741ca0692ffd5c4c68349.

- Scan mode: repository
- Target kind: git_revision
- Target ID: target_sha256_008cf4cc2cf99c162a5e5126dfb07b83234a52c00a45dfcebc847518a57db491
- Revision: cbc480d3928222065b3741ca0692ffd5c4c68349
- Inventory strategy: repository
- Included paths: .
- Excluded paths: none
- Runtime or test status: not recorded

Limitations and exclusions:
- No runtime attack simulation.
- Worker token usage unavailable.
- Excluded \*\*/build/\*\*, \*\*/.gradle/\*\*, \*\*/.idea/\*\*, \*\*/\*.jar, \*\*/\*.{ttf,png,webp}, \*\*/values\*/strings.xml: Generated/local environment files and bundled binary assets excluded; strings structurally parsed only.

### Scan Summary

| Field | Value |
| --- | --- |
| Scan outcome | completed |
| Reportable findings | 4 |
| Severity mix | medium: 4 |
| Confidence mix | high: 4 |
| Coverage | partial |
| Validation mode | not recorded |

Canonical artifacts: `scan-manifest.json`, `findings.json`, and `coverage.json`. This report is a deterministic projection of those files.

## Threat Model

Foliore is an Android-first local EPUB library manager, not a reader, with a Kotlin shared parser/repository and Compose Android UI (README.md:3; androidApp/src/main/kotlin/com/foliore/android/di/AppContainer.kt:31). Production targets Android API 26–36, applicationId com.foliore.android (androidApp/build.gradle.kts:27). EPUBs arrive through SAF or exported share/view activities, are copied into private storage, parsed, catalogued in SQLite, edited, prepared, and optionally shared or emailed. Actual production wiring additionally includes OpenAI metadata/image generation, Google Books/Open Library lookup, SAF folder synchronization, Google Drive appData synchronization, and background WorkManager synchronization (androidApp/src/main/kotlin/com/foliore/android/di/AppContainer.kt:46; androidApp/src/main/kotlin/com/foliore/android/di/AppContainer.kt:78; androidApp/src/main/kotlin/com/foliore/android/storage/FolioreSyncWorker.kt:12). This is architecture mapping, not completed vulnerability-audit coverage.

### Assets

- Original EPUBs, prepared EPUBs, cover images, editable metadata and change records under Context.filesDir/books/\<bookId\>/; imported IDs are random UUIDs and filenames are fixed (androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:149; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:315). SQLite library state is the Android database foliore.db (shared/src/androidMain/kotlin/com/foliore/shared/db/DatabaseDriverFactory.android.kt:9).
- OpenAI account spending authority: encrypted credential in private SharedPreferences foliore_ai, using AndroidKeyStore alias foliore_openai_api_key and AES-GCM; the plaintext credential is consumed by HTTPS requests to api.openai.com (androidApp/src/main/kotlin/com/foliore/android/storage/AiPreferenceRepository.kt:18; androidApp/src/main/kotlin/com/foliore/android/storage/AiPreferenceRepository.kt:113; androidApp/src/main/kotlin/com/foliore/android/storage/AiPreferenceRepository.kt:134; androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:212; androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:483).
- Google Books credential in private SharedPreferences foliore_metadata_preferences, encrypted under AndroidKeyStore alias foliore_google_books_api_key; request recipient is www.googleapis.com/books/v1/volumes with the key in the query (androidApp/src/main/kotlin/com/foliore/android/storage/MetadataPreferenceRepository.kt:17; androidApp/src/main/kotlin/com/foliore/android/storage/MetadataPreferenceRepository.kt:81; androidApp/src/main/kotlin/com/foliore/android/storage/MetadataPreferenceRepository.kt:102; androidApp/src/main/kotlin/com/foliore/android/metadata/GoogleBooksMetadataService.kt:234).
- SMTP password and sending authority, sender address, destination address and configured SMTP host: private SharedPreferences foliore_kindle_email_preferences; password encrypted with AndroidKeyStore alias foliore_kindle_smtp_password, while addresses/host/user are ordinary preference fields (androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:41; androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:60; androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:172; androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:193).
- Google Drive appData access tokens and account association, returned by Google Identity authorization and used as bearer credentials by GoogleDriveApiClient; requested scope is drive.appdata (androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:64; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:75; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveApiClient.kt:265).
- Remote library confidentiality, consistency and recoverability: library.manifest.json, devices/\<deviceId\>.json, ai-cover-prompts.json, books/\<sanitized author-title\>/\<sanitized title\>.epub and book-state.zip, plus snapshots/last-sync.txt. The uploaded EPUB prefers the prepared version when present (androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:304; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:315; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:338; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1321; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1362).
- Release signing identity configured from repository-root keystore.properties, with storeFile resolved through rootProject.file; secret values are excluded from this analysis (androidApp/build.gradle.kts:9; androidApp/build.gradle.kts:43).

### Trust Boundaries

- Other apps and document providers supply intents, MIME metadata, URIs and file bytes to exported EPUB and cover activities. EPUB admission uses supported actions and MIME/name heuristics; actual reads use ContentResolver. Covers require SEND and image/\* before copying. Android URI grants govern provider access (androidApp/src/main/AndroidManifest.xml:25; androidApp/src/main/AndroidManifest.xml:53; androidApp/src/main/kotlin/com/foliore/android/share/IncomingEpubRepository.kt:13; androidApp/src/main/kotlin/com/foliore/android/share/IncomingEpubRepository.kt:52; androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt:14; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:270).
- Untrusted ZIP/XML/image content crosses into the app process and private storage. EPUB entry reads operate within ZipFile rather than extracting arbitrary archive paths; imported books use a UUID directory. Entry text/bytes are materialized by readText/readBytes, and cover bytes are passed to image processing (androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:20; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:149; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:157).
- Private library data crosses to chosen share recipients through a nonexported FileProvider with URI grants. Provider roots include files/books, files/exports and cache/incoming-covers. Sharing creates files/exports/\<metadata-derived filename\> and grants read access to its URI (androidApp/src/main/AndroidManifest.xml:64; androidApp/src/main/res/xml/file_paths.xml:3; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:73; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:125; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:134).
- User-entered SMTP credentials and an exported prepared EPUB cross to the configured SMTP host, then the configured recipient. Default destination server is smtp.gmail.com:587. Port 465 selects implicit TLS; advertised STARTTLS upgrades other connections; missing STARTTLS is rejected specifically on 587 before AUTH LOGIN. SSLSocket configuration is owned by SmtpEmailSender (androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:202; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:22; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:30; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:144; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:87).
- Library metadata and user prompts cross to OpenAI; metadata request construction additionally includes originalFilePath and identifiers. Generated metadata is parsed into a draft; generated image bytes are written to cache/generated-covers/\<bookId\>/\<generationId\>/\<index\>-\<styleId\>.png (androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:33; androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:55; androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:66; androidApp/src/main/kotlin/com/foliore/android/ai/OpenAiApiService.kt:126).
- External bibliographic services receive searches and return metadata/images. Google Books uses www.googleapis.com/books/v1/volumes; Open Library uses openlibrary.org/search.json, work JSON and covers.openlibrary.org. Downloaded cover files reside under cache/metadata-covers/\<bookId\>/ (androidApp/src/main/kotlin/com/foliore/android/metadata/GoogleBooksMetadataService.kt:170; androidApp/src/main/kotlin/com/foliore/android/metadata/GoogleBooksMetadataService.kt:234; androidApp/src/main/kotlin/com/foliore/android/metadata/OpenLibraryMetadataService.kt:175; androidApp/src/main/kotlin/com/foliore/android/metadata/OpenLibraryMetadataService.kt:190; androidApp/src/main/kotlin/com/foliore/android/metadata/OpenLibraryMetadataService.kt:195; androidApp/src/main/kotlin/com/foliore/android/metadata/OpenLibraryMetadataService.kt:239).
- SAF synchronization reads, replaces and deletes data in a user-selected document tree under persisted read/write permission. Effective library root is either an existing recognized root or its Foliore Library child. Root reconciliation may remove duplicate roots and loose legacy library artifacts; this authority is broader than a single export file (androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:29; androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:40; androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:103; androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:117; androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:155; androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:241; androidApp/src/main/kotlin/com/foliore/android/storage/SyncFolderStructureRules.kt:6).
- Google Drive synchronization uses Google Identity consent/account resolution and the appDataFolder root, independently of SAF tree permissions. Authorization first requests the remembered account and falls back to an unpinned request after a failure. Normal sync and explicit backup deletion/recreation are separate operations; recreation has a false default guard (androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:23; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:64; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAppDataRemoteLibraryStore.kt:14; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveSyncService.kt:40; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveSyncService.kt:60; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveSyncService.kt:115).
- Both remote stores feed the same deserialized manifest and per-book ZIP state consumer. Remote bookId contributes to files/books/\<bookId\>, cache/sync-download/\<bookId\>-\<UUID\>, cache/sync-backup/\<bookId\>-\<UUID\>, and cache/sync-state/\<bookId\>/book-state.zip. ZIP state extraction separately reduces entry names to their final component and accepts a fixed filename set. Replacement uses staging, backup and rollback (androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:61; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:522; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:736; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:784; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1030; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1390).
- WorkManager constructs its own AppContainer and invokes the same sync service after checking auto-sync settings; network constraints distinguish unmetered from connected and periodic work is six-hourly (androidApp/src/main/kotlin/com/foliore/android/storage/FolioreSyncWorker.kt:12; androidApp/src/main/kotlin/com/foliore/android/storage/SyncWorkScheduler.kt:52; androidApp/src/main/kotlin/com/foliore/android/storage/SyncWorkScheduler.kt:66).

### Attacker Capabilities

- An ordinary external app or content author can supply an EPUB or image and associated share-intent fields; it does not thereby own Foliore's private files, AndroidKeyStore keys or configured cloud account (androidApp/src/main/AndroidManifest.xml:25; androidApp/src/main/AndroidManifest.xml:53).
- A writer to the selected SAF library, or a document provider implementing that tree, can change manifest, EPUB and state-bundle inputs subsequently read by synchronization. This capability requires user selection of that tree or separately acquired write authority; it is not an anonymous Internet endpoint (androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:40; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:1030).
- A Drive attacker needs access to the relevant application's appData content or equivalent account/app authorization; drive.appdata must not be modeled as publicly readable storage (androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:120; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveApiClient.kt:344).
- Network intermediaries can influence unauthenticated traffic and availability; remote servers control their own responses. Whether a specific SMTP boundary permits credential interception requires investigation of the effective TLS configuration, rather than assuming default JVM hostname checks (androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:30; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:144; androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207).
- A malicious repository contributor or compromised release host is a different privileged build threat: release signing properties are consumed by Gradle, not exposed through the app's document-import interface (androidApp/build.gradle.kts:9; androidApp/build.gradle.kts:43).

### Security Objectives

- Preserve original EPUB integrity, isolate imported/remote identifiers and archive names from unrelated private paths, and keep failures from destroying existing library records. The product's original-preservation claim and imported-file placement establish the intended invariant (README.md:3; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:149; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:736).
- Keep credentials private at rest and transmit them only to the intended authenticated service; constrain exported content to the user's chosen recipient and URI grant (androidApp/src/main/kotlin/com/foliore/android/storage/AiPreferenceRepository.kt:113; androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:172; androidApp/src/main/kotlin/com/foliore/android/storage/AndroidPreparedEpubService.kt:73).
- Keep synchronization within the selected provider/account/library authority and bind deletion/recreation to the appropriate operation; maintain recoverability across partially completed downloads (androidApp/src/main/kotlin/com/foliore/android/storage/ManualSyncService.kt:40; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveSyncService.kt:60; androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:736).
- Treat imported and remote data as parsing inputs, not executable instructions or unlimited resource budgets, and disclose actual third-party transfers including AI prompts/metadata. These are review objectives, not claims that all controls already exist.

### Assumptions

- User context: assess readiness before Play Store publication across security, functionality, UI and UX. No user-supplied threat model or knowledge base was provided. The requested policy resolver returned no applicable policy content.
- Documentation is stale: docs/SYNC_SPEC.md:3 says synchronization is intentionally unimplemented, while AppContainer wires both production providers and FolioreSyncWorker invokes synchronization (androidApp/src/main/kotlin/com/foliore/android/di/AppContainer.kt:78; androidApp/src/main/kotlin/com/foliore/android/storage/FolioreSyncWorker.kt:12). README.md:7 similarly describes an earlier first slice.
- Local-first is not equivalent to local-only: optional AI, metadata lookups, SMTP, cloud/folder sync and Android backup are actual egress surfaces. AndroidManifest.xml:7 enables allowBackup and AndroidManifest.xml:14 enables cleartext traffic. Exact OS/OEM backup behavior and deployed user settings were not observed.
- Context.filesDir, cacheDir, SharedPreferences and Android database locations are platform-managed private directories; their app-relative paths above are resolved from actual consumers. A physical /data/user/\<userId\>/... prefix is not hardcoded and was not assumed.
- The Google Drive account chosen by fallback authorization, OAuth release-signature registration and consent configuration require deployment verification; source alone does not establish the effective production account or console configuration (androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:23; androidApp/src/main/kotlin/com/foliore/android/storage/GoogleDriveAuthService.kt:64).
- Release signing is conditional on keystore.properties existence; its secret contents and actual keystore path were deliberately not read. Debug-only exported import/sync receivers are declared under src/debug and must not be attributed to the release manifest (androidApp/build.gradle.kts:55; androidApp/src/debug/AndroidManifest.xml:5; androidApp/src/debug/AndroidManifest.xml:12).
- iOS is a placeholder rather than an established shipping interface (README.md:28). No application execution, network access, source modifications, exploit inputs or vulnerability validation were performed.
- 103 nonempty citation anchors across 29 existing repository files were batch-checked before returning.

## Findings

| Finding | Severity | Confidence | Detailed write-up |
| --- | --- | --- | --- |
| [Unbounded EPUB and image processing allows import denial of service](#finding-1) | medium | high | inline below |
| [SMTP TLS does not verify the server hostname before transmitting credentials](#finding-2) | medium | high | inline below |
| [Remote manifest bookId can escape the library directory and replace app data](#finding-3) | medium | high | inline below |
| [Custom SMTP ports permit authentication without TLS](#finding-4) | medium | high | inline below |

### Confidence Scale

| Label | Meaning |
| --- | --- |
| high | Direct evidence supports the finding with no material unresolved blocker. |
| medium | Evidence supports a plausible issue, but material runtime or reachability proof remains. |
| low | Evidence is incomplete and the item is retained only for explicit follow-up. |

<a id="finding-1"></a>

### [1] Unbounded EPUB and image processing allows import denial of service

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Independent parent source trace confirms the missing control; no exploit was run. |
| Category | resource-exhaustion |
| CWE | CWE-400 |
| Affected lines | androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:287-295, androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15-22, androidApp/src/main/kotlin/com/foliore/android/storage/CoverImageWriter.kt:65-78, androidApp/src/main/kotlin/com/foliore/android/share/CoverShareReceiverActivity.kt:73-79, androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt:25-33 |

#### Summary

A small compressed EPUB containing a very large OPF or cover entry can exhaust heap and crash import. Large or nonterminating provider streams can fill storage. A compact raster image with huge pixel dimensions can exhaust memory despite the intended 1400x2100 output limit. Cover sharing additionally performs its unrestricted copy synchronously in Activity.onCreate, allowing UI hangs.

#### Root Cause

MainActivity.kt:99-103 automatically imports received EPUB URIs. AndroidEpubImporter.kt:287-294 copies input until EOF without a byte limit. AndroidZipEpubArchive.kt:15-22 reads entire decompressed text/image entries into memory. AndroidEpubImporter.kt:157-160 parses the archive and decodes its cover. CoverImageWriter.kt:70 fully decodes raster images before the later resizing at :23-25.

**entrypoint: AndroidEpubImporter** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:287-295`

External input enters the reviewed flow.

```kotlin
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    bytesCopied += read
                }
            }
```

**root_control: AndroidZipEpubArchive** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15-22`

The expected validation is absent before the sensitive operation.

```kotlin
    override fun readText(path: String): String? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
```

**sink: CoverImageWriter** — `androidApp/src/main/kotlin/com/foliore/android/storage/CoverImageWriter.kt:65-78`

This operation establishes the reported impact.

```kotlin
    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.looksLikeSvg()) {
            decodeSvg(bytes)?.let { return it }
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            }.getOrNull()?.let { return it }
```

**entrypoint: CoverShareReceiverActivity** — `androidApp/src/main/kotlin/com/foliore/android/share/CoverShareReceiverActivity.kt:73-79`

This operation establishes the reported impact.

```kotlin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allowDisplayCutout()

        val incomingCoverUri = IncomingCoverRepository(this).copyFromIntent(intent)
```

**sink: IncomingCoverRepository** — `androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt:25-33`

This operation establishes the reported impact.

```kotlin
        return runCatching {
            val cacheDir = File(appContext.cacheDir, "incoming-covers").apply { mkdirs() }
            val target = File(cacheDir, "${UUID.randomUUID()}.img")
            val input = appContext.contentResolver.openInputStream(source) ?: return null
            input.use { sourceStream ->
                FileOutputStream(target).use { output ->
                    sourceStream.copyTo(output)
                }
            }
```

#### Validation

Untrusted documents must have bounded compressed size, expanded size, image dimensions, and processing work. A small compressed EPUB containing a very large OPF or cover entry can exhaust heap and crash import. Large or nonterminating provider streams can fill storage. A compact raster image with huge pixel dimensions can exhaust memory despite the intended 1400x2100 output limit. Cover sharing additionally performs its unrestricted copy synchronously in Activity.onCreate, allowing UI hangs.

Validation method: Independent static source trace

**entrypoint: AndroidEpubImporter** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:287-295`

External input enters the reviewed flow.

```kotlin
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    bytesCopied += read
                }
            }
```

**root_control: AndroidZipEpubArchive** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15-22`

The expected validation is absent before the sensitive operation.

```kotlin
    override fun readText(path: String): String? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
```

**sink: CoverImageWriter** — `androidApp/src/main/kotlin/com/foliore/android/storage/CoverImageWriter.kt:65-78`

This operation establishes the reported impact.

```kotlin
    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.looksLikeSvg()) {
            decodeSvg(bytes)?.let { return it }
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            }.getOrNull()?.let { return it }
```

**entrypoint: CoverShareReceiverActivity** — `androidApp/src/main/kotlin/com/foliore/android/share/CoverShareReceiverActivity.kt:73-79`

This operation establishes the reported impact.

```kotlin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allowDisplayCutout()

        val incomingCoverUri = IncomingCoverRepository(this).copyFromIntent(intent)
```

**sink: IncomingCoverRepository** — `androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt:25-33`

This operation establishes the reported impact.

```kotlin
        return runCatching {
            val cacheDir = File(appContext.cacheDir, "incoming-covers").apply { mkdirs() }
            val target = File(cacheDir, "${UUID.randomUUID()}.img")
            val input = appContext.contentResolver.openInputStream(source) ?: return null
            input.use { sourceStream ->
                FileOutputStream(target).use { output ->
                    sourceStream.copyTo(output)
                }
            }
```

Assertions:
- MainActivity.kt:99-103 automatically imports received EPUB URIs.
- AndroidEpubImporter.kt:287-294 copies input until EOF without a byte limit.
- AndroidZipEpubArchive.kt:15-22 reads entire decompressed text/image entries into memory.
- AndroidEpubImporter.kt:157-160 parses the archive and decodes its cover.
- CoverImageWriter.kt:70 fully decodes raster images before the later resizing at :23-25.

Limitations:
- No dynamic exploit reproduction.
- Normal EPUB import runs on Dispatchers.IO, avoiding ordinary main-thread I/O, but this does not bound memory or storage.
- The first parser call uses runCatching, but storeCopied parses again and catches Exception rather than OutOfMemoryError.
- SVG raster output is bounded and internal entities are disabled.
- Google Books cover downloads have an 8 MiB limit, but local/shared covers and EPUB entries do not.

#### Dataflow

MainActivity.kt:99-103 automatically imports received EPUB URIs. -\> AndroidEpubImporter.kt:287-294 copies input until EOF without a byte limit. -\> AndroidZipEpubArchive.kt:15-22 reads entire decompressed text/image entries into memory. -\> AndroidEpubImporter.kt:157-160 parses the archive and decodes its cover. -\> CoverImageWriter.kt:70 fully decodes raster images before the later resizing at :23-25.

- **Source:** A supplier of a malicious EPUB/image, or another installed app sending content to the exported share activities.

- **Sink:** androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt

- **Outcome:** A small compressed EPUB containing a very large OPF or cover entry can exhaust heap and crash import. Large or nonterminating provider streams can fill storage. A compact raster image with huge pixel dimensions can exhaust memory despite the intended 1400x2100 output limit. Cover sharing additionally performs its unrestricted copy synchronously in Activity.onCreate, allowing UI hangs.

**entrypoint: AndroidEpubImporter** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt:287-295`

External input enters the reviewed flow.

```kotlin
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    bytesCopied += read
                }
            }
```

**root_control: AndroidZipEpubArchive** — `androidApp/src/main/kotlin/com/foliore/android/storage/AndroidZipEpubArchive.kt:15-22`

The expected validation is absent before the sensitive operation.

```kotlin
    override fun readText(path: String): String? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
```

**sink: CoverImageWriter** — `androidApp/src/main/kotlin/com/foliore/android/storage/CoverImageWriter.kt:65-78`

This operation establishes the reported impact.

```kotlin
    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.looksLikeSvg()) {
            decodeSvg(bytes)?.let { return it }
        }

        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            }.getOrNull()?.let { return it }
```

**entrypoint: CoverShareReceiverActivity** — `androidApp/src/main/kotlin/com/foliore/android/share/CoverShareReceiverActivity.kt:73-79`

This operation establishes the reported impact.

```kotlin

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        allowDisplayCutout()

        val incomingCoverUri = IncomingCoverRepository(this).copyFromIntent(intent)
```

**sink: IncomingCoverRepository** — `androidApp/src/main/kotlin/com/foliore/android/share/IncomingCoverRepository.kt:25-33`

This operation establishes the reported impact.

```kotlin
        return runCatching {
            val cacheDir = File(appContext.cacheDir, "incoming-covers").apply { mkdirs() }
            val target = File(cacheDir, "${UUID.randomUUID()}.img")
            val input = appContext.contentResolver.openInputStream(source) ?: return null
            input.use { sourceStream ->
                FileOutputStream(target).use { output ->
                    sourceStream.copyTo(output)
                }
            }
```

#### Reachability

A supplier of a malicious EPUB/image, or another installed app sending content to the exported share activities.

- **Attacker:** A supplier of a malicious EPUB/image, or another installed app sending content to the exported share activities.

- **Entry point:** androidApp/src/main/kotlin/com/foliore/android/storage/AndroidEpubImporter.kt

- **Outcome:** A small compressed EPUB containing a very large OPF or cover entry can exhaust heap and crash import. Large or nonterminating provider streams can fill storage. A compact raster image with huge pixel dimensions can exhaust memory despite the intended 1400x2100 output limit. Cover sharing additionally performs its unrestricted copy synchronously in Activity.onCreate, allowing UI hangs.

#### Severity

**Medium** — Import is exposed through share activities and accepts attacker supplied documents.

Additional runtime or deployment evidence could raise or lower this severity.

Impact assessment:
- **Level:** medium
- **Why:** A small compressed EPUB containing a very large OPF or cover entry can exhaust heap and crash import. Large or nonterminating provider streams can fill storage. A compact raster image with huge pixel dimensions can exhaust memory despite the intended 1400x2100 output limit. Cover sharing additionally performs its unrestricted copy synchronously in Activity.onCreate, allowing UI hangs.

Likelihood assessment:
- **Level:** high
- **Why:** Import is exposed through share activities and accepts attacker supplied documents.

#### Remediation

Implement bounded streaming import and per-entry/total decompression budgets, enforce entry-count and pixel-count limits, and sample raster decoding before allocation. Move cover-share copying off the main thread, support cancellation, and delete partial files on failure.

Tests:
- Reject oversized expanded EPUB entries and raster dimensions while cleaning partial files.

<a id="finding-2"></a>

### [2] SMTP TLS does not verify the server hostname before transmitting credentials

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Independent parent source trace confirms the missing control; no exploit was run. |
| Category | certificate-validation |
| CWE | CWE-297 |
| Affected lines | androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:142-153, androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159, androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207-213 |

#### Summary

A network attacker can impersonate the configured SMTP server using an otherwise valid certificate for an unrelated domain, obtain the SMTP password, and intercept the EPUB attachment.

#### Root Cause

KindleEmailPreferenceRepository supplies the user's SMTP credentials. SmtpEmailSender creates raw SSLSocket instances for STARTTLS and implicit TLS. Neither socket enables endpoint identification or performs a hostname check. After the handshake, AUTH LOGIN sends the Base64-encoded username and password.

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:142-153`

External input enters the reviewed flow.

```kotlin
        }

        fun startTls() {
            command("STARTTLS", 220)
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, port, true) as SSLSocket
            sslSocket.soTimeout = TIMEOUT_MS
            sslSocket.startHandshake()
            socket = sslSocket
            tlsActive = true
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

The expected validation is absent before the sensitive operation.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207-213`

This operation establishes the reported impact.

```kotlin
        private fun createSocket(): Socket {
            return if (useImplicitTls) {
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host, port) as SSLSocket)
                    .apply {
                        soTimeout = TIMEOUT_MS
                        startHandshake()
                    }
```

#### Validation

SMTP authentication and EPUB contents must only be sent to the configured, authenticated SMTP host. A network attacker can impersonate the configured SMTP server using an otherwise valid certificate for an unrelated domain, obtain the SMTP password, and intercept the EPUB attachment.

Validation method: Independent static source trace

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:142-153`

External input enters the reviewed flow.

```kotlin
        }

        fun startTls() {
            command("STARTTLS", 220)
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, port, true) as SSLSocket
            sslSocket.soTimeout = TIMEOUT_MS
            sslSocket.startHandshake()
            socket = sslSocket
            tlsActive = true
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

The expected validation is absent before the sensitive operation.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207-213`

This operation establishes the reported impact.

```kotlin
        private fun createSocket(): Socket {
            return if (useImplicitTls) {
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host, port) as SSLSocket)
                    .apply {
                        soTimeout = TIMEOUT_MS
                        startHandshake()
                    }
```

Assertions:
- KindleEmailPreferenceRepository supplies the user's SMTP credentials.
- SmtpEmailSender creates raw SSLSocket instances for STARTTLS and implicit TLS.
- Neither socket enables endpoint identification or performs a hostname check.
- After the handshake, AUTH LOGIN sends the Base64-encoded username and password.

Limitations:
- No dynamic exploit reproduction.
- The default trust manager validates certificate trust; this is not a trust-all implementation.
- Passing host to createSocket supplies connection/SNI context but does not itself enable JSSE endpoint identification.
- Port 587 requires STARTTLS, so passive cleartext interception on that default port is prevented.

#### Dataflow

KindleEmailPreferenceRepository supplies the user's SMTP credentials. -\> SmtpEmailSender creates raw SSLSocket instances for STARTTLS and implicit TLS. -\> Neither socket enables endpoint identification or performs a hostname check. -\> After the handshake, AUTH LOGIN sends the Base64-encoded username and password.

- **Source:** An active network attacker with a publicly trusted certificate for a different hostname.

- **Sink:** androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt

- **Outcome:** A network attacker can impersonate the configured SMTP server using an otherwise valid certificate for an unrelated domain, obtain the SMTP password, and intercept the EPUB attachment.

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:142-153`

External input enters the reviewed flow.

```kotlin
        }

        fun startTls() {
            command("STARTTLS", 220)
            val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(socket, host, port, true) as SSLSocket
            sslSocket.soTimeout = TIMEOUT_MS
            sslSocket.startHandshake()
            socket = sslSocket
            tlsActive = true
            reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

The expected validation is absent before the sensitive operation.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:207-213`

This operation establishes the reported impact.

```kotlin
        private fun createSocket(): Socket {
            return if (useImplicitTls) {
                ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(host, port) as SSLSocket)
                    .apply {
                        soTimeout = TIMEOUT_MS
                        startHandshake()
                    }
```

#### Reachability

An active network attacker with a publicly trusted certificate for a different hostname.

- **Attacker:** An active network attacker with a publicly trusted certificate for a different hostname.

- **Entry point:** androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt

- **Outcome:** A network attacker can impersonate the configured SMTP server using an otherwise valid certificate for an unrelated domain, obtain the SMTP password, and intercept the EPUB attachment.

#### Severity

**Medium** — Credential compromise is serious, but requires an active network attacker with a trusted certificate for another domain.

Additional runtime or deployment evidence could raise or lower this severity.

Impact assessment:
- **Level:** high
- **Why:** A network attacker can impersonate the configured SMTP server using an otherwise valid certificate for an unrelated domain, obtain the SMTP password, and intercept the EPUB attachment.

Likelihood assessment:
- **Level:** medium
- **Why:** Credential compromise is serious, but requires an active network attacker with a trusted certificate for another domain.

#### Remediation

Enable SSLParameters.endpointIdentificationAlgorithm before both TLS handshakes, or use a maintained SMTP client with explicit hostname verification enabled. Add a test that rejects a trusted certificate issued for another hostname.

Tests:
- Reject a CA-trusted certificate for a different SMTP hostname in both TLS modes.

<a id="finding-3"></a>

### [3] Remote manifest bookId can escape the library directory and replace app data

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Independent parent source trace confirms the missing control; no exploit was run. |
| Category | path-traversal |
| CWE | CWE-22 |
| Affected lines | androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:61-65, androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:520-526, androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:750-771 |

#### Summary

A manifest entry such as bookId='../../shared_prefs', with valid book state and a distinct hash, targets the app's shared_prefs directory when the books directory exists. Successful commit replaces that directory and deletes its backup. Other app-writable directories can also be targeted, causing loss or corruption of settings and library data.

#### Root Cause

ManualSyncService.remoteStoreOrNull supplies the configured external document tree to PortableLibrarySyncEngine. PortableLibrarySyncEngine.kt:61-65 reads manifest books without validating bookId; :1030-1033 merely deserializes JSON. PortableLibrarySyncEngine.kt:522-526 interpolates bookId into local destination and staging filesystem paths. PortableLibrarySyncEngine.kt:741-779 backs up, recursively replaces, and deletes those directories.

**entrypoint: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:61-65`

External input enters the reviewed flow.

```kotlin
        val remoteManifest = store.readManifest()
        val remoteBooks = remoteManifest?.books
            ?.filterNot { it.deleted }
            ?.associateBy { it.bookId }
            .orEmpty()
```

**root_control: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:520-526`

The expected validation is absent before the sensitive operation.

```kotlin
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "downloadBook start id=${remoteBook.bookId}")
        val destinationDir = File(appContext.filesDir, "books/${remoteBook.bookId}")
        val stagingDir = File(
            appContext.cacheDir,
            "sync-download/${remoteBook.bookId}-${UUID.randomUUID()}",
        ).apply { mkdirs() }
```

**sink: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:750-771`

This operation establishes the reported impact.

```kotlin
                hasBackup = destinationDir.renameTo(backupDir)
                if (!hasBackup) {
                    check(destinationDir.copyRecursively(backupDir, overwrite = true)) {
                        "Could not back up local book ${record.book.id}"
                    }
                    check(destinationDir.deleteRecursively()) {
                        "Could not replace local book ${record.book.id}"
                    }
                    hasBackup = true
                }
            }

            try {
                if (!stagingDir.renameTo(destinationDir)) {
                    check(stagingDir.copyRecursively(destinationDir, overwrite = true)) {
                        "Could not store downloaded book ${record.book.id}"
                    }
                }
                libraryRepository.insertImportedBook(record)
                if (hasBackup) {
                    backupDir.deleteRecursively()
                }
```

#### Validation

Untrusted synchronization records must only modify their own validated book directory. A manifest entry such as bookId='../../shared_prefs', with valid book state and a distinct hash, targets the app's shared_prefs directory when the books directory exists. Successful commit replaces that directory and deletes its backup. Other app-writable directories can also be targeted, causing loss or corruption of settings and library data.

Validation method: Independent static source trace

**entrypoint: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:61-65`

External input enters the reviewed flow.

```kotlin
        val remoteManifest = store.readManifest()
        val remoteBooks = remoteManifest?.books
            ?.filterNot { it.deleted }
            ?.associateBy { it.bookId }
            .orEmpty()
```

**root_control: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:520-526`

The expected validation is absent before the sensitive operation.

```kotlin
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "downloadBook start id=${remoteBook.bookId}")
        val destinationDir = File(appContext.filesDir, "books/${remoteBook.bookId}")
        val stagingDir = File(
            appContext.cacheDir,
            "sync-download/${remoteBook.bookId}-${UUID.randomUUID()}",
        ).apply { mkdirs() }
```

**sink: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:750-771`

This operation establishes the reported impact.

```kotlin
                hasBackup = destinationDir.renameTo(backupDir)
                if (!hasBackup) {
                    check(destinationDir.copyRecursively(backupDir, overwrite = true)) {
                        "Could not back up local book ${record.book.id}"
                    }
                    check(destinationDir.deleteRecursively()) {
                        "Could not replace local book ${record.book.id}"
                    }
                    hasBackup = true
                }
            }

            try {
                if (!stagingDir.renameTo(destinationDir)) {
                    check(stagingDir.copyRecursively(destinationDir, overwrite = true)) {
                        "Could not store downloaded book ${record.book.id}"
                    }
                }
                libraryRepository.insertImportedBook(record)
                if (hasBackup) {
                    backupDir.deleteRecursively()
                }
```

Assertions:
- ManualSyncService.remoteStoreOrNull supplies the configured external document tree to PortableLibrarySyncEngine.
- PortableLibrarySyncEngine.kt:61-65 reads manifest books without validating bookId; :1030-1033 merely deserializes JSON.
- PortableLibrarySyncEngine.kt:522-526 interpolates bookId into local destination and staging filesystem paths.
- PortableLibrarySyncEngine.kt:741-779 backs up, recursively replaces, and deletes those directories.

Limitations:
- No dynamic exploit reproduction.
- Ordinary imports generate UUID book IDs, but remote records bypass that path.
- State bundle filenames are basename-allowlisted at lines 791-793; this prevents ordinary archive-entry traversal but does not validate the parent directory derived from bookId.
- AndroidBookDeleter has a canonical parent check, but the synchronization commit does not.
- Android's app sandbox limits the impact to app-writable locations.

#### Dataflow

ManualSyncService.remoteStoreOrNull supplies the configured external document tree to PortableLibrarySyncEngine. -\> PortableLibrarySyncEngine.kt:61-65 reads manifest books without validating bookId; :1030-1033 merely deserializes JSON. -\> PortableLibrarySyncEngine.kt:522-526 interpolates bookId into local destination and staging filesystem paths. -\> PortableLibrarySyncEngine.kt:741-779 backs up, recursively replaces, and deletes those directories.

- **Source:** Someone able to modify a configured SAF synchronization folder, including a shared-folder collaborator or malicious document provider.

- **Sink:** androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt

- **Outcome:** A manifest entry such as bookId='../../shared_prefs', with valid book state and a distinct hash, targets the app's shared_prefs directory when the books directory exists. Successful commit replaces that directory and deletes its backup. Other app-writable directories can also be targeted, causing loss or corruption of settings and library data.

**entrypoint: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:61-65`

External input enters the reviewed flow.

```kotlin
        val remoteManifest = store.readManifest()
        val remoteBooks = remoteManifest?.books
            ?.filterNot { it.deleted }
            ?.associateBy { it.bookId }
            .orEmpty()
```

**root_control: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:520-526`

The expected validation is absent before the sensitive operation.

```kotlin
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "downloadBook start id=${remoteBook.bookId}")
        val destinationDir = File(appContext.filesDir, "books/${remoteBook.bookId}")
        val stagingDir = File(
            appContext.cacheDir,
            "sync-download/${remoteBook.bookId}-${UUID.randomUUID()}",
        ).apply { mkdirs() }
```

**sink: PortableLibrarySyncEngine** — `androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt:750-771`

This operation establishes the reported impact.

```kotlin
                hasBackup = destinationDir.renameTo(backupDir)
                if (!hasBackup) {
                    check(destinationDir.copyRecursively(backupDir, overwrite = true)) {
                        "Could not back up local book ${record.book.id}"
                    }
                    check(destinationDir.deleteRecursively()) {
                        "Could not replace local book ${record.book.id}"
                    }
                    hasBackup = true
                }
            }

            try {
                if (!stagingDir.renameTo(destinationDir)) {
                    check(stagingDir.copyRecursively(destinationDir, overwrite = true)) {
                        "Could not store downloaded book ${record.book.id}"
                    }
                }
                libraryRepository.insertImportedBook(record)
                if (hasBackup) {
                    backupDir.deleteRecursively()
                }
```

#### Reachability

Someone able to modify a configured SAF synchronization folder, including a shared-folder collaborator or malicious document provider.

- **Attacker:** Someone able to modify a configured SAF synchronization folder, including a shared-folder collaborator or malicious document provider.

- **Entry point:** androidApp/src/main/kotlin/com/foliore/android/storage/PortableLibrarySyncEngine.kt

- **Outcome:** A manifest entry such as bookId='../../shared_prefs', with valid book state and a distinct hash, targets the app's shared_prefs directory when the books directory exists. Successful commit replaces that directory and deletes its backup. Other app-writable directories can also be targeted, causing loss or corruption of settings and library data.

#### Severity

**Medium** — Exploitation requires write access to the configured external sync library; Android sandbox limits the affected scope.

Additional runtime or deployment evidence could raise or lower this severity.

Impact assessment:
- **Level:** high
- **Why:** A manifest entry such as bookId='../../shared_prefs', with valid book state and a distinct hash, targets the app's shared_prefs directory when the books directory exists. Successful commit replaces that directory and deletes its backup. Other app-writable directories can also be targeted, causing loss or corruption of settings and library data.

Likelihood assessment:
- **Level:** medium
- **Why:** Exploitation requires write access to the configured external sync library; Android sandbox limits the affected scope.

#### Remediation

Validate remote book IDs as canonical UUIDs before planning any operation. Independently require canonical destination parents to equal the books root. Use random staging/backup names independent of remote identifiers. Reject the entire invalid record before downloads or deletions.

Tests:
- Reject traversal and malformed IDs before any write; preserve existing preferences and book data.

<a id="finding-4"></a>

### [4] Custom SMTP ports permit authentication without TLS

| Field | Value |
| --- | --- |
| Severity | medium |
| Confidence | high |
| Confidence rationale | Independent parent source trace confirms the missing control; no exploit was run. |
| Category | cleartext-transmission |
| CWE | CWE-319 |
| Affected lines | androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:70-75, androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:25-36, androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159 |

#### Summary

On ports such as 25 or 2525, absent or stripped STARTTLS advertisement results in plaintext transmission of recoverable SMTP credentials and the EPUB.

#### Root Cause

KindleEmailPreferenceRepository.kt:74-75 accepts any port from 1 through 65535. SmtpEmailSender.kt:25 enables implicit TLS only on port 465. SmtpEmailSender.kt:30-35 refuses missing STARTTLS only on port 587. SmtpEmailSender.kt:36 and :156-159 then authenticate on the potentially plaintext socket.

**user_input: KindleEmailPreferenceRepository** — `androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:70-75`

External input enters the reviewed flow.

```kotlin
        val cleanPassword = smtpPassword.trim()
        val keepExistingPassword = cleanPassword.isBlank() && hasSmtpPassword()

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanSender).matches()) return false
        if (cleanHost.isBlank() || smtpPort !in 1..65535 || cleanUsername.isBlank()) return false
        if (!keepExistingPassword && cleanPassword.isBlank()) return false
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:25-36`

The expected validation is absent before the sensitive operation.

```kotlin
                useImplicitTls = credentials.smtpPort == IMPLICIT_TLS_PORT,
            )
            smtp.use {
                smtp.expect(220)
                smtp.ehlo()
                if (!smtp.isTlsActive && smtp.supportsStartTls) {
                    smtp.startTls()
                    smtp.ehlo()
                } else if (!smtp.isTlsActive && credentials.smtpPort == STARTTLS_PORT) {
                    error("SMTP server does not advertise STARTTLS")
                }
                smtp.authLogin(credentials.smtpUsername, credentials.smtpPassword)
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

This operation establishes the reported impact.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

#### Validation

Credentials must never be transmitted before TLS is active, regardless of the configured port. On ports such as 25 or 2525, absent or stripped STARTTLS advertisement results in plaintext transmission of recoverable SMTP credentials and the EPUB.

Validation method: Independent static source trace

**user_input: KindleEmailPreferenceRepository** — `androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:70-75`

External input enters the reviewed flow.

```kotlin
        val cleanPassword = smtpPassword.trim()
        val keepExistingPassword = cleanPassword.isBlank() && hasSmtpPassword()

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanSender).matches()) return false
        if (cleanHost.isBlank() || smtpPort !in 1..65535 || cleanUsername.isBlank()) return false
        if (!keepExistingPassword && cleanPassword.isBlank()) return false
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:25-36`

The expected validation is absent before the sensitive operation.

```kotlin
                useImplicitTls = credentials.smtpPort == IMPLICIT_TLS_PORT,
            )
            smtp.use {
                smtp.expect(220)
                smtp.ehlo()
                if (!smtp.isTlsActive && smtp.supportsStartTls) {
                    smtp.startTls()
                    smtp.ehlo()
                } else if (!smtp.isTlsActive && credentials.smtpPort == STARTTLS_PORT) {
                    error("SMTP server does not advertise STARTTLS")
                }
                smtp.authLogin(credentials.smtpUsername, credentials.smtpPassword)
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

This operation establishes the reported impact.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

Assertions:
- KindleEmailPreferenceRepository.kt:74-75 accepts any port from 1 through 65535.
- SmtpEmailSender.kt:25 enables implicit TLS only on port 465.
- SmtpEmailSender.kt:30-35 refuses missing STARTTLS only on port 587.
- SmtpEmailSender.kt:36 and :156-159 then authenticate on the potentially plaintext socket.

Limitations:
- No dynamic exploit reproduction.
- Default port 587 fails closed when STARTTLS is absent.
- Port 465 uses implicit TLS.
- The vulnerable branch requires a custom port, so it does not affect every configured account.

#### Dataflow

KindleEmailPreferenceRepository.kt:74-75 accepts any port from 1 through 65535. -\> SmtpEmailSender.kt:25 enables implicit TLS only on port 465. -\> SmtpEmailSender.kt:30-35 refuses missing STARTTLS only on port 587. -\> SmtpEmailSender.kt:36 and :156-159 then authenticate on the potentially plaintext socket.

- **Source:** A passive or active network attacker when the user configures a nonstandard SMTP port.

- **Sink:** androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt

- **Outcome:** On ports such as 25 or 2525, absent or stripped STARTTLS advertisement results in plaintext transmission of recoverable SMTP credentials and the EPUB.

**user_input: KindleEmailPreferenceRepository** — `androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt:70-75`

External input enters the reviewed flow.

```kotlin
        val cleanPassword = smtpPassword.trim()
        val keepExistingPassword = cleanPassword.isBlank() && hasSmtpPassword()

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanSender).matches()) return false
        if (cleanHost.isBlank() || smtpPort !in 1..65535 || cleanUsername.isBlank()) return false
        if (!keepExistingPassword && cleanPassword.isBlank()) return false
```

**root_control: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:25-36`

The expected validation is absent before the sensitive operation.

```kotlin
                useImplicitTls = credentials.smtpPort == IMPLICIT_TLS_PORT,
            )
            smtp.use {
                smtp.expect(220)
                smtp.ehlo()
                if (!smtp.isTlsActive && smtp.supportsStartTls) {
                    smtp.startTls()
                    smtp.ehlo()
                } else if (!smtp.isTlsActive && credentials.smtpPort == STARTTLS_PORT) {
                    error("SMTP server does not advertise STARTTLS")
                }
                smtp.authLogin(credentials.smtpUsername, credentials.smtpPassword)
```

**sink: SmtpEmailSender** — `androidApp/src/main/kotlin/com/foliore/android/storage/SmtpEmailSender.kt:156-159`

This operation establishes the reported impact.

```kotlin
        fun authLogin(username: String, password: String) {
            command("AUTH LOGIN", 334)
            command(Base64.getEncoder().encodeToString(username.toByteArray(Charsets.UTF_8)), 334)
            command(Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8)), 235)
```

#### Reachability

A passive or active network attacker when the user configures a nonstandard SMTP port.

- **Attacker:** A passive or active network attacker when the user configures a nonstandard SMTP port.

- **Entry point:** androidApp/src/main/kotlin/com/foliore/android/storage/KindleEmailPreferenceRepository.kt

- **Outcome:** On ports such as 25 or 2525, absent or stripped STARTTLS advertisement results in plaintext transmission of recoverable SMTP credentials and the EPUB.

#### Severity

**Medium** — Credential compromise is serious, but requires an active network attacker and a user-configured nonstandard SMTP port.

Additional runtime or deployment evidence could raise or lower this severity.

Impact assessment:
- **Level:** high
- **Why:** On ports such as 25 or 2525, absent or stripped STARTTLS advertisement results in plaintext transmission of recoverable SMTP credentials and the EPUB.

Likelihood assessment:
- **Level:** medium
- **Why:** Credential compromise is serious, but requires an active network attacker and a user-configured nonstandard SMTP port.

#### Remediation

Require isTlsActive unconditionally before AUTH. Model TLS mode explicitly instead of inferring the complete security policy from the port.

Tests:
- Reject AUTH when STARTTLS is absent on 25, 2525 and 587.

## Reviewed Surfaces

| Surface | Risk Area | Outcome | Notes |
| --- | --- | --- | --- |
| Remote manifest to private filesystem | not recorded | Reported | State ZIP extraction strips paths to basenames and permits only five filenames at PortableLibrarySyncEngine.kt:784-803 and :1692-1698. This blocks entry-name Zip Slip but does not constrain destinationDir. SAF remote lookup walks child names beneath the selected root; Drive lookup walks child names under appDataFolder. A '..' remote file-path segment does not itself become native filesystem traversal in either store. repositoryLock protects a commit from other commits on the same engine instance but performs no containment check. Failure rollback at :772-779 does not protect a successful malicious import. |
| SMTP authentication and TLS | not recorded | Reported | Both TLS modes retain trust-chain validation but omit hostname verification. Port 587 fails closed when STARTTLS absent; arbitrary custom ports do not. |
| EPUB and cover parsing resource budgets | not recorded | Reported | No bounded expanded-entry reads or raster preallocation sampling; SVG internal entities disabled. |
| Credential storage, SQL and share permissions | not recorded | No issue found | The supplied root resolver result was empty. Resolver calls for androidApp and shared also returned no policy. No. The reviewed repositories use randomized AES-256-GCM with keys generated in Android Keystore. This does not establish backup/restore correctness; encrypted preferences can be restored without their Keystore keys. The shared EPUB parser uses regular expressions rather than an entity-resolving XML parser. CoverImageWriter explicitly disables AndroidSVG internal entities. No ordinary entry-path filesystem extraction exists in AndroidZipEpubArchive. Sync state extraction uses allowed basenames. The confirmed traversal occurs in remote book IDs used as directory names. The reviewed SQLDelight schema and repository use parameterized statements. No SQL injection was identified. The FileProvider is non-exported and exposes scoped books, exports, and incoming-covers directories. Prepared exports grant read access for the selected URI. Google Drive requests the drive.appdata scope and uses fixed HTTPS API endpoints. OpenAI requests also use fixed HTTPS endpoints. No hardcoded API credential was identified in the reviewed implementation. No. This audit was restricted to static analysis; no application execution or network access was performed. Parent was informed of potential remote-folder deletion after failed downloads, duplicate library-root deletion without merging, JPEG bytes replacing non-JPEG EPUB cover entries without updating media-type, and unescaped regex replacement strings during metadata preparation. These require parent validation and are not counted as separate security findings here. |
| Remaining UI, viewmodels, workers, tests and configuration | not recorded | No issue found | Independent supplemental full-file review completed; combined inventories cover all 103 Kotlin source files. Additional functional issues are tracked separately in the publication audit. No further independent security findings. |

## Open Questions And Follow Up

- No runtime attack simulation; security conclusions are source validated.
- Pending independent parent validation
  - Follow-up prompt: Review deferred unit sync-0 and close its stated proof gap.
- Awaiting parent validation
  - Follow-up prompt: Review deferred unit smtp-tls-identity and close its stated proof gap.
- Awaiting parent validation
  - Follow-up prompt: Review deferred unit sync-bookid-traversal and close its stated proof gap.
