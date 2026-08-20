# Roleplay mode — current-state audit

Written as the starting point for building out Roleplay's three intended
display modes (chat-app-like, Dungeon Master/CYOA, manga/comic-panel).
This is a read-only investigation of what already exists — no code changes
accompany this document. See `docs/CHECKPOINTS.md` for where this sits
relative to the Novel-writing checkpoint.

The codebase already has real infrastructure for exactly three modes,
tagged as `RpChatEntity.displayMode` string values `"messenger"`,
`"dungeonMaster"`, and `"roleplay"`. This document records how much of
each is genuinely implemented versus scaffolding, so future work can
build on what's there instead of re-discovering it.

## Shared infrastructure (all three modes)

- **Data model** (`data/db/entities/InkEntities.kt`): `RpChatEntity`
  carries `displayMode: String = "messenger"` per chat, plus unused
  `narrationColorHex`/`speechColorHex`/`oocColorHex` fields (schema exists,
  nothing reads them yet — likely intended for future DM-style colored
  text). `RpMessageEntity` also carries its own `displayMode`, and
  messages are **hard-partitioned per mode at the query level**
  (`RoleplayDao.observeMessages(chatId, displayMode)`, composite index on
  `[chatId, displayMode]`) — switching modes shows a completely separate
  message history, not a re-render of the same messages.
- **No `MediaPanel`/"panels" table exists.** Panels are a UI-only,
  runtime-derived concept (`RpMediaRef`, in-memory), rebuilt from each
  message's `contentJson` block list on every publish. Grid placement
  (`gridCol`/`gridRow`/`gridColSpan`/`gridRowSpan`) lives on the `Block`
  sealed types themselves (`core/text/DocumentModel.kt`'s `MediaBlock`/
  `MediaStackBlock`) — the same fields Novel mode's media blocks use.
- **Grid math** (`core/text/MediaGrid.kt`, unit-tested): one shared engine
  for both grid sizes — `MediaGrid.SIZE = 6` (manga) and
  `MediaGrid.DM_SIZE = 3` (DM). `RoleplayChatViewModel.activeGridSize()`
  picks the size by mode.
- **Mode switching**: `RoleplayChatViewModel.setDisplayMode(mode)` validates
  against the three literal strings, persists to the DB, updates local
  state — no data migration, no per-mode setup/teardown. The switcher UI
  is `RoleplayDisplayModeBar` in `feature/shell/AppShell.kt` (a 3-way
  segmented pill: Messenger / DM / Roleplay), wired through
  `RoleplayChatChrome` (a plain data holder pushed up from the detail
  screen, not a composable itself despite the name). New chats always
  start in `"messenger"` — there is no mode picker at chat-creation time.
- **AI generation is identical across all three modes.** `generate()`/
  `regenerate()` use the same code path everywhere; the only
  mode-awareness is that message history is filtered to the current
  mode. `RoleplayPromptBuilder.systemBlocks()` builds the same system
  prompt (character card + persona + generic AI guide) regardless of
  mode — **there is no DM-specific "narrate then stop for the player"
  instruction, and no manga-specific "write one short panel caption"
  instruction.** Every mode gets one generic prose reply per turn.
- **No AI image generation exists anywhere in the codebase.**
  `AIProvider` only supports vision *input* (`supportsImages`,
  `ImageAttachment` for sending an image to the model) — nothing
  generates images. "Pictures" everywhere in Roleplay means
  user-attached/uploaded media (`attachMedia`, backed by
  `MediaRepository.importFromUris`) only. **This is the single biggest
  open question for the DM and manga modes** — see "Open questions"
  below.
- Media editing (`MediaEditAction`: Cut/Copy/Paste/Delete/Shrink/Expand/
  Collapse/Stack/Move) and clipboard (`MediaClipboard`) are fully shared
  and mode-agnostic already.
- **No automated test coverage** exists for `RoleplayChatViewModel`, mode
  switching, or either grid-placement function — only the mode-agnostic
  grid math itself (`MediaGridTest.kt`, `MediaStackOpsTest.kt`) is tested.

## Mode 1 — Messenger (chat-app-like): fully working

The mature, default mode. `LazyColumn` of chat bubbles, left/right by
role, themeable bubble colors, auto-scroll-to-bottom, full swipe/
regenerate UI, streaming indicator, long-press edit/delete/copy/cut/
paste/speak. Media attaches inline per-bubble (stacked vertically, no
grid). This is the baseline the other two modes should be judged against
for polish, not something that needs rework.

