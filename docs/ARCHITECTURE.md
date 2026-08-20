# Weaverse architecture

This document is written to be sufficient, on its own, for an AI (or a
developer) with no access to this repository to reconstruct Weaverse's
module layout, data model, and core protocols from scratch. It describes
what is actually implemented in this codebase, not an aspirational spec.

For the wiki-formatted equivalent (same content, split across pages), see
the [repo Wiki](https://github.com/ihy2ln/weaverse/wiki).

For a feature-level deep dive (rather than this document's module/schema
level), see `docs/features/`, and `docs/CHECKPOINTS.md` for stable points
in the branch history worth knowing about before a large change.

## What the app is

Weaverse is an offline-first novel-writing app (Novelcrafter-style: Plan /
Write / Chat / Review, a Codex, Snippets, a prompt library) combined with a
SillyTavern-style roleplay chat mode (characters, personas, presets, real
streaming chats with swipe-cycled regeneration), plus a Windows desktop
companion that hosts a web UI and a Wi-Fi/remote sync protocol between
devices. Everything except AI text generation works with no network. There
is no account system, no login, no cloud backend — sync is peer-to-peer,
gated by a one-time PIN/password.

## Module layout

A 3-module Gradle project, `rootProject.name = "weaverse"`:

| Module | Type | Depends on | Purpose |
|---|---|---|---|
| `app` | Android application (`com.android.application`) | `sync-core` | The Android app — all UI, Room DB, AI providers. |
| `sync-core` | Pure Kotlin/JVM library | (none) | Sync package format, pairing/auth primitives, and the Novelcrafter ZIP importer/parser — code shared between `app` (Android host) and `desktop` (JVM host) so both sides speak the identical protocol. |
| `desktop` | Kotlin/JVM `application` plugin | `sync-core` | Windows/Linux desktop companion: an embedded Ktor server that serves a web UI and the same sync REST API as the Android host, plus a tiny Go launcher compiled to a native `.exe`. |

`app`'s Gradle namespace/applicationId is `com.ihy2ln.weaverse`; `sync-core`
and `desktop` sources live under the same package prefix
(`com.ihy2ln.weaverse.sync`, `com.ihy2ln.weaverse.desktop`) even though
they're plain JVM, not Android, modules.

Toolchain: Kotlin, JDK 17, Android `compileSdk`/`targetSdk` 35, `minSdk` 26.
UI is 100% Jetpack Compose (Material 3) with Navigation Compose; DI is Hilt;
persistence is Room (+ FTS4 for search); settings are DataStore; API keys
are encrypted at rest with Jetpack Security (`EncryptedSharedPreferences`,
AES-256-GCM). Networking is Ktor (client for the OpenRouter AI provider and
the desktop-peer sync calls, embedded server for the sync/web host).
Images via Coil 3, audio/video via Media3/ExoPlayer. Tests: JUnit 5 +
Turbine + MockK + MockWebServer for `app`; plain JUnit-style Kotlin tests
for `sync-core`.

## `app` package breakdown

Under `app/src/main/java/com/ihy2ln/weaverse/`:

- **`ai/`** — the AI-provider abstraction and the (currently sole) OpenRouter
  implementation. See "AI provider layer" below.
- **`core/`** — cross-cutting, UI-agnostic-ish utilities: `media/` (media
  capture/playback helpers), `roleplay/` (shared roleplay display logic),
  `text/` (rich-text/document-JSON helpers), `tts/` (text-to-speech), `ui/`
  (design system: theme, reusable Compose components, previews), `util/`.
- **`data/`** — the persistence layer: `db/` (Room — `entities/`, `dao/`),
  `repo/` (repositories wrapping DAOs), `backup/` (export/import of a book
  as JSON), `export/novelcrafter/` (Novelcrafter ZIP → entities), `seed/`
  (first-run database seeding + bundled sample book), `settings/`
  (DataStore-backed preferences), `sync/` (`SyncCoordinator` — the Android
  side of the sync protocol, see below).
- **`di/`** — three Hilt modules: `AiModule` (binds the OpenRouter
  provider), `DatabaseModule` (provides `WeaverseDatabase` + DAOs),
  `NetworkModule` (provides the shared Ktor `HttpClient`).
- **`feature/`** — the screens, one subpackage per area: `novel/` (further
  split into `plan/`, `write/` [+ `write/editor/`], `chat/` [the "Workshop"
  AI chat, not roleplay], `review/`, `manuscript/`, `codex/`, `snippets/`),
  `roleplay/` (`characters/`, `personas/`, `presets/`, `chat/`,
  `lorebook/`), `library/`, `notes/`, `prompts/`/`prompt/`, `search/`,
  `settings/`, `export/`, `media/`, and `shell/` (top-level navigation —
  `AppNavigation.kt`, `AppShell.kt`).

## Data model (Room, `WeaverseDatabase`, schema version 3)

One `@Database` (`WeaverseDatabase.kt`) declaring 19 entities and exposing
9 DAO interfaces (`seriesDao`, `bookDao`, `manuscriptDao`, `codexDao`,
`snippetDao`, `workshopChatDao`, `roleplayDao`, `mediaDao`, `promptDao`).
Entities live in one file (`data/db/entities/InkEntities.kt`), DAOs in one
file (`data/db/dao/InkDaos.kt`) — a deliberate "one file per concern"
consolidation rather than one-file-per-class.

**Novel hierarchy** (each level indexed on its parent, cascading
conceptually top-down): `SeriesEntity` (optional grouping) → `BookEntity`
(`seriesId?`, genre/pov/tense/styleGuide/targetWordCount) → `ActEntity` →
`ChapterEntity` (has a `summary`) → `SceneEntity` — the core writing unit:
`docJson` (the rich document, block-based JSON — see "Document format"
below), `plainText` (denormalized for search/word-count/AI context),
`summary`, `beatsJson` (scene-beat prompts as a JSON array), `wordCount`,
`status`, `pov`/`povCharacterId`, `inWorldDate`, `labelsJson`, `colorHex`.

**Codex** (worldbuilding/lore, shared across scopes): `CodexCategoryEntity`
(scoped by `scopeType`+`scopeId` — categories belong to a book *or* a
roleplay character, not globally) → `CodexEntryEntity` (name, aliases,
`docJson`/`plainText`, optional linked image) → optionally
`CodexEntryLoreEntity` (one-to-one on `entryId`) which holds full
SillyTavern-style "World Info" lorebook fields: `keysJson`/
`secondaryKeysJson`, `selectiveLogic`, `insertionOrder`, `position`
(`beforeChar` etc.), `depth`, `probability`, `isConstant`, `caseSensitive`,
`matchWholeWords`, `scanDepth`, `tokenBudgetWeight`, `recursionAllowed`,
`groupName` — i.e. codex entries are dual-purpose: simple worldbuilding
notes by default, full activatable lorebook entries when `CodexEntryLoreEntity`
is attached. `SceneCodexLinkEntity` (composite PK `sceneId`+`entryId`, plus
`source`) is the many-to-many join recording which codex entries are
explicitly linked to which scene (vs. auto-matched via lorebook keys at
generation time).

**Snippets/Notes**: `SnippetEntity` is one table backing two features —
"Snippets" (`category` blank/other, scoped to a book) and "Notes"
(`category = "notes"`, `scopeType = "app"`/`scopeId = "global"` — shared
across the whole app, not per-book). The sync REST API's `/api/notes/{id}`
endpoints read/write this same table filtered on `category == "notes"`.

**Workshop chat** (AI chat about your novel, distinct from roleplay):
`ChatThreadEntity` (optionally pinned, tied to a `promptId` and/or
`sceneId`) → `ChatMessageEntity` (`role`, `contentJson`, `contextUsedJson`
recording which codex/scene context was fed to the model, token/word
counts).

**Roleplay**: `RpCharacterEntity` — a SillyTavern/Chub "character card v2"
superset (`description`, `personality`, `scenario`, `firstMes`,
`mesExample`, `creatorNotes`, `systemPrompt`, `postHistoryInstructions`,
`alternateGreetingsJson`, `tagsJson`, `characterVersion`, `extensionsJson`)
— this shape is what makes PNG character-card import/export
(tEXt-chunk-embedded JSON, the de facto SillyTavern/Chub format)
straightforward. `RpPersonaEntity` (the user's side — can have a default).
`RpChatEntity` — one chat between a persona and a character (or a
`groupId` for group chats), with `displayMode` (`messenger` /
`dungeonMaster` / `roleplay`), author's note + depth, optional preset/
prompt-template refs, `branchOfChatId` for chat branching, and per-role
color overrides. `RpMessageEntity` — `swipeGroupId` + `swipeIndex` +
`isActiveSwipe` implement swipe-to-regenerate (multiple message variants
share a `swipeGroupId`; exactly one is `isActiveSwipe` at a time); also
carries its own `displayMode`, since the same underlying chat's content is
rendered differently (and can diverge) per display mode.

**Media**: one `MediaEntity` table for every image/audio/video attachment
app-wide (`type`, `relativePath` under app-private storage, `mimeType`,
dimensions, `durationMs`, `thumbnailPath`, `checksum`).

**Prompts**: `PromptFolderEntity` → `PromptEntity` (`instructionsJson`
array, `advancedJson` object, `isSystem` flag for built-in, non-deletable
prompts) — the user-editable prompt library referenced by chat threads and
the `/` and `\` quick-generate flows.

**AI connections**: `AiProfileEntity` — a saved provider connection
(`providerType`, `label`, `baseUrl`, `favoriteModelsJson`, `isDefault`).
API keys themselves are **not** in this table — they're in encrypted
DataStore/`EncryptedSharedPreferences`, never in the Room DB (which is what
gets zipped up and synced between devices — keys deliberately don't travel
with a sync package).

### Document format

Scene bodies, codex entries, and other rich text are stored as `docJson` +
a denormalized `plainText`: a block-based JSON document (paragraphs,
scene-beat blocks, media blocks, media-stack blocks — see
`feature/novel/write/editor/` for the block renderers:
`BlockEditorField.kt`, `MediaBlockView.kt`, `MediaStackBlockView.kt`,
`SceneBeatBlockView.kt`, `DocumentEditor.kt`) rather than markdown or HTML.
`plainText` is what search (Room FTS4) and AI context-building read; the
block JSON is what the editor renders and edits.

## AI provider layer

`ai/AIProvider.kt` defines the provider-agnostic contract:

```kotlin
interface AIProvider {
    val name: String
    suspend fun models(): List<ModelInfo>
    fun stream(request: AIRequest): Flow<AIChunk>       // AIChunk = Delta | Usage | Done
    suspend fun complete(request: AIRequest): AIResult
}
```

`AIRequest` carries `modelId`, `systemPrompt`, `messages` (role/content
pairs), sampling params, and optional `imageAttachments` (base64, for
vision-capable models). `AIError` is a sealed hierarchy (`NoApiKey`,
`NoProvider`, `InvalidKey`, `OutOfCredits`, `RateLimited(retryAfterSeconds)`,
`BadRequest`, `ProviderDown`, `NoNetwork`, `HttpFailure`, `EmbeddedError`) —
callers pattern-match this instead of parsing HTTP status codes themselves.

The only implementation today is `ai/providers/OpenRouterProvider.kt`,
backed by `ai/openrouter/OpenRouterRepository.kt` (HTTP calls),
`OpenRouterSseParser.kt` (parses OpenRouter's streaming SSE format into
`AIChunk`s), `OpenRouterModelCache.kt`/`OpenRouterModels.kt` (the "Models"
picker lists every OpenRouter text model, cached), and
`OpenRouterErrorMapper.kt` (maps OpenRouter's HTTP/JSON error shapes onto
`AIError`). `AiGenerationService.kt` is the app-facing entry point features
call into (rather than talking to a provider directly), and
`ai/context/ContextBuilder.kt` + `ai/prompt/PromptTokens.kt` +
`RoleplayPromptBuilder.kt` assemble the actual prompt sent to the model —
pulling in relevant codex entries (lorebook activation by key match against
recent text, respecting `insertionOrder`/`position`/`depth` from
`CodexEntryLoreEntity`), scene/chat history within a token budget, and the
selected prompt template's instructions. `AiModule.kt` (Hilt) binds
`OpenRouterProvider` as the app-wide `AIProvider`. `WeaverseAiLog.kt` is a
small structured logger for AI request/response debugging.

Adding a second provider means: implement `AIProvider`, extend
`AiModule`/whatever provider-registry (`AIProviderRegistry.kt`) picks the
right instance per `AiProfileEntity.providerType`, and add its base URL/key
handling to the Settings → AI Connections screen — the rest of the app
(context building, chat UI, streaming rendering) is provider-agnostic
already.

## Sync protocol

Two hosts can run the identical protocol: the Android app itself
(`data/sync/SyncCoordinator.kt`, embedding a Ktor CIO server) or the
desktop companion (`desktop/`, embedding Ktor Netty — see "Desktop
companion" below). Whichever host is running exposes:

| Route | Method | Purpose |
|---|---|---|
| `/` , `/app.js`, `/app.css` | GET | The web UI (served from `sync-core`'s `sync/web/WebAssets.kt` so both hosts serve byte-identical assets). |
| `/api/status` | GET | Device id/name, app version, host mode, port, **pair PIN**, LAN IP hint. |
| `/api/pair` | POST | Body `{pin}`. Compares against the host's PIN with `SyncAuth.constantTimeEquals` (not `==`, to resist timing attacks); on success returns a random 24-byte hex session token. |
| `/api/library` | GET | Book + note summaries (requires the `X-Weaverse-Token` header). |
| `/api/notes/{id}` | GET / PUT | Read/write a single shared note (the `SnippetEntity` rows with `category="notes"`). |
| `/api/media/{id}` | GET | Serve one media file by id. |
| `/api/import` | POST | Upload a Novelcrafter export ZIP; validated with `NovelcrafterZipParser.looksLikeNovelcrafterZipBytes` before parsing. |
| `/api/sync/pull` | GET | Download the host's full library as a package ZIP (auth required). |
| `/api/sync/push` | POST | Upload a package ZIP to replace/merge into the host's library (auth required). |

**Pairing**: `SyncAuth.newPairPin()` is a random 6-digit PIN, freshly
generated per host-start. The web UI displays it; that's "the one sync
password" the user types into the peer device once. `/api/pair` trades the
PIN for a session token (`SyncAuth.newSessionToken()`, 24 random bytes as
hex) that's then sent as `X-Weaverse-Token` on every authenticated call —
short-lived and per-connection, not persisted, so the PIN itself doesn't
need to be resent for every request.

**Package format** (`sync-core/sync/SyncPackage.kt`): a ZIP containing
`weaverse.db` (+ `weaverse.db-wal`/`-shm` if present — the DB is WAL-
checkpointed with `PRAGMA wal_checkpoint(FULL)` before zipping, so a plain
file copy is consistent), a `media/` directory mirroring the app's media
storage, and `manifest.json` (`SyncManifest`: `protocolVersion`,
`exportedAt`, `deviceId`, `deviceName`, `appVersion`, counts). Restoring
just extracts and overwrites the local `weaverse.db`(`-wal`/`-shm`) and
`media/` in place — **last successful push/pull wins for the whole
library**, there's no field-level merge.

**Auto-sync** (`SyncCoordinator`, Android side): polls every 20s; if a peer
is configured and its last-sync timestamp is newer than local *and* newer
than the local DB's mtime, it pulls (and relaunches the app process so the
freshly-restored Room DB is picked up cleanly, since Room doesn't expect
its backing file to change underneath it); otherwise, if the local DB is
newer than the last recorded sync, it pushes.

**Import**: Novelcrafter export ZIPs (`novel.docx`/`novel.md` +
`characters/` etc.) are parsed by `sync-core/sync/novelcrafter/`
(`NovelcrafterZipParser.kt`, `DocxPlainText.kt`, `WordHeadingHeuristics.kt`,
`NovelcrafterModels.kt`, `NovelcrafterCategories.kt`) into a
provider-neutral intermediate form, then `data/export/novelcrafter/`
(Android-side) maps that into Room entities — codex folders become
Characters/Locations/Objects/Lore categories, and characters additionally
become `RpCharacterEntity` roleplay cards.

## Desktop companion

`desktop/src/main/kotlin/com/ihy2ln/weaverse/desktop/`:

- `Main.kt` — entry point. Resolves a data directory (`DesktopPaths`),
  loads/creates `DesktopConfigStore` (JSON config: port, `openBrowser`
  flag, app version), starts `SyncHttpServer`, prints connection info
  (LAN URL(s), where to drop an import ZIP), optionally opens the system
  browser, and blocks until Ctrl+C.
- `SyncHttpServer.kt` — the desktop-side Ktor Netty server implementing
  the *same* route table as `SyncCoordinator` above (desktop is just
  another sync peer — Android and desktop are symmetric, either can be the
  "host" the other pairs to), backed by `sqlite-jdbc` (plain JDBC against
  `weaverse.db`, not Room, since Room is Android-only) via `LibraryReader.kt`.
- `DesktopPaths.kt` — resolves the data directory: prefers a `data/`
  folder beside the JAR, then a sibling `data/` one level up (the
  `Weaverse/data/` layout used by the distributed PC package), then
  `./data`, then `~/.weaverse`.
- `DesktopConfig.kt` — the JSON-backed local config (port, etc).
- `WebAssets.kt` (in `sync-core`, not `desktop`) — the actual HTML/JS/CSS
  for the web UI, shared so Android-hosted and desktop-hosted web UIs are
  identical.
- `desktop/launcher/` — a **separate Go module**
  (`github.com/ihy2ln/weaverse/desktop/launcher`), not Kotlin: a small
  native launcher that becomes `Weaverse.exe`. It locates a Java 17+
  runtime (checks `JAVA_HOME`, `PATH`, then common install locations under
  `ProgramFiles`/`LOCALAPPDATA` for Temurin/Microsoft/Corretto JDKs) and
  runs `java -jar Weaverse.jar --data=<dir>` next to itself, so end users
  don't need to know Java is involved. Rebuild with:
  `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-s -w" -o ../../Weaverse/Weaverse.exe .`
  from `desktop/launcher/`.

The Gradle `:desktop:packageDesktopZip` task (`desktop/build.gradle.kts`)
stages the fat JAR (renamed `Weaverse.jar`), the `desktop/scripts/*`
launch scripts, and the contents of the repo-root `Weaverse/` folder
(README, SYNC.md, install script, sample import, and — if present —
a prebuilt `Weaverse.exe`) into one zip.

## Build & CI

`./gradlew assembleDebug` (Android), `./gradlew :desktop:packageDesktopZip`
(desktop bundle), `./gradlew :sync-core:test` (protocol/parser unit tests
— pure JVM, no Android SDK needed, the fastest thing to run to sanity-check
a change). GitHub Actions: `build.yml` runs lint + unit tests (`app` and
`sync-core`) + `assembleDebug` + the desktop zip on every push/PR;
`release.yml` (triggered by a `v*` tag, or manually) additionally runs
`assembleRelease` — signed with a keystore decoded from the
`KEYSTORE_BASE64`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` repo
secrets if configured, falling back to debug signing otherwise so a release
build is always produced — and publishes the APK + desktop zip to a GitHub
Release.

## Reconstruction notes

If this repository is gone and you're rebuilding from this document alone:

1. Start with `sync-core` (no Android dependency, easiest to get compiling
   first): the `SyncModels`/`SyncPackage`/`SyncAuth`/`SyncUrls` primitives,
   then the Novelcrafter importer.
2. Stand up the Room schema exactly as described above — table names and
   column defaults matter if you ever need to read an old `weaverse.db`
   from a sync package or backup export.
3. Build the `AIProvider` interface and `OpenRouterProvider` before any UI
   — most features (Write, Workshop chat, Roleplay chat) are thin Compose
   layers over generation calls that all funnel through
   `AiGenerationService`.
4. The two host implementations (`SyncCoordinator` on Android,
   `SyncHttpServer` on desktop) should stay behaviorally identical — same
   routes, same auth, same package format — since a device pairs with
   "whichever host is running," not "the Android host" or "the desktop
   host" specifically.
5. UI screens are the least architecturally load-bearing part: they're
   Compose + Hilt ViewModels over the repositories, one subpackage per
   feature under `feature/`, and can be rebuilt last, screen by screen,
   against an already-working data/sync/AI layer.