## Mode 2 — Dungeon Master: partially working, doesn't match the target UX

**Target**: one image, the DM narrates a scenario, the user responds.

**What exists**: the same free-form snap-grid canvas as manga mode
(`MangaSnapGrid`), just sized 3×3 instead of 6×6, with `textEmphasis =
true` (images `ContentScale.Fit` capped at 62% height, captions render as
a text block below rather than overlaid). `ensureDmGridPlacement()`
auto-places any unplaced image/text into the next free grid cell, and
`ensureTextTilesForDm()` synthesizes an invisible media tile (sentinel
`mediaId = "__dm_text__"`) so prose-only messages still get a grid slot.

**The gap**: this is a smaller free-form scrapbook, not a turn-based
narrate/respond flow. Both roles' content lands as interchangeable,
drag-anywhere tiles in a shared grid — there's no structural distinction
between "the DM's scene-setting" and "the player's response," no fixed
image-on-top/narration-below/input-at-bottom layout, and (per "AI
generation" above) no DM-specific system prompt shaping the model's
behavior. The 3×3 grid and text-tile synthesis are real, working
mechanisms — but building the target UX means either constraining this
canvas to a strict linear layout, or building DM mode as a genuinely
different (non-grid) screen that happens to reuse the same media-picker/
edit-action plumbing.

## Mode 3 — Roleplay (manga/comic): closer, but panels aren't truly independent

**Target**: heavily picture-focused, one image + one caption per panel.

**What exists**: `MangaSnapGrid` at 6×6, `textEmphasis = false` — images
fill their cell (`ContentScale.Crop`), and a caption (if present) overlays
as a small bottom-pinned label. `ensureMangaGridPlacement()` auto-places
unplaced panels into the next free cell, span-aware. Drag-to-move,
corner-drag resize, and stacking (multiple images cycling in one panel)
are implemented and reasonably polished, including snap-to-grid and
stack-on-drop detection.

**The gap**: captions are **not** authored per panel. They come from the
entire message's plain text — a message with multiple images shows the
*same* caption repeated on every one of its panels, and there's no
tap-a-panel-and-type-a-short-caption interaction. (Useful: `MediaBlock`
already has a `caption: List<Span>` field in the data model, unused by
Roleplay today — Novel mode's media blocks already support inline
captions the same shape, so wiring per-panel captions in Roleplay is
closer to "expose an existing field in a new UI" than "design a new
data model.") There's also no panel-sequencing/reading-order concept
beyond raw grid position, and — same as DM mode — no manga-specific
system prompt asking the model for a short caption instead of a full
prose reply.

## Open questions before implementation

These materially change scope and are worth deciding before writing
code, not after:

1. **Image generation.** Does "picture" in DM/manga mode mean the user
   manually attaches images (as today, everywhere), or should the app
   generate images from the AI's narration/caption? The latter needs an
   image-generation provider integration (a new `AIProvider`-shaped
   capability, likely a different provider than OpenRouter's text
   models, plus real API cost) — a materially larger effort than
   reshaping existing UI.
2. **DM mode's target layout.** Constrain the existing free-form 3×3 grid
   to a strict single-image/narration/response flow, or build a
   dedicated (non-grid) screen for DM mode?
3. **Per-panel captions in manga mode.** Add a caption-entry affordance
   per panel (tap the panel → edit its own `MediaBlock.caption`,
   independent of the message's body text)?
4. **Per-mode system prompts.** Add DM- and manga-specific instructions
   to `RoleplayPromptBuilder` so the model's output actually matches each
   mode's format (a short DM beat that stops for player input; a short
   panel caption instead of full prose)?

## Update — questions 2, 3, 4 resolved; question 1 deferred

Resolved by the user and implemented in the same pass as this update:

- **DM mode is now a dedicated linear screen** (`DungeonMasterFlow` in
  `RoleplayChatDetailScreen.kt`), not the shared `MangaSnapGrid`. It shows
  the most recent scene image on top, the DM's latest narration in the
  middle, and a fixed response `OutlinedTextField` + Send button at the
  bottom, wired to the existing `generate()`/`onInputChange()` path. The
  3×3 grid, `ensureDmGridPlacement()`, `ensureTextTilesForDm()`, and
  `DM_TEXT_TILE_MEDIA_ID` are left in place (unused by the new UI, low
  risk to keep) rather than removed in this pass.
- **Manga mode now supports real per-panel captions.** Tapping a panel's
  caption chip (or the "+ Caption" affordance when blank) opens a dialog
  that writes to that block's own `caption` field via
  `RoleplayChatViewModel.setPanelCaption(messageId, blockId, text)`.
  `MediaStackBlock` gained a `caption: List<Span>` field (previously only
  `MediaBlock` had one) so stacked panels can be captioned too.
  `publishMessages()` now prefers a block's own caption and falls back to
  the whole message's text only when the block has none — existing
  messages keep working exactly as before.
- **System prompts are now mode-aware.**
  `RoleplayPromptBuilder.systemBlocks()` takes a `displayMode` parameter;
  `"dungeonMaster"` gets a short-burst-then-wait-for-the-player
  instruction, `"roleplay"` gets a short-panel-beat instruction, and
  `"messenger"` (or anything else) is unchanged. Both `generate()` and
  `regenerate()` in `RoleplayChatViewModel`, plus the global prompt
  overlay's roleplay path in `GlobalPromptViewModel`, now pass the chat's
  actual display mode through.

**Still open**: question 1, AI image generation (local ComfyUI and/or a
cloud API-credit provider, user-selectable) is a separate, materially
larger follow-up — new provider abstraction, settings UI, credential
handling — not attempted here. Today "pictures" in all three modes still
means user-attached media only.

## Update — mode-first navigation, and Storyboard replaces the 6×6 manga grid

A second round of changes, requested after the above:

- **Roleplay now opens on a mode picker.** `RoleplayModePickerScreen.kt`
  is a new top screen (three labeled cards: Messenger / Dungeon Master /
  Storyboard) shown by `AppShell`'s `RoleplayDestination.Chats` branch
  whenever no mode has been picked yet (`selectedRpMode == null`).
  Selecting a card filters `RoleplayChatsScreen` to chats whose
  `RpChatEntity.displayMode` matches that mode (client-side filter — no
  new DAO query), with a "← Modes" button back to the picker. Since
  `displayMode` is a single mutable field per chat (see "Shared
  infrastructure" above), a chat only appears under the mode it's
  currently set to; the existing in-chat mode switcher
  (`RoleplayDisplayModeBar`) is how a chat moves between modes' lists.
  `selectedRpMode` resets to null (back to the picker) when leaving the
  Roleplay section entirely or jumping in via global search, but is
  preserved across the Characters/Personas/Codex/Presets tabs.
- **"Roleplay" mode is relabeled Storyboard** in every user-facing string
  (mode picker, chats list header, in-chat segmented pill, chrome
  subtitle) via `roleplayModeLabel()`/`roleplayModeSubtitle()` in
  `RoleplayChatChrome.kt`. The internal `displayMode` value is still the
  string `"roleplay"` — no data migration.
- **Storyboard's grid shrank from 6×6 to 3×3** — `activeGridSize()` and
  `ensureMangaGridPlacement()` in `RoleplayChatViewModel.kt`, and the
  `gridSize` passed to `MangaSnapGrid` in `RoleplayChatDetailScreen.kt`,
  now use `MediaGrid.DM_SIZE` (3) for `"roleplay"` instead of
  `MediaGrid.SIZE` (6). Each of the 9 panels was already independently
  movable/resizable (drag-to-move, corner-drag resize) — that didn't need
  new code, just fewer/larger cells to work with. `MediaGrid.DM_SIZE`'s
  doc comment was updated since Storyboard, not DM, is now its only
  consumer; the constant itself wasn't renamed (tests reference it).
- **DM's picture is now a fixed proportion of the screen** (`weight(1.1f)`
  vs. the narration's `weight(0.9f)` in `DungeonMasterFlow`) instead of a
  340dp-capped thumbnail inside a scrolling column — "large picture" per
  the request. A placeholder message fills the same reserved area when no
  scene image exists yet, so the layout doesn't jump when one is added.

## Update — Storyboard: add media to a specific empty panel

Previously Storyboard could only *remove* media from a panel (the "-"
button); adding media always went through the bottom Media/Audio buttons
and landed wherever `ensureMangaGridPlacement()`'s next-free-cell search
put it — there was no way to choose which of the 9 cells a new picture
went into.

Empty grid cells now render a tappable "+" placeholder
(`MangaSnapGrid` in `RoleplayChatDetailScreen.kt`, gated by a new
`showAddCell` param, `true` only for the Storyboard call site — DM and
messenger don't use this grid). Occupied cells are tracked while
rendering panels (`MediaGrid.cellsCovered(...)` per panel) and skipped;
every other cell gets the "+" overlay, positioned with the same
`cellW`/`cellH` offset math the panels themselves use.

Tapping "+" calls `RoleplayChatViewModel.requestMediaPickForCell(col,
row)`, which stores `(col, row)` in a new `mediaPickTargetCell` state
field and reuses the existing `mediaPickRequestId` picker-launch
mechanism (`RoleplayChatDetailScreen`'s `LaunchedEffect` was already
watching that id). `attachMedia(uris)` then places the *first* imported
media at that exact cell via `Block.withGridPlacement(...)` instead of
leaving it unplaced for auto-placement; any additional media from a
multi-select still falls through to `ensureMangaGridPlacement()` as
before. The target cell is cleared after use and on picker cancel
(`clearMediaPickRequest()`, previously a no-op, now clears it) so a
later plain "Media" button tap doesn't reuse a stale cell.

## Update — DM: matching add-picture button

DM mode has no grid/cells (it shows one scene picture), so it gets the
same affordance in the one place it applies: the scene-picture area in
`DungeonMasterFlow`. Empty, it's a full-area dashed-border tap target
("+ / Tap to add a scene picture") styled like Storyboard's empty-cell
placeholder. Once a picture is showing, a small "+" chip sits in the
top-right corner (mirroring where Storyboard panels put their "-" remove
button) so the scene picture can be swapped without leaving the screen.
Both call the existing untargeted `RoleplayChatViewModel.requestMediaPick()`
— no cell math needed here, since `DungeonMasterFlow` already picks
whichever media panel is most recent
(`mediaPanels.lastOrNull { ... }`) regardless of grid placement, the same
way the bottom "Media" button always worked for this mode.

## Update — Storyboard: resize displaces neighbors, "+ Page" for more boards

Two gaps from the initial Storyboard pass: expanding a panel used to just
draw it over whatever was already in those cells (no collision handling
at all), and a single 3×3 board was a hard ceiling — nine panels, no way
to get more without cannibalizing existing ones.

**Resize now displaces instead of covering.** `MediaBlock`/
`MediaStackBlock` gained a `gridPage: Int = 0` field (`DocumentModel.kt`)
so panels can live on separate boards; `MediaGrid.kt` gained
`nextFreeSlot(occupied, gridSize, colSpan, rowSpan)` (a span-aware
version of the existing 1×1-only `nextFreeCell`) plus `Block.gridPageOrZero()`,
`withGridPage`-aware `withGridPlacement(..., page: Int? = null)`, and
`Block.withGridUnplaced()`. `RoleplayChatViewModel.setMediaGridSpan()`
now: computes the resized panel's new footprint, finds every other panel
on the *same page* whose current footprint overlaps it, and re-homes
each of those into the next free slot of its own size on that page —
or, if the page is too full to fit it anywhere, marks it unplaced
(`withGridUnplaced()`) so the existing `placeUnplacedPanels()` auto-place
pass picks it back up once room frees up, rather than losing it. All
the mutated messages (the resized panel's, plus any displaced panels
that happen to live in other messages — or the *same* message, which
needed care to avoid one write clobbering the other) are collected into
a `pendingBlocks` map and persisted together at the end.
`placeUnplacedPanels()` (used for both new-media auto-placement and this
recovery path) is now itself page-aware — `panels.groupBy { it.gridPage }`
— so each page gets its own independent free-cell search instead of one
shared across every board.

**"+ Page" adds another board.** `MangaSnapGrid` gained a `pagingEnabled`
param (`true` only for the Storyboard call site) and local `currentPage`
state, filtering `panels` down to `pagePanels` for that page before
rendering — drag/resize/stack interactions only ever see the current
page's panels, so nothing can be dragged onto or stacked with a panel on
a page that isn't visible. A `‹ Page X/N › [+ Page]` row sits above the
grid: `‹`/`›` step between pages that actually have panels on them
(`realPageCount`, derived from `panels.maxOf { it.gridPage } + 1`);
`+ Page` jumps to one page past the current max — a purely local,
unpersisted "blank page," since nothing needs writing until a panel
actually lands on it. Tapping an empty cell's "+" now threads the page
through too (`GridCellTarget(col, row, page)` replaces the old
`Pair<Int, Int>` for `mediaPickTargetCell`), so new media added from
page 2 lands on page 2, not page 1.
